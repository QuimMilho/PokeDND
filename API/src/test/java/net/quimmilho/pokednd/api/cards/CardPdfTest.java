/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.cards;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import net.quimmilho.pokednd.api.cards.CardData.MoveLine;
import net.quimmilho.pokednd.api.cards.CardData.Multiplier;
import net.quimmilho.pokednd.api.cards.CardData.Stats;
import net.quimmilho.pokednd.api.cards.CardOptions.CardSize;
import net.quimmilho.pokednd.api.cards.CardOptions.Paper;
import net.quimmilho.pokednd.api.error.ApiException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The PDF engine on made-up cards: no database, no bundle and no extracted resources needed. */
class CardPdfTest {

    private static Path art;

    @BeforeAll
    static void makeArt(@TempDir Path tmp) throws IOException {
        // a transparent picture with a coloured disc, standing in for card art
        BufferedImage img = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(200, 60, 60));
        g.fillOval(64, 64, 384, 384);
        g.dispose();
        art = tmp.resolve("art.png");
        Files.createDirectories(art.getParent());
        ImageIO.write(img, "png", art.toFile());
        // @TempDir is deleted after the class; keep a copy that survives
        Path keep = Files.createTempFile("pokednd-art", ".png");
        Files.copy(art, keep, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        keep.toFile().deleteOnExit();
        art = keep;
    }

    static CardData card(int i) {
        String key = String.format("%04d_testmon%d", i, i);
        return new CardData(key, CardIds.qrId(key), i, "testmon" + i, "testmon" + i + "_base", "Testmon " + i, List.of("fire", "flying"),
                List.of("Fire", "Flying"), "Stage 1", 60 + i, new Stats(70, 60, 80, 55, 90),
                List.of(new MoveLine("Ember", "fire", "special", "2d6", "d20 · miss 1–4 · hit 5–19 · crit 20", "May burn the target.", 25),
                        new MoveLine("Wing Attack", "flying", "physical", "2d8", "d20 · hit 1–19 · crit 20", "Hits with wings.", 35),
                        new MoveLine("Growl", "normal", "status", "—", "d20 · miss 1–3 · hit 4–20", "Lowers Attack.", 40),
                        new MoveLine("Flame Wheel", "fire", "physical", "3d6", "always hits", "A rolling charge.", 25)),
                List.of("Blaze"), "Solar Power", List.of(new Multiplier("water", "Water", 2.0), new Multiplier("rock", "Rock", 4.0)),
                List.of(new Multiplier("fire", "Fire", 0.5), new Multiplier("bug", "Bug", 0.25)), List.of("Ground"),
                "A made-up creature with an Ação, a Pokémon-like name and an accent: ãõçéü. It exists only to test the card engine.", art, false, List.of());
    }

