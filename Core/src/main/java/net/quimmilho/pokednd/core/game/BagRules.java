/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import net.quimmilho.pokednd.core.trainer.TrainerRulebook;

/**
 * Everything the item, shop and trade calls need to know about the game besides its state: the data books, the trainer rules (for the
 * class features that change prices and bag room), the settings, and what the evolution rules need to know about the place and time.
 * The caller builds it once per game and passes it to {@link BagDesk}, {@link ShopDesk} and {@link TradeDesk}.
 *
 * @param species  the species (growth, abilities, what they learn)
 * @param moves    the moves and the items
 * @param rulebook the trainer classes and their features
 * @param progress the progress settings (level cap, evolution mode, moves per Pokémon, party size)
 * @param economy  the bag and money settings
 * @param context  time, weather and place for evolution rules (nothing known by default)
 */
public record BagRules(SpeciesBook species, MoveBook moves, TrainerRulebook rulebook, ProgressSettings progress, EconomySettings economy,
                       EvolutionCheck.Context context) {

    public BagRules {
        progress = progress == null ? ProgressSettings.defaults() : progress;
        economy = economy == null ? EconomySettings.defaults() : economy;
        context = context == null ? EvolutionCheck.Context.none() : context;
    }

    /** The default settings, and nothing known about the place and time. */
    public static BagRules of(SpeciesBook species, MoveBook moves, TrainerRulebook rulebook) {
        return new BagRules(species, moves, rulebook, ProgressSettings.defaults(), EconomySettings.defaults(), EvolutionCheck.Context.none());
    }

    public BagRules withEconomy(EconomySettings value) {
        return new BagRules(species, moves, rulebook, progress, value, context);
    }

    public BagRules withProgress(ProgressSettings value) {
        return new BagRules(species, moves, rulebook, value, economy, context);
    }

    public BagRules withContext(EvolutionCheck.Context value) {
        return new BagRules(species, moves, rulebook, progress, economy, value);
    }
}
