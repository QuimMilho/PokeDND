/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The names of the event types and the conversion between an event and the two columns of the log: {@code type} and {@code payload}
 * (chapter 18, the {@code GameEvent} row). The payload is the JSON of the record's fields, so a log written today can be read later as
 * long as fields are only added.
 */
public final class EventTypes {

    public static final String STATUS_CHANGED = "game.status";
    public static final String ROUND_ADVANCED = "game.round";
    public static final String TRAINER_SET = "trainer.set";
    public static final String TRAINER_REMOVED = "trainer.removed";
    public static final String ROLL_REQUESTED = "roll.requested";
    public static final String ROLL_ANSWERED = "roll.answered";
    public static final String ROLL_CANCELLED = "roll.cancelled";
    public static final String ROLL_SUBMITTED = "roll.submitted";
    public static final String ROLL_REJECTED = "roll.rejected";
    public static final String ROLL_OVERRIDDEN = "roll.overridden";
    public static final String ROLL_EXPIRED = "roll.expired";
    public static final String RANDOM_DRAWN = "random.drawn";
    public static final String BATTLE_STARTED = "battle.started";
    public static final String ROUND_OPENED = "battle.round.opened";
    public static final String INITIATIVE_SET = "battle.initiative";
    public static final String ACTION_DECLARED = "battle.declared";
    public static final String RESOLVE_STARTED = "battle.resolve";
    public static final String ACTION_RESOLVED = "battle.resolved";
    public static final String REPLACEMENT_SENT = "battle.replacement";
    public static final String COMBATANT_SET = "battle.combatant";
    public static final String QUEUE_REORDERED = "battle.reordered";
    public static final String ROUND_ENDED = "battle.round.ended";
    public static final String BATTLE_ENDED = "battle.ended";
    public static final String HP_CHANGED = "battle.hp";
    public static final String STATUS_SET = "battle.status";
    public static final String STAGE_CHANGED = "battle.stage";
    public static final String VOLATILE_SET = "battle.volatile";
    public static final String HELD_ITEM_SET = "battle.item";
    public static final String BATTLE_NOTED = "battle.noted";
    public static final String SWITCH_REQUIRED = "battle.switch.required";
    public static final String ADMIN_TASK_OPENED = "battle.task.opened";
    public static final String ADMIN_TASK_CLOSED = "battle.task.closed";

    private static final Map<String, Class<? extends GameEvent>> CLASSES = new LinkedHashMap<>();

    static {
        CLASSES.put(STATUS_CHANGED, GameEvent.StatusChanged.class);
        CLASSES.put(ROUND_ADVANCED, GameEvent.RoundAdvanced.class);
        CLASSES.put(TRAINER_SET, GameEvent.TrainerSet.class);
        CLASSES.put(TRAINER_REMOVED, GameEvent.TrainerRemoved.class);
        CLASSES.put(ROLL_REQUESTED, GameEvent.RollRequested.class);
        CLASSES.put(ROLL_ANSWERED, GameEvent.RollAnswered.class);
        CLASSES.put(ROLL_CANCELLED, GameEvent.RollCancelled.class);
        CLASSES.put(ROLL_SUBMITTED, GameEvent.RollSubmitted.class);
        CLASSES.put(ROLL_REJECTED, GameEvent.RollRejected.class);
        CLASSES.put(ROLL_OVERRIDDEN, GameEvent.RollOverridden.class);
        CLASSES.put(ROLL_EXPIRED, GameEvent.RollExpired.class);
        CLASSES.put(RANDOM_DRAWN, GameEvent.RandomDrawn.class);
        CLASSES.put(BATTLE_STARTED, GameEvent.BattleStarted.class);
        CLASSES.put(ROUND_OPENED, GameEvent.RoundOpened.class);
        CLASSES.put(INITIATIVE_SET, GameEvent.InitiativeSet.class);
        CLASSES.put(ACTION_DECLARED, GameEvent.ActionDeclared.class);
        CLASSES.put(RESOLVE_STARTED, GameEvent.ResolveStarted.class);
        CLASSES.put(ACTION_RESOLVED, GameEvent.ActionResolved.class);
        CLASSES.put(REPLACEMENT_SENT, GameEvent.ReplacementSent.class);
        CLASSES.put(COMBATANT_SET, GameEvent.CombatantSet.class);
        CLASSES.put(QUEUE_REORDERED, GameEvent.QueueReordered.class);
        CLASSES.put(ROUND_ENDED, GameEvent.RoundEnded.class);
        CLASSES.put(BATTLE_ENDED, GameEvent.BattleEnded.class);
        CLASSES.put(HP_CHANGED, GameEvent.HpChanged.class);
        CLASSES.put(STATUS_SET, GameEvent.StatusSet.class);
        CLASSES.put(STAGE_CHANGED, GameEvent.StageChanged.class);
        CLASSES.put(VOLATILE_SET, GameEvent.VolatileSet.class);
        CLASSES.put(HELD_ITEM_SET, GameEvent.HeldItemSet.class);
        CLASSES.put(BATTLE_NOTED, GameEvent.BattleNoted.class);
        CLASSES.put(SWITCH_REQUIRED, GameEvent.SwitchRequired.class);
        CLASSES.put(ADMIN_TASK_OPENED, GameEvent.AdminTaskOpened.class);
        CLASSES.put(ADMIN_TASK_CLOSED, GameEvent.AdminTaskClosed.class);
    }

    private EventTypes() {
    }

    /** Every type name that exists. */
    public static java.util.Set<String> all() {
        return java.util.Collections.unmodifiableSet(CLASSES.keySet());
    }

    /** The event as the JSON that goes in the {@code payload} column. */
    public static ObjectNode payload(GameEvent event) {
        return (ObjectNode) Json.MAPPER.valueToTree(event);
    }

    /**
     * The event of a stored {@code type} and {@code payload}.
     *
     * @throws InvalidEventException if the type is unknown (a log from a newer version) or the payload does not fit it
     */
    public static GameEvent read(String type, JsonNode payload) {
        Class<? extends GameEvent> cls = CLASSES.get(type);
        if (cls == null) {
            throw new InvalidEventException("The event type \"" + type + "\" is not known to this version");
        }
        try {
            return Json.MAPPER.treeToValue(payload, cls);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new InvalidEventException("The payload of a \"" + type + "\" event is not valid: " + e.getMessage());
        }
    }

    /** The shared JSON settings. */
    static final class Json {
        /** A log may have been written by a newer version that added fields: unknown fields are skipped, never an error. */
        static final ObjectMapper MAPPER = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        private Json() {
        }
    }
}