    static List<CardData> cards(int n) {
        List<CardData> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            list.add(card(i));
        }
        return list;
    }

    // ---------------------------------------------------------------- page counts

    @Test
    void nCardsGiveTheRightNumberOfPagesForThePaperAndSize() throws IOException {
        CardOptions a4 = CardOptions.defaults();
        PageLayout layout = PageLayout.compute(a4);
        assertEquals(3, layout.cols());
        assertEquals(3, layout.rows(), "63x88 cards fit 3 by 3 on A4 with bleed, gap and marks");

        int[][] cases = {{1, 1}, {9, 1}, {10, 2}, {18, 2}, {19, 3}, {27, 3}};
        for (int[] c : cases) {
            CardPdf.Result r = CardPdf.write(cards(c[0]), a4);
            assertEquals(c[1], r.pages(), c[0] + " cards on A4");
            assertEquals(c[1], PdfTools.pageCount(r.pdf()));
            assertEquals(c[1], r.sheets());
        }
        // with backs every sheet has a second page
        assertEquals(4, CardPdf.write(cards(10), a4.withBacks(true)).pages());
        assertEquals(2, CardPdf.write(cards(9), a4.withBacks(true)).pages());
        // copies multiply the cards: 3 cards x 4 copies = 12 printed cards = 2 sheets
        CardPdf.Result copies = CardPdf.write(cards(3), a4.withCopies(4));
        assertEquals(12, copies.printed());
        assertEquals(2, copies.pages());
        // tarot size 70x120: 2 by 2 on A4
        PageLayout tarot = PageLayout.compute(a4.withSize(new CardSize(70, 120)));
        assertEquals(4, tarot.perSheet());
        assertEquals(2, CardPdf.write(cards(5), a4.withSize(new CardSize(70, 120))).pages());
        // mini 44x63: 4 by 4 on A4
        assertEquals(16, PageLayout.compute(a4.withSize(new CardSize(44, 63))).perSheet());
        // US Letter is a little shorter: 3 by 2
        PageLayout letter = PageLayout.compute(a4.withPaper(Paper.LETTER));
        assertEquals(3, letter.cols());
        assertEquals(2, letter.rows());
        assertEquals(6, letter.perSheet());
    }

    @Test
    void impossibleSizesAndOptionsAreRefusedWithAClearMessage() {
        // the biggest allowed card still fits on A4, alone on its sheet
        assertEquals(1, PageLayout.compute(CardOptions.defaults().withSize(new CardSize(150, 220))).perSheet());
        ApiException e = assertThrows(ApiException.class, () -> CardSize.parse("12x34"));
        assertEquals("INVALID_EXPORT_OPTIONS", e.code());
        assertEquals(422, e.status());
        assertThrows(ApiException.class, () -> new CardOptions(CardSize.TRADING, Paper.A4, 11, 2, false, "en", false, 1, false, true));
        assertThrows(ApiException.class, () -> CardSize.parse("wide"));
        assertEquals(new CardSize(63, 88), CardSize.parse("63x88"));
        assertThrows(ApiException.class, () -> CardOptions.defaults().withCopies(0));
        assertThrows(ApiException.class, () -> CardOptions.defaults().withLanguage("fr"));
    }

    @Test
    void theGridIsCentredAndTheCutLinesAreOneGapApart() {
        PageLayout l = PageLayout.compute(CardOptions.defaults());
        double gridW = 3 * 63 + 2 * 2;
        assertEquals(l.cardX(0), (210 - gridW) / 2, 1.5, "centred on the paper (within the margins)");
        assertEquals(2.0, l.cardX(1) - (l.cardX(0) + 63), 1e-9);
        assertEquals(2.0, l.cardY(1) - (l.cardY(0) + 88), 1e-9);
        assertEquals(new PageLayout.Slot(2, 0), l.mirrored(new PageLayout.Slot(0, 0)));
        assertEquals(new PageLayout.Slot(1, 2), l.mirrored(new PageLayout.Slot(1, 2)));
    }

    // ---------------------------------------------------------------- QR codes

    @Test
    void theQrCodesOnTheBacksDecodeToTheCardIds() throws IOException {
        List<CardData> cards = cards(7);
        CardPdf.Result r = CardPdf.write(cards, CardOptions.defaults().withBacks(true));
        assertEquals(2, r.pages());
        BufferedImage back = PdfTools.render(r.pdf(), 2, 150);
        List<PdfTools.Qr> found = PdfTools.qrCodes(back);

        Set<String> expected = new HashSet<>();
        cards.forEach(c -> expected.add(c.qrId()));
        Set<String> actual = new HashSet<>();
        found.forEach(q -> actual.add(q.text()));
        assertEquals(expected, actual, "every back carries its card's ID");
        assertEquals(7, found.size());
        for (CardData c : cards) {
            assertTrue(c.qrId().matches("PKC1-[0-9A-Z]{16}"), c.qrId());
        }
    }

    @Test
    void theBacksAreMirroredForALongEdgeFlip() throws IOException {
        List<CardData> cards = cards(3); // the first row of the sheet: columns 1, 2, 3
        CardPdf.Result r = CardPdf.write(cards, CardOptions.defaults().withBacks(true));
        // the manifest says so ...
        List<CardPdf.ManifestRow> front = r.manifest().stream().filter(m -> m.side().equals("front")).toList();
        List<CardPdf.ManifestRow> back = r.manifest().stream().filter(m -> m.side().equals("back")).toList();
        assertEquals(List.of(1, 2, 3), front.stream().map(CardPdf.ManifestRow::col).toList());
        assertEquals(List.of(3, 2, 1), back.stream().map(CardPdf.ManifestRow::col).toList());
        assertEquals(List.of(1, 2), r.manifest().stream().map(CardPdf.ManifestRow::page).distinct().toList());
        // ... and so do the QR codes: the card that is leftmost on the front is rightmost on the back
        BufferedImage img = PdfTools.render(r.pdf(), 2, 150);
        List<PdfTools.Qr> qrs = new ArrayList<>(PdfTools.qrCodes(img));
        qrs.sort(java.util.Comparator.comparingDouble(PdfTools.Qr::x));
        assertEquals(List.of(cards.get(2).qrId(), cards.get(1).qrId(), cards.get(0).qrId()), qrs.stream().map(PdfTools.Qr::text).toList());
    }

    @Test
    void theManifestListsEveryPrintedCardWithItsPosition() throws IOException {
        CardPdf.Result r = CardPdf.write(cards(4), CardOptions.defaults().withCopies(2).withBacks(true));
        assertEquals(8 * 2, r.manifest().size(), "8 printed cards, front and back");
        String csv = r.manifestCsv();
        assertTrue(csv.startsWith("page,side,row,column,card_key,qr_id,copy\n"));
        assertTrue(csv.contains("1,front,1,1,0001_testmon1,PKC1-"));
        assertEquals(1 + 16, csv.lines().count());
    }

    // ---------------------------------------------------------------- the same input gives the same bytes

    @Test
    void theSameCardsAndOptionsGiveTheSameBytes() throws IOException {
        CardOptions options = CardOptions.defaults().withBacks(true);
        byte[] a = CardPdf.write(cards(5), options).pdf();
        byte[] b = CardPdf.write(cards(5), options).pdf();
        assertArrayEquals(a, b);
        assertNotEquals(0, a.length);
        // another input gives another file
        assertFalse(java.util.Arrays.equals(a, CardPdf.write(cards(6), options).pdf()));
        String head = new String(a, 0, 8, StandardCharsets.ISO_8859_1);
        assertTrue(head.startsWith("%PDF-"), head);
        String all = new String(a, StandardCharsets.ISO_8859_1);
        assertFalse(all.contains("/CreationDate"), "no dates in the file");
        assertFalse(all.contains("/ModDate"));
    }

    // ---------------------------------------------------------------- text, languages and options

    @Test
    void thePageHasTheCardTextAndTheCreditLine() throws IOException {
        CardPdf.Result r = CardPdf.write(cards(1), CardOptions.defaults());
        String text = PdfTools.text(r.pdf(), 1);
        for (String expected : List.of("Testmon 1", "HP 61", "STAGE 1", "#001 · Fire / Flying", "Ember", "2d6", "PP 25", "Wing Attack",
                "Ability: Blaze · Hidden: Solar Power", "Weak: Rock ×4 · Water ×2", "Resist: Fire ×½ · Bug ×¼ · Immune: Ground",
                "ATK 70 · DEF 60 · SPA 80 · SPD 55 · SPE 90", "ãõçéü",
                "Fan project · not for sale · art based on Cobblemon models, CC BY-NC 3.0, modified")) {
            assertTrue(text.contains(expected), "missing \"" + expected + "\" in:\n" + text);
        }
    }

    @Test
    void thePortugueseCreditLineAndLabels() throws IOException {
        CardPdf.Result r = CardPdf.write(cards(1), CardOptions.defaults().withLanguage("pt"));
        String text = PdfTools.text(r.pdf(), 1);
        assertTrue(text.contains("Projeto de fãs · não é para venda · arte baseada em modelos do Cobblemon, CC BY-NC 3.0, modificada"), text);
        assertTrue(text.contains("Habilidade: Blaze"), text);
    }

    @Test
    void inkSavingAndTheBackIdTextWork() throws IOException {
        CardPdf.Result r = CardPdf.write(cards(2), CardOptions.defaults().withInkSaving(true).withBacks(true).withShowBackId(true));
        assertEquals(2, r.pages());
        String back = PdfTools.text(r.pdf(), 2);
        assertTrue(back.contains(cards(2).get(0).qrId()), back);
        // the QR codes still decode on the white back
        assertEquals(2, PdfTools.qrCodes(PdfTools.render(r.pdf(), 2, 150)).size());
    }

    @Test
    void aCardWithoutArtStillPrintsAndTheWarningTravelsWithTheResult() throws IOException {
        CardData base = card(1);
        CardData noArt = new CardData(base.key(), base.qrId(), base.dex(), base.speciesId(), base.formId(), base.name(), base.types(),
                base.typeNames(), base.stage(), base.hp(), base.stats(), base.moves(), base.abilities(), base.hiddenAbility(), base.weaknesses(),
                base.resistances(), base.immunities(), base.flavour(), null, false, List.of("0001_testmon1: no card art and no icon"));
        CardPdf.Result r = CardPdf.write(List.of(noArt), CardOptions.defaults());
        assertEquals(1, r.pages());
        assertEquals(List.of("0001_testmon1: no card art and no icon"), r.warnings());
    }

    @Test
    void anEmptySelectionStillMakesAValidPdf() throws IOException {
        CardPdf.Result r = CardPdf.write(List.of(), CardOptions.defaults());
        assertEquals(1, PdfTools.pageCount(r.pdf()));
        assertEquals(0, r.printed());
    }
}
