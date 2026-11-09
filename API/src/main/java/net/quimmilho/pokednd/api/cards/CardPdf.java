/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.cards;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.quimmilho.pokednd.api.cards.CardPainter.Bleed;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/**
 * Turns cards into a print-ready PDF (chapter 16.5): one sheet per page with the cards in a grid, crop marks and the credit line,
 * and, if asked, the back of every sheet right after it with the columns mirrored for double-sided printing (long-edge flip).
 *
 * <p>The output is <b>deterministic</b>: the same cards and options give the same bytes. No dates are written, and the document
 * ID is a hash of the input.
 */
public final class CardPdf {

    private CardPdf() {
    }

    /**
     * Where one printed card sits (1-based page, row and column).
     *
     * @param side {@code front} or {@code back}
     * @param copy 1 for the first copy of a card
     */
    public record ManifestRow(int page, String side, int row, int col, String cardKey, String qrId, int copy) {
    }

    /**
     * The result of a run.
     *
     * @param pages    pages in the PDF (backs included)
     * @param sheets   sheets, counting a front and its back as one
     * @param printed  printed cards (cards times copies)
     * @param warnings problems that did not stop the export (missing art…)
     */
    public record Result(byte[] pdf, int pages, int sheets, int printed, List<ManifestRow> manifest, List<String> warnings, PageLayout layout) {

        /** The manifest as CSV: where each printed card is and its ID (chapter 16.5). */
        public String manifestCsv() {
            StringBuilder sb = new StringBuilder("page,side,row,column,card_key,qr_id,copy\n");
            for (ManifestRow r : manifest) {
                sb.append(r.page()).append(',').append(r.side()).append(',').append(r.row()).append(',').append(r.col()).append(',')
                        .append(r.cardKey()).append(',').append(r.qrId()).append(',').append(r.copy()).append('\n');
            }
            return sb.toString();
        }
    }

    /** One printed card: which card, and which copy of it. */
    private record Printed(CardData card, int copy) {
    }

    /** How many printed cards (cards times copies) these options make. */
    public static int printedCount(int cards, CardOptions options) {
        return cards * options.copies();
    }

    public static Result write(List<CardData> cards, CardOptions options) throws IOException {
        PageLayout layout = PageLayout.compute(options);
        List<Printed> printed = new ArrayList<>();
        for (CardData c : cards) {
            for (int copy = 1; copy <= options.copies(); copy++) {
                printed.add(new Printed(c, copy));
            }
        }
        int perSheet = layout.perSheet();
        int sheets = layout.sheets(printed.size());

        List<ManifestRow> manifest = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        cards.forEach(c -> warnings.addAll(c.warnings()));

        try (PDDocument doc = new PDDocument()) {
            CardFonts fonts = CardFonts.load(doc);
            CardPainter painter = new CardPainter(doc, fonts, options);
            PDRectangle paper = new PDRectangle((float) (options.paper().widthMm * Pen.PT_PER_MM), (float) (options.paper().heightMm * Pen.PT_PER_MM));
            int page = 0;
            for (int sheet = 0; sheet < sheets; sheet++) {
                int from = sheet * perSheet;
                int to = Math.min(printed.size(), from + perSheet);
                page++;
                sheetPage(doc, paper, painter, layout, printed.subList(from, to), false, page, manifest);
                if (options.backs()) {
                    page++;
                    sheetPage(doc, paper, painter, layout, printed.subList(from, to), true, page, manifest);
                }
            }
            if (sheets == 0) {
                // an empty page keeps the PDF valid
                doc.addPage(new PDPage(paper));
            }
            warnings.addAll(painter.warnings());

            PDDocumentInformation info = new PDDocumentInformation();
            info.setTitle("PokeDnD species cards");
            info.setCreator("PokeDnD");
            doc.setDocumentInformation(info);
            doc.getDocument().getTrailer().setItem(COSName.ID, documentId(printed, options));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return new Result(out.toByteArray(), Math.max(page, 1), sheets, printed.size(), List.copyOf(manifest), List.copyOf(warnings), layout);
        }
    }

    private static void sheetPage(PDDocument doc, PDRectangle paper, CardPainter painter, PageLayout layout, List<Printed> cards, boolean back,
                                  int pageNumber, List<ManifestRow> manifest) throws IOException {
        CardOptions o = layout.options();
        PDPage page = new PDPage(paper);
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            Pen pen = new Pen(cs, o.paper().heightMm);
            for (int i = 0; i < cards.size(); i++) {
                PageLayout.Slot slot = layout.slot(i);
                int col = back ? layout.mirrored(slot).col() : slot.col();
                double x = layout.cardX(col);
                double y = layout.cardY(slot.row());
                Bleed bleed = bleedAt(layout, col, slot.row());
                Printed p = cards.get(i);
                // a clip keeps the bleed of one card from painting over its neighbour's cut area
                pen.save();
                pen.clipRect(x - bleed.left(), y - bleed.top(), o.size().widthMm() + bleed.left() + bleed.right(),
                        o.size().heightMm() + bleed.top() + bleed.bottom());
                if (back) {
                    painter.back(pen, p.card(), x, y, bleed);
                } else {
                    painter.front(pen, p.card(), x, y, bleed);
                }
                pen.restore();
                manifest.add(new ManifestRow(pageNumber, back ? "back" : "front", slot.row() + 1, col + 1, p.card().key(), p.card().qrId(), p.copy()));
            }
            if (o.cropMarks()) {
                painter.cropMarks(pen, layout);
            }
            painter.footer(pen, o.paper().widthMm, o.paper().heightMm);
        }
    }

    /** Full bleed on the outside of the grid; between neighbours only as much as fits in the gap (half of it). */
    private static Bleed bleedAt(PageLayout layout, int col, int row) {
        CardOptions o = layout.options();
        double outer = o.bleedMm();
        double inner = Math.min(o.bleedMm(), o.gapMm() / 2);
        return new Bleed(col == 0 ? outer : inner, row == 0 ? outer : inner, col == layout.cols() - 1 ? outer : inner,
                row == layout.rows() - 1 ? outer : inner);
    }

    /** The PDF's two ID strings: a hash of what went in, so that the same input gives the same file. */
    private static COSArray documentId(List<Printed> printed, CardOptions options) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(options.toString().getBytes(StandardCharsets.UTF_8));
            for (Printed p : printed) {
                md.update((p.card().key() + "#" + p.copy() + ";").getBytes(StandardCharsets.UTF_8));
            }
            byte[] id = java.util.Arrays.copyOf(md.digest(), 16);
            COSArray array = new COSArray();
            array.add(new COSString(id));
            array.add(new COSString(id));
            return array;
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** For messages: {@code 63x88 mm on A4}. */
    public static String describe(CardOptions o) {
        return o.size() + " mm on " + o.paper().name().toLowerCase(Locale.ROOT);
    }
}
