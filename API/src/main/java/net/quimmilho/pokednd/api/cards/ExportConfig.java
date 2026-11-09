/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.cards;

import io.smallrye.config.ConfigMapping;

/** The {@code pokednd.cards.export.*} settings: the keys {@code cards.export.*} of chapter 9.10 (the settings registry comes in phase 33). */
@ConfigMapping(prefix = "pokednd.cards.export")
public interface ExportConfig {

    /** {@code cards.export.maxCardsPerJob}: limit on printed cards (cards times copies) in one job. */
    int maxCardsPerJob();

    /** {@code cards.export.fileTtlHours}: how long a finished export stays downloadable. */
    int fileTtlHours();

    /** {@code cards.export.defaultSizeMm}, for example {@code 63x88}. */
    String defaultSizeMm();

    /** {@code cards.export.defaultPaper}: {@code A4} or {@code LETTER}. */
    String defaultPaper();

    /** {@code cards.export.bleedMm}. */
    double bleedMm();

    /** {@code cards.export.gapMm}. */
    double gapMm();

    /** How often the scheduler deletes expired files, for example {@code 10m}. */
    String cleanupEvery();

    /** How many export jobs one person may start per hour (rate limit, chapter 14.6). */
    int maxJobsPerHour();
}
