/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.trainer;

/** What happens when a trainer reaches 0 HP (chapter 4.6). */
public final class KnockOut {

    /** What the game does next. */
    public enum Outcome {
        /** Go to the last healer, heal the party, lose some ₽ ({@link #respawn}). */
        RESPAWN,
        /** Start the death saving throws ({@link DeathSaves#start()}). */
        DEATH_SAVES,
        /** Nothing automatic: the admin decides. */
        ADMIN_DECIDES
    }

    private KnockOut() {
    }

    /** What to do when a trainer drops to 0 HP, for a knock-out mode. */
    public static Outcome onZeroHp(KoMode mode) {
        return switch (mode) {
            case FAINT_RESPAWN -> Outcome.RESPAWN;
            case DEATH_SAVES -> Outcome.DEATH_SAVES;
            case ADMIN -> Outcome.ADMIN_DECIDES;
        };
    }

    /** ₽ lost on a knock-out with {@code combat.whiteout.moneyLossPct}, rounded down. */
    public static long moneyLost(long money, int moneyLossPct) {
        if (moneyLossPct < 0 || moneyLossPct > 100) {
            throw new IllegalArgumentException("combat.whiteout.moneyLossPct is 0 to 100, not " + moneyLossPct);
        }
        return money * moneyLossPct / 100;
    }

    /** The trainer back at a healer: full HP and {@code moneyLossPct} % of the ₽ gone. */
    public static Trainer respawn(Trainer trainer, int moneyLossPct) {
        return trainer.healFully().withMoney(trainer.money() - moneyLost(trainer.money(), moneyLossPct));
    }

    /**
     * The 5e death saving throws. At the start of each turn the unconscious trainer rolls 1d20: 10 or more is a success, 9 or
     * less a failure; a natural 20 brings them back to 1 HP; a natural 1 counts as two failures. Three successes stabilise;
     * three failures fall back to {@link Outcome#RESPAWN}, or kill the trainer if {@code trainer.ko.deathAllowed} is on.
     *
     * @param successes saves passed so far
     * @param failures  saves failed so far
     * @param state     where it stands
     */
    public record DeathSaves(int successes, int failures, State state) {

        /** Where the saves stand. */
        public enum State {
            /** Still rolling. */
            UNCONSCIOUS,
            /** Three successes, or an ally stabilised the trainer: out of danger at 0 HP. */
            STABLE,
            /** A natural 20: the trainer is back at 1 HP. */
            REVIVED,
            /** Three failures with {@code trainer.ko.deathAllowed}: the trainer dies and the admin decides what happens. */
            DEAD,
            /** Three failures without {@code trainer.ko.deathAllowed}: fall back to faint-and-respawn. */
            RESPAWN
        }

        /** The saves of a trainer who has just dropped to 0 HP. */
        public static DeathSaves start() {
            return new DeathSaves(0, 0, State.UNCONSCIOUS);
        }

        /** True once the saves are over, so that no more rolls are needed. */
        public boolean isOver() {
            return state != State.UNCONSCIOUS;
        }

        /** One death saving throw. {@code d20} is the die, 1 to 20. */
        public DeathSaves roll(int d20, boolean deathAllowed) {
            if (d20 < 1 || d20 > 20) {
                throw new IllegalArgumentException("A d20 shows 1 to 20, not " + d20);
            }
            if (isOver()) {
                throw new IllegalStateException("The death saves are over (" + state + ")");
            }
            if (d20 == 20) {
                return new DeathSaves(successes, failures, State.REVIVED);
            }
            int s = successes;
            int f = failures;
            if (d20 == 1) {
                f += 2;
            } else if (d20 >= 10) {
                s++;
            } else {
                f++;
            }
            if (f >= 3) {
                return new DeathSaves(s, f, deathAllowed ? State.DEAD : State.RESPAWN);
            }
            if (s >= 3) {
                return new DeathSaves(s, f, State.STABLE);
            }
            return new DeathSaves(s, f, State.UNCONSCIOUS);
        }

        /** An ally stabilised the trainer (a Medicine check of DC 10, or a healing effect). */
        public DeathSaves stabilise() {
            if (isOver()) {
                throw new IllegalStateException("The death saves are over (" + state + ")");
            }
            return new DeathSaves(successes, failures, State.STABLE);
        }

        /** The trainer after a natural 20: 1 HP. */
        public Trainer applyRevival(Trainer trainer) {
            if (state != State.REVIVED) {
                throw new IllegalStateException("Only a natural 20 brings the trainer back to 1 HP");
            }
            return trainer.withHp(1);
        }
    }
}
