/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import java.util.List;

/**
 * What happened while one input file was read: how many rows there were, how many were kept, and every row that was
 * skipped or repaired, with the reason. The reports of all files are kept for the final reports step (phase 14).
 *
 * @param file     the file, relative to the resources folder (for example {@code attack.csv})
 * @param rowsRead data rows found (blank lines and the header are not counted)
 * @param rowsKept rows that became records
 * @param issues   skipped rows and repairs, in file order
 */
public record FileReport(String file, int rowsRead, int rowsKept, List<Issue> issues) {

    /** What the importer did about something. */
    public enum Kind {
        /** The row was dropped. */
        SKIPPED,
        /** The data was read, but something had to be repaired or looked odd (a header typo, a byte-order mark…). */
        FIXED
    }

    /**
     * One thing worth reporting.
     *
     * @param kind    skipped or repaired
     * @param row     1-based number of the data row (0 when it concerns the whole file)
     * @param message plain-language reason
     */
    public record Issue(Kind kind, int row, String message) {
    }

    public FileReport {
        issues = List.copyOf(issues);
    }

    /** The rows that were dropped. */
    public List<Issue> skipped() {
        return issues.stream().filter(i -> i.kind() == Kind.SKIPPED).toList();
    }

    /** The repairs and oddities. */
    public List<Issue> fixes() {
        return issues.stream().filter(i -> i.kind() == Kind.FIXED).toList();
    }
}
