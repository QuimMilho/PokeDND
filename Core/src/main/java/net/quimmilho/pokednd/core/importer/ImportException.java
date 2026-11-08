/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

/**
 * The importer cannot go on: a file is missing, a required column is missing, or the file is broken beyond one bad row.
 * A single bad row never throws this; it is skipped and written to the file's {@link FileReport}.
 */
public final class ImportException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ImportException(String message) {
        super(message);
    }

    public ImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
