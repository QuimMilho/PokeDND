/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The settings that decide how a roll request is answered (chapter 6.8). The defaults are those of the specification.
 *
 * @param inputModes          {@code dice.input.modes}: the ways a player may answer. The admin can always enter a result
 * @param approvalRequired    {@code dice.approval.required}: a physical roll typed by a player needs the admin's approval
 * @param approvalAutoSeconds {@code dice.approval.autoSeconds}: approve by itself after this many seconds (0 = never)
 * @param entryPerDie         {@code dice.entry.perDie}: the player types every die; if false, only the total
 * @param showTrueChance      {@code dice.prompt.showTrueChance}: the prompt tells the exact chance
 * @param timeoutSeconds      {@code dice.prompt.timeoutSeconds}: a request that nobody answers in this time expires (0 = never)
 * @param enemyMode           {@code dice.enemy.mode}: enemy rolls are made by the system, or by the admin with real dice
 * @param secretMode          {@code dice.secret.mode}: the same for secret rolls (encounter, shiny, boss, wild level, loot)
 * @param rngSource           {@code dice.rng.source}: where the seeds of digital rolls come from
 */
public record RollSettings(Set<InputMode> inputModes, boolean approvalRequired, int approvalAutoSeconds, boolean entryPerDie,
                           boolean showTrueChance, int timeoutSeconds, SystemRolls enemyMode, SystemRolls secretMode, RngSource rngSource) {

    /** The ways to answer a roll request (chapter 6.7). */
    public enum InputMode {
        PLAYER_SUBMIT("playerSubmit"),
        DIGITAL("digital"),
        ADMIN_ENTER("adminEnter");

        private final String id;

        InputMode(String id) {
            this.id = id;
        }

        /** The word used in the setting. */
        public String id() {
            return id;
        }
    }

    /** Who makes the rolls the system would otherwise make. */
    public enum SystemRolls {
        AUTO("auto"),
        ADMIN_PHYSICAL("adminPhysical");

        private final String id;

        SystemRolls(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    /** Where digital seeds come from. */
    public enum RngSource {
        SECURE("secure"),
        SEEDED("seeded");

        private final String id;

        RngSource(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public RollSettings {
        if (inputModes == null || inputModes.isEmpty()) {
            throw new IllegalArgumentException("At least one way to answer a roll must be allowed");
        }
        inputModes = Collections.unmodifiableSet(EnumSet.copyOf(inputModes));
        if (approvalAutoSeconds < 0 || timeoutSeconds < 0) {
            throw new IllegalArgumentException("Seconds cannot be negative");
        }
        if (enemyMode == null || secretMode == null || rngSource == null) {
            throw new IllegalArgumentException("Every roll setting needs a value");
        }
    }

    /** The defaults of chapter 6.8. */
    public static RollSettings defaults() {
        return new RollSettings(EnumSet.allOf(InputMode.class), true, 0, true, true, 0, SystemRolls.AUTO, SystemRolls.AUTO, RngSource.SECURE);
    }

    public RollSettings withInputModes(Set<InputMode> value) {
        return new RollSettings(value, approvalRequired, approvalAutoSeconds, entryPerDie, showTrueChance, timeoutSeconds, enemyMode, secretMode, rngSource);
    }

    public RollSettings withApproval(boolean required, int autoSeconds) {
        return new RollSettings(inputModes, required, autoSeconds, entryPerDie, showTrueChance, timeoutSeconds, enemyMode, secretMode, rngSource);
    }

    public RollSettings withEntryPerDie(boolean value) {
        return new RollSettings(inputModes, approvalRequired, approvalAutoSeconds, value, showTrueChance, timeoutSeconds, enemyMode, secretMode, rngSource);
    }

    public RollSettings withShowTrueChance(boolean value) {
        return new RollSettings(inputModes, approvalRequired, approvalAutoSeconds, entryPerDie, value, timeoutSeconds, enemyMode, secretMode, rngSource);
    }

    public RollSettings withTimeoutSeconds(int value) {
        return new RollSettings(inputModes, approvalRequired, approvalAutoSeconds, entryPerDie, showTrueChance, value, enemyMode, secretMode, rngSource);
    }

    public RollSettings withSystemRolls(SystemRolls enemy, SystemRolls secret) {
        return new RollSettings(inputModes, approvalRequired, approvalAutoSeconds, entryPerDie, showTrueChance, timeoutSeconds, enemy, secret, rngSource);
    }

    public RollSettings withRngSource(RngSource value) {
        return new RollSettings(inputModes, approvalRequired, approvalAutoSeconds, entryPerDie, showTrueChance, timeoutSeconds, enemyMode, secretMode, value);
    }

    /** True if players may answer in this way. */
    public boolean allows(InputMode mode) {
        return inputModes.contains(mode);
    }

    /**
     * The setting {@code dice.input.modes} as text, such as {@code playerSubmit,digital,adminEnter}.
     *
     * @throws IllegalArgumentException for an unknown word or an empty list
     */
    public static Set<InputMode> parseInputModes(String text) {
        Set<InputMode> modes = EnumSet.noneOf(InputMode.class);
        for (String word : text == null ? new String[0] : text.split(",")) {
            String w = word.strip();
            if (w.isEmpty()) {
                continue;
            }
            modes.add(Arrays.stream(InputMode.values()).filter(m -> m.id.equalsIgnoreCase(w)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown way to answer a roll: \"" + w + "\" (use playerSubmit, digital or adminEnter)")));
        }
        if (modes.isEmpty()) {
            throw new IllegalArgumentException("At least one way to answer a roll must be allowed");
        }
        return modes;
    }

    /** The setting as text again, in the order of the specification. */
    public String inputModesText() {
        return inputModes.stream().map(InputMode::id).collect(Collectors.joining(","));
    }

    /** {@code auto} or {@code adminPhysical}. */
    public static Optional<SystemRolls> parseSystemRolls(String text) {
        return Arrays.stream(SystemRolls.values()).filter(s -> s.id.equalsIgnoreCase(text == null ? "" : text.strip())).findFirst();
    }

    /** {@code secure} or {@code seeded}. */
    public static Optional<RngSource> parseRngSource(String text) {
        return Arrays.stream(RngSource.values()).filter(s -> s.id.equalsIgnoreCase(text == null ? "" : text.strip())).findFirst();
    }
}
