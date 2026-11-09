/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.trainer;

import java.util.List;

/**
 * A class feature that a trainer gets at a level (chapter 4.4).
 *
 * @param level   the trainer level that gives it
 * @param id      the identifier, for example {@code commanding_presence}
 * @param name    the name shown to players
 * @param text    what it does, in words (players read this; the admin adjudicates what the effects cannot say)
 * @param effects the machine-readable parts
 */
public record ClassFeature(int level, String id, String name, String text, List<FeatureEffect> effects) {

    public ClassFeature {
        if (level < 1) {
            throw new IllegalArgumentException("A feature level is at least 1, not " + level);
        }
        if (id == null || !id.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("A feature id uses lowercase letters, digits and underscores, not " + id);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("The feature " + id + " needs a name");
        }
        text = text == null ? "" : text;
        effects = effects == null ? List.of() : List.copyOf(effects);
    }
}
