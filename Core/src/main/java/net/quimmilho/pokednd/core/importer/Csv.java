/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A small CSV reader with no dependencies.
 *
 * <p>It handles a UTF-8 byte-order mark, quoted fields (commas, line breaks and {@code ""} inside quotes), CRLF and LF
 * line ends, and skips blank lines. It repairs the known header typo {@code Attcak EVs}.
 */
public final class Csv {

    /** Header names that are known to be misspelt in the source files, and what they should say. */
    private static final Map<String, String> HEADER_FIXES = Map.of("Attcak EVs", "Attack EVs");

    private Csv() {
    }

    /**
     * One data row.
     *
     * @param number 1-based position among the data rows (the header is not counted, blank lines are not counted)
     * @param cells  the fields, as written (not trimmed)
     */
    public record Row(int number, List<String> cells) {
    }

    /**
     * A whole file.
     *
     * @param header       column names, after repairing known typos and trimming
     * @param rows         data rows
     * @param hadBom       the file started with a byte-order mark (it was removed)
     * @param blankLines   how many blank lines were skipped
     * @param headerFixes  one sentence per header typo that was repaired
     */
    public record Table(List<String> header, List<Row> rows, boolean hadBom, int blankLines, List<String> headerFixes) {

        /** Position of a column, or -1 when there is no such column. */
        public int indexOf(String column) {
            return header.indexOf(column);
        }
    }

    /** Reads a UTF-8 file. */
    public static Table read(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ImportException("Cannot read " + file + ": " + e.getMessage(), e);
        }
        try {
            return parse(text);
        } catch (ImportException e) {
            throw new ImportException(file.getFileName() + ": " + e.getMessage(), e);
        }
    }

    /** Parses CSV text. The first record is the header. */
    public static Table parse(String text) {
        boolean bom = !text.isEmpty() && text.charAt(0) == '﻿';
        if (bom) {
            text = text.substring(1);
        }
        List<List<String>> records = new ArrayList<>();
        int blank = 0;
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean fieldStarted = false;
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
                fieldStarted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
                fieldStarted = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < n && text.charAt(i + 1) == '\n') {
                    i++;
                }
                if (!fieldStarted && row.isEmpty()) {
                    blank++;
                } else {
                    row.add(field.toString());
                    records.add(row);
                }
                row = new ArrayList<>();
                field.setLength(0);
                fieldStarted = false;
            } else {
                field.append(c);
                fieldStarted = true;
            }
        }
        if (quoted) {
            throw new ImportException("a quoted field is never closed (record " + (records.size() + 1) + ")");
        }
        if (fieldStarted || !row.isEmpty()) {
            row.add(field.toString());
            records.add(row);
        }
        if (records.isEmpty()) {
            throw new ImportException("the file has no header");
        }

        List<String> header = new ArrayList<>();
        List<String> fixes = new ArrayList<>();
        for (String raw : records.get(0)) {
            String name = raw.strip();
            String fixed = HEADER_FIXES.get(name);
            if (fixed != null) {
                fixes.add("header \"" + name + "\" is misspelt and was read as \"" + fixed + "\"");
                name = fixed;
            }
            header.add(name);
        }
        List<Row> rows = new ArrayList<>();
        for (int r = 1; r < records.size(); r++) {
            rows.add(new Row(r, List.copyOf(records.get(r))));
        }
        return new Table(List.copyOf(header), List.copyOf(rows), bom, blank, List.copyOf(fixes));
    }
}
