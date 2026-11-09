/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.cards;

import java.util.Map;
import net.quimmilho.pokednd.api.error.ApiException;

/**
 * Where the cards go on a sheet. Cards are placed in a grid, centred on the paper; the cut lines of two neighbours are
 * {@code gap} apart; crop marks sit outside the grid, so the margin leaves room for bleed and marks (chapter 9.6).
 *
 * <p>All numbers are millimetres, measured from the top-left corner of the page.
 */
public record PageLayout(CardOptions options, double margin, double bottomMargin, int cols, int rows, double originX, double originY) {

    /** One place on the sheet (0-based). */
    public record Slot(int col, int row) {
    }

    /** Crop marks start this far past the bleed and are this long. */
    static final double MARK_OFFSET = 0.5;
    static final double MARK_LENGTH = 3.0;
    /** Extra room at the bottom for the credit line (chapter 21.3). */
    static final double FOOTER_ROOM = 5.0;

    public static PageLayout compute(CardOptions o) {
        double w = o.size().widthMm();
        double h = o.size().heightMm();
        // room for the bleed and the crop marks on every side, and for the credit line at the bottom
        double margin = o.bleedMm() + MARK_OFFSET + MARK_LENGTH + 0.5;
        double bottom = margin + FOOTER_ROOM;
        double availW = o.paper().widthMm - 2 * margin;
        double availH = o.paper().heightMm - margin - bottom;
        int cols = (int) Math.floor((availW + o.gapMm()) / (w + o.gapMm()) + 1e-9);
        int rows = (int) Math.floor((availH + o.gapMm()) / (h + o.gapMm()) + 1e-9);
        if (cols < 1 || rows < 1) {
            throw new ApiException(422, "CARD_TOO_BIG", "A card of " + o.size() + " mm does not fit on " + o.paper() + " with room for bleed and crop marks.",
                    Map.of("size", o.size().toString(), "paper", o.paper().name()));
        }
        double gridW = cols * w + (cols - 1) * o.gapMm();
        double gridH = rows * h + (rows - 1) * o.gapMm();
        return new PageLayout(o, margin, bottom, cols, rows, margin + (availW - gridW) / 2, margin + (availH - gridH) / 2);
    }

    public int perSheet() {
        return cols * rows;
    }

    /** How many sheets (front pages) {@code cards} printed cards need. */
    public int sheets(int cards) {
        return (cards + perSheet() - 1) / perSheet();
    }

    /** The place of the n-th card on its sheet, filling rows from the left. */
    public Slot slot(int indexOnSheet) {
        return new Slot(indexOnSheet % cols, indexOnSheet / cols);
    }

    /** Where the same card is on the back of the sheet: the columns are mirrored for a long-edge flip. */
    public Slot mirrored(Slot front) {
        return new Slot(cols - 1 - front.col(), front.row());
    }

    /** Left edge of the cut line of the card in this column. */
    public double cardX(int col) {
        return originX + col * (options.size().widthMm() + options.gapMm());
    }

    /** Top edge of the cut line of the card in this row. */
    public double cardY(int row) {
        return originY + row * (options.size().heightMm() + options.gapMm());
    }
}
