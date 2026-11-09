/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.trainer;

/** What happens when a trainer reaches 0 HP: {@code trainer.ko.mode} (chapter 4.6). */
public enum KoMode {
    /** The trainer returns to the last healer, the party is healed, and some ₽ are lost. The default. */
    FAINT_RESPAWN("faintRespawn"),
    /** 5e death saving throws. */
    DEATH_SAVES("deathSaves"),
    /** No automatic rule: the admin decides. */
    ADMIN("admin");

    private final String id;

    KoMode(String id) {
        this.id = id;
    }

    /** The value written in the settings. */
    public String id() {
        return id;
    }

    /** Reads a setting value, ignoring case. */
    public static KoMode parse(String text) {
        for (KoMode m : values()) {
            if (m.id.equalsIgnoreCase(text.strip())) {
                return m;
            }
        }
        throw new IllegalArgumentException("trainer.ko.mode must be faintRespawn, deathSaves or admin, not " + text);
    }
}
