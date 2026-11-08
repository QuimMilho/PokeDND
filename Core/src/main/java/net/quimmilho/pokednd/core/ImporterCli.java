/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core;

import java.nio.file.Files;
import java.nio.file.Path;
import net.quimmilho.pokednd.core.importer.Bundle;
import net.quimmilho.pokednd.core.importer.ImportException;

/**
 * Command line entry of the importer: writes the data bundle (chapter 19.2) to a folder.
 *
 * <pre>
 * java -cp core.jar net.quimmilho.pokednd.core.ImporterCli --resources resources --roster build/roster.json --out build/bundle
 *     [--overrides cards/signature-overrides.json]
 * </pre>
 *
 * Without {@code --overrides} the file {@code cards/signature-overrides.json} is used if it exists in the current folder.
 *
 * Exit code 0 when the bundle was written, 1 when an input is missing or an id dangles (the reason is printed), 2 for wrong arguments.
 */
public final class ImporterCli {

    private ImporterCli() {
    }

    public static void main(String[] args) {
        Path resources = Path.of("resources");
        Path roster = Path.of("build/roster.json");
        Path out = Path.of("build/bundle");
        Path overrides = Files.isRegularFile(Path.of("cards/signature-overrides.json")) ? Path.of("cards/signature-overrides.json") : null;
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 >= args.length) {
                usage("missing value for " + args[i]);
                return;
            }
            switch (args[i]) {
                case "--resources" -> resources = Path.of(args[i + 1]);
                case "--roster" -> roster = Path.of(args[i + 1]);
                case "--out" -> out = Path.of(args[i + 1]);
                case "--overrides" -> overrides = Path.of(args[i + 1]);
                default -> {
                    usage("unknown option " + args[i]);
                    return;
                }
            }
        }
        try {
            Bundle bundle = Bundle.build(resources, roster, overrides);
            bundle.write(out);
            System.out.println("Bundle written to " + out + " (hash " + bundle.outputHash() + ")");
        } catch (ImportException e) {
            System.err.println("Import failed: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void usage(String problem) {
        System.err.println(problem);
        System.err.println("Usage: ImporterCli [--resources <folder>] [--roster <roster.json>] [--out <folder>] [--overrides <signature-overrides.json>]");
        System.exit(2);
    }
}
