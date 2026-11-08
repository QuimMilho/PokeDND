/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvTest {

    @Test
    void removesTheByteOrderMarkAndSaysSo() {
        Csv.Table t = Csv.parse("﻿a,b\n1,2\n");
        assertTrue(t.hadBom());
        assertEquals(List.of("a", "b"), t.header(), "the first column name must not start with an invisible character");
        assertEquals(List.of("1", "2"), t.rows().get(0).cells());
    }

    @Test
    void worksWithoutAByteOrderMark() {
        assertFalse(Csv.parse("a,b\n1,2\n").hadBom());
    }

    @Test
    void repairsTheKnownHeaderTypo() {
        Csv.Table t = Csv.parse("HP EVs,Attcak EVs,Defense EVs\n0,1,0\n");
        assertEquals(List.of("HP EVs", "Attack EVs", "Defense EVs"), t.header());
        assertEquals(1, t.headerFixes().size());
        assertTrue(t.headerFixes().get(0).contains("Attcak EVs"));
        assertEquals(1, t.indexOf("Attack EVs"));
        assertEquals(-1, t.indexOf("Attcak EVs"));
    }

    @Test
    void readsQuotedFieldsWithCommasQuotesAndLineBreaks() {
        Csv.Table t = Csv.parse("name,text\n\"10,000,000 Volt\",\"She said \"\"hi\"\"\"\nx,\"two\nlines\"\n");
        assertEquals(List.of("10,000,000 Volt", "She said \"hi\""), t.rows().get(0).cells());
        assertEquals(List.of("x", "two\nlines"), t.rows().get(1).cells());
        assertEquals(2, t.rows().size());
    }

    @Test
    void understandsWindowsAndUnixLineEnds() {
        Csv.Table crlf = Csv.parse("a,b\r\n1,2\r\n3,4\r\n");
        Csv.Table lf = Csv.parse("a,b\n1,2\n3,4\n");
        assertEquals(lf, crlf);
        assertEquals(2, lf.rows().size());
    }

    @Test
    void lastRowNeedsNoLineEnd() {
        assertEquals(List.of("3", "4"), Csv.parse("a,b\n1,2\n3,4").rows().get(1).cells());
    }

    @Test
    void skipsBlankLinesAndCountsThem() {
        Csv.Table t = Csv.parse("a,b\n\n1,2\n\n\n3,4\n");
        assertEquals(2, t.rows().size());
        assertEquals(3, t.blankLines());
        assertEquals(2, t.rows().get(1).number(), "row numbers do not count blank lines");
    }

    @Test
    void keepsEmptyCellsButNotEmptyLines() {
        Csv.Table t = Csv.parse("a,b,c\n,,\n1,,3\n");
        assertEquals(List.of("", "", ""), t.rows().get(0).cells(), "a row of commas is a row, not a blank line");
        assertEquals(List.of("1", "", "3"), t.rows().get(1).cells());
    }

    @Test
    void anUnclosedQuoteIsAnError() {
        ImportException e = assertThrows(ImportException.class, () -> Csv.parse("a,b\n1,\"oops\n"));
        assertTrue(e.getMessage().contains("never closed"));
    }

    @Test
    void anEmptyFileIsAnError() {
        assertThrows(ImportException.class, () -> Csv.parse(""));
    }

    @Test
    void readingTwiceGivesEqualTables() {
        String text = "﻿a,Attcak EVs\n1,2\n";
        assertEquals(Csv.parse(text), Csv.parse(text));
    }
}
