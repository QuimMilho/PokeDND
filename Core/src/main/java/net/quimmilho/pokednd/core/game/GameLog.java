/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The event log of one game, with its snapshots and its audit trail (chapters 12.5, 14.5). The game <em>is</em> this log: the state is
 * {@link GameReducer} applied to every entry that is not undone, so the same log always gives the same state. The API keeps the three
 * lists in the database (in the same transaction) and builds a log again with {@link #restore}; the log itself does no input or output
 * and has no clock (callers pass the time, which is only recorded).
 *
 * <p><b>Actions.</b> The events added by one {@link #append} are one <em>action</em>; undo and redo work on whole actions. Rolls and
 * random choices are part of the events, so undoing and redoing never roll again.
 *
 * <p><b>Undo.</b> {@link #undo} marks the last actions as undone (at most {@code admin.undo.depth} of them can be reached). The roll
 * requests they answered are open again, because the request event is older and still counts. {@link #redo} brings undone actions back
 * until a new action is added; after that the undone ones stay in the history (marked {@code discarded}) but cannot return.
 *
 * <p><b>Snapshots.</b> When a round starts the log keeps the state ({@code game.autosave.everyRounds}). Rebuilding the state starts from the
 * newest snapshot. An undo or redo removes the snapshots that it makes wrong.
 *
 * <p>Not thread-safe: the API locks the game while it uses the log.
 */
public final class GameLog {

    /**
     * @param undoDepth           {@code admin.undo.depth}: how many recent actions can be undone
     * @param snapshotEveryRounds {@code game.autosave.everyRounds}: a snapshot at the start of every n-th round
     */
    public record Settings(int undoDepth, int snapshotEveryRounds) {

        public Settings {
            if (undoDepth < 0) {
                throw new IllegalArgumentException("admin.undo.depth cannot be negative");
            }
            if (snapshotEveryRounds < 1) {
                throw new IllegalArgumentException("game.autosave.everyRounds is at least 1");
            }
        }

        /** The defaults of chapter 12.6: 20 actions, a snapshot every round. */
        public static Settings defaults() {
            return new Settings(20, 1);
        }
    }

    /** What {@link #append} did. */
    public record Appended(long actionId, long lastSeq, GameState state, List<AuditEntry> audit) {
    }

    /** What {@link #undo} and {@link #redo} did: the actions that were changed, the state afterwards and the audit entry. */
    public record Changed(List<Long> actionIds, GameState state, AuditEntry audit) {
    }

    /** One line of the history an admin picks a point from. */
    public record ActionSummary(long actionId, String actor, long atMillis, List<String> types) {
    }

    private final Settings settings;
    private final List<LogEntry> entries = new ArrayList<>();
    private final List<Snapshot> snapshots = new ArrayList<>();
    private final List<AuditEntry> audit = new ArrayList<>();
    private GameState state = GameState.empty();

    public GameLog(Settings settings) {
        this.settings = settings;
    }

    /**
     * A log as it was stored. {@code entries} must be in order with {@code seq} 1, 2, 3, … and no gaps; the state is rebuilt from the
     * newest snapshot and the entries after it.
     *
     * @throws InvalidEventException if an entry cannot be read or cannot be applied (the log is damaged)
     */
    public static GameLog restore(Settings settings, List<LogEntry> entries, List<Snapshot> snapshots, List<AuditEntry> audit) {
        GameLog log = new GameLog(settings);
        for (int i = 0; i < entries.size(); i++) {
            LogEntry e = entries.get(i);
            if (e.seq() != i + 1L) {
                throw new InvalidEventException("The log has entry " + e.seq() + " where entry " + (i + 1) + " should be");
            }
            if (e.actionId() < 1 || e.actionId() > e.seq()) {
                throw new InvalidEventException("Entry " + e.seq() + " belongs to action " + e.actionId());
            }
        }
        log.entries.addAll(entries);
        log.snapshots.addAll(snapshots.stream().sorted(java.util.Comparator.comparingLong(Snapshot::seq)).toList());
        log.audit.addAll(audit);
        log.state = log.rebuild();
        return log;
    }

    // ---------------------------------------------------------------------------------------------------------------- reading

    /** The state now. */
    public GameState state() {
        return state;
    }

    /** Every entry, undone ones included. */
    public List<LogEntry> entries() {
        return List.copyOf(entries);
    }

    public List<Snapshot> snapshots() {
        return List.copyOf(snapshots);
    }

    public List<AuditEntry> audit() {
        return List.copyOf(audit);
    }

    public Settings settings() {
        return settings;
    }

    /** The state made by replaying every live entry from the very start, ignoring snapshots. It must equal {@link #state()}. */
    public GameState replay() {
        return replayFrom(GameState.empty(), 0);
    }

    /** The actions that an undo can reach, newest first (at most {@code admin.undo.depth}). */
    public List<ActionSummary> recentActions() {
        List<Long> ids = liveActionIds();
        List<ActionSummary> out = new ArrayList<>();
        for (int i = ids.size() - 1; i >= 0 && out.size() < settings.undoDepth(); i--) {
            long id = ids.get(i);
            List<LogEntry> of = entries.stream().filter(e -> e.actionId() == id && !e.undone()).toList();
            out.add(new ActionSummary(id, of.get(0).actor(), of.get(0).atMillis(), of.stream().map(LogEntry::type).toList()));
        }
        return out;
    }

    /** How many actions an undo can reach now. */
    public int undoableActions() {
        return Math.min(settings.undoDepth(), liveActionIds().size());
    }

    /** How many actions a redo can bring back now. */
    public int redoableActions() {
        return redoActionIds().size();
    }

    // ---------------------------------------------------------------------------------------------------------------- changing

    /**
     * Adds the events of one action. They are applied one after the other; if one of them cannot happen nothing is added.
     *
     * @throws InvalidEventException if an event cannot happen (the message says why)
     */
    public Appended append(String actor, long atMillis, List<? extends GameEvent> events) {
        if (events.isEmpty()) {
            throw new IllegalArgumentException("An action has at least one event");
        }
        long firstSeq = entries.size() + 1L;
        List<LogEntry> added = new ArrayList<>();
        List<AuditEntry> auditAdded = new ArrayList<>();
        GameState current = state;
        for (GameEvent event : events) {
            GameState next = GameReducer.apply(current, event);
            long seq = firstSeq + added.size();
            JsonNode payload = EventTypes.payload(event);
            added.add(new LogEntry(seq, firstSeq, event.type(), actor, atMillis, payload, false, false));
            StateDiff diff = StateDiff.between(current, next);
            auditAdded.add(new AuditEntry(seq, firstSeq, actor, event.type(), event.target(), atMillis, diff.before(), diff.after()));
            current = next;
        }
        // nothing failed: the action becomes part of the log, and what could have been redone is gone for good
        entries.replaceAll(LogEntry::discard);
        entries.addAll(added);
        audit.addAll(auditAdded);
        long lastSeq = firstSeq + added.size() - 1;
        if (current.round() != state.round() && current.round() % settings.snapshotEveryRounds() == 0) {
            snapshots.add(new Snapshot(lastSeq, current.round(), GameStateJson.write(current)));
        }
        state = current;
        return new Appended(firstSeq, lastSeq, current, auditAdded);
    }

    /**
     * Undoes the last {@code actions} actions. The state goes back exactly to what it was before them.
     *
     * @throws IllegalArgumentException if {@code actions} is below 1 or more than {@link #undoableActions()}
     */
    public Changed undo(String actor, long atMillis, int actions) {
        int reachable = undoableActions();
        if (actions < 1 || actions > reachable) {
            throw new IllegalArgumentException("Undo 1 to " + reachable + " action(s), not " + actions);
        }
        List<Long> live = liveActionIds();
        List<Long> chosen = new ArrayList<>(live.subList(live.size() - actions, live.size()));
        return flip(actor, atMillis, chosen, true);
    }

    /**
     * Undoes every action after the one with this id (a point picked from {@link #recentActions()}); {@code actionId} 0 undoes all that
     * can be reached.
     */
    public Changed undoTo(String actor, long atMillis, long actionId) {
        List<Long> live = liveActionIds();
        int keep = actionId == 0 ? 0 : live.indexOf(actionId) + 1;
        if (actionId != 0 && keep == 0) {
            throw new IllegalArgumentException("Action " + actionId + " is not in the history");
        }
        return undo(actor, atMillis, live.size() - keep);
    }

    /**
     * Brings back the {@code actions} oldest undone actions, which is possible until a new action is added.
     *
     * @throws IllegalArgumentException if {@code actions} is below 1 or more than {@link #redoableActions()}
     */
    public Changed redo(String actor, long atMillis, int actions) {
        List<Long> redoable = redoActionIds();
        if (actions < 1 || actions > redoable.size()) {
            throw new IllegalArgumentException("Redo 1 to " + redoable.size() + " action(s), not " + actions);
        }
        return flip(actor, atMillis, new ArrayList<>(redoable.subList(0, actions)), false);
    }

    private Changed flip(String actor, long atMillis, List<Long> actionIds, boolean undone) {
        Set<Long> ids = new LinkedHashSet<>(actionIds);
        long lowest = Long.MAX_VALUE;
        long highest = 0;
        for (int i = 0; i < entries.size(); i++) {
            LogEntry e = entries.get(i);
            if (ids.contains(e.actionId())) {
                entries.set(i, e.withUndone(undone));
                lowest = Math.min(lowest, e.seq());
                highest = Math.max(highest, e.seq());
            }
        }
        final long from = lowest;
        snapshots.removeIf(s -> s.seq() >= from); // they contain the entries that just changed
        GameState before = state;
        state = rebuild();
        StateDiff diff = StateDiff.between(before, state);
        String target = "actions:" + String.join(",", actionIds.stream().map(String::valueOf).toList());
        AuditEntry entry = new AuditEntry(highest, 0, actor, undone ? "undo" : "redo", target, atMillis, diff.before(), diff.after());
        audit.add(entry);
        return new Changed(List.copyOf(actionIds), state, entry);
    }

    // ---------------------------------------------------------------------------------------------------------------- inside

    /** The ids of the actions that have live entries, oldest first. */
    private List<Long> liveActionIds() {
        Set<Long> ids = new LinkedHashSet<>();
        for (LogEntry e : entries) {
            if (!e.undone()) {
                ids.add(e.actionId());
            }
        }
        return new ArrayList<>(ids);
    }

    /** The undone actions that a redo can bring back, oldest first. */
    private List<Long> redoActionIds() {
        Set<Long> ids = new LinkedHashSet<>();
        for (LogEntry e : entries) {
            if (e.redoable()) {
                ids.add(e.actionId());
            }
        }
        return new ArrayList<>(ids);
    }

    /** The newest snapshot, then every live entry after it. */
    private GameState rebuild() {
        if (snapshots.isEmpty()) {
            return replay();
        }
        Snapshot newest = snapshots.get(snapshots.size() - 1);
        return replayFrom(newest.read(), newest.seq());
    }

    private GameState replayFrom(GameState start, long afterSeq) {
        GameState current = start;
        for (LogEntry e : entries) {
            if (e.seq() > afterSeq && !e.undone()) {
                current = GameReducer.apply(current, e.event());
            }
        }
        return current;
    }
}
