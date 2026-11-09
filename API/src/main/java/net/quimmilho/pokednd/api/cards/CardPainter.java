/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.cards;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import net.quimmilho.pokednd.api.cards.CardData.MoveLine;
import net.quimmilho.pokednd.api.cards.CardData.Multiplier;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/** Draws the front and the back of a card (chapters 9.2 and 9.7) and the page furniture: crop marks and the credit line. */
final class CardPainter {

    /** How far the printed area runs past the cut line on each side. */
    record Bleed(double left, double top, double right, double bottom) {
    }

    private static final Color TEXT = new Color(0x1B1B1F);
    private static final Color GREY = new Color(0x5A5A62);
    private static final Color NAVY = new Color(0x1F2A44);
    /** The art is cut down to this size before it is embedded: 700 px over a 57 mm box is about 310 dpi. */
    private static final int ART_MAX_PX = 700;

    private final PDDocument doc;
    private final CardFonts fonts;
    private final CardOptions options;
    private final Map<String, PDImageXObject> images = new HashMap<>();
    private final Map<String, BitMatrix> qrCodes = new HashMap<>();
    private final List<String> warnings = new ArrayList<>();

    CardPainter(PDDocument doc, CardFonts fonts, CardOptions options) {
        this.doc = doc;
        this.fonts = fonts;
        this.options = options;
    }

    List<String> warnings() {
        return warnings;
    }

    // ---------------------------------------------------------------- texts in the two languages

    private boolean pt() {
        return options.language().equals("pt");
    }

    private String t(String en, String pt) {
        return pt() ? pt : en;
    }

    /** The credit line that closes every page (chapter 21.3). */
    String creditLine() {
        return t("Fan project · not for sale · art based on Cobblemon models, CC BY-NC 3.0, modified",
                "Projeto de fãs · não é para venda · arte baseada em modelos do Cobblemon, CC BY-NC 3.0, modificada");
    }

    // ---------------------------------------------------------------- front

    void front(Pen pen, CardData c, double x, double y, Bleed bleed) throws IOException {
        double w = options.size().widthMm();
        double h = options.size().heightMm();
        double f = Math.min(w / 63.0, h / 88.0);
        boolean ink = options.inkSaving();
        Color type = TypeColours.of(c.primaryType());
        Color typeDark = TypeColours.darken(type, 0.35);
        Color frame = ink ? Color.WHITE : type;
        Color panel = ink ? Color.WHITE : TypeColours.lighten(type, 0.88);
        Color artBg = ink ? Color.WHITE : TypeColours.lighten(type, 0.68);

        pen.fillRect(x - bleed.left(), y - bleed.top(), w + bleed.left() + bleed.right(), h + bleed.top() + bleed.bottom(), frame);
        if (ink) {
            pen.roundRect(x + 0.7 * f, y + 0.7 * f, w - 1.4 * f, h - 1.4 * f, 2.0 * f, null, typeDark, 0.5 * f);
        }
        double fr = 2.2 * f;
        pen.roundRect(x + fr, y + fr, w - 2 * fr, h - 2 * fr, 1.2 * f, panel, ink ? typeDark : null, 0.3 * f);

        double pad = 1.6 * f;
        double cx = x + fr + pad;
        double cw = w - 2 * (fr + pad);
        double right = cx + cw;
        double bottom = y + h - fr - pad;
        double cy = y + fr + pad;

        // ---- header: stage badge, name, HP
        double hh = 5.4 * f;
        String stage = CardFonts.safe(fonts.bold, c.stage().toUpperCase(Locale.ROOT));
        double badgeSize = size(3.6 * f);
        double badgeW = Pen.width(fonts.bold, badgeSize, stage) + 2.0 * f;
        double badgeH = 3.0 * f;
        double badgeY = cy + (hh - badgeH) / 2;
        pen.roundRect(cx, badgeY, badgeW, badgeH, 0.6 * f, ink ? Color.WHITE : TypeColours.darken(type, 0.45), ink ? TEXT : null, 0.2 * f);
        pen.text(fonts.bold, badgeSize, cx + 1.0 * f, centredBaseline(badgeY + badgeH / 2, badgeSize), stage, ink ? TEXT : Color.WHITE);

        String hp = CardFonts.safe(fonts.bold, "HP " + c.hp());
        double hpSize = size(7.0 * f);
        pen.textRight(fonts.bold, hpSize, right, centredBaseline(cy + hh / 2, hpSize), hp, TEXT);
        double hpW = Pen.width(fonts.bold, hpSize, hp);
        double nameX = cx + badgeW + 1.4 * f;
        Fitted name = fit(fonts.bold, size(8.4 * f), size(5.0 * f), right - hpW - 1.4 * f - nameX, c.name());
        pen.text(fonts.bold, name.size, nameX, centredBaseline(cy + hh / 2, name.size), name.text, TEXT);
        cy += hh + 0.6 * f;

        // ---- what goes below the art; the art takes whatever height is left
        double typeH = 3.6 * f;
        double moveH = 5.9 * f;
        double lineH = 3.1 * f;
        double flavourH = 6.0 * f;
        double gap = 0.6 * f;
        double below = typeH + 4 * moveH + 4 * lineH + flavourH + 6 * gap;
        double artH = Math.max(8 * f, bottom - cy - below);
        pen.roundRect(cx, cy, cw, artH, 0.8 * f, artBg, ink ? typeDark : TypeColours.darken(type, 0.15), 0.25 * f);
        drawArt(pen, c, cx + 0.8 * f, cy + 0.8 * f, cw - 1.6 * f, artH - 1.6 * f, artBg);
        cy += artH + gap;

        // ---- Dex number and types
        StringBuilder types = new StringBuilder();
        for (int i = 0; i < c.typeNames().size(); i++) {
            types.append(i == 0 ? "" : " / ").append(c.typeNames().get(i));
        }
        String typeLine = String.format(Locale.ROOT, "#%03d · %s", c.dex(), types);
        Fitted tl = fit(fonts.bold, size(5.4 * f), size(3.8 * f), cw, typeLine);
        pen.text(fonts.bold, tl.size, cx, centredBaseline(cy + typeH / 2, tl.size), tl.text, TEXT);
        cy += typeH;
        pen.line(cx, cy, right, cy, 0.15 * f, ink ? GREY : typeDark);
        cy += gap;

        // ---- the four moves
        for (int i = 0; i < 4; i++) {
            if (i < c.moves().size()) {
                move(pen, c.moves().get(i), cx, cy, cw, f, ink);
            }
            cy += moveH;
        }
        pen.line(cx, cy - 0.3 * f, right, cy - 0.3 * f, 0.15 * f, ink ? GREY : typeDark);
        cy += gap - 0.3 * f;

        // ---- ability, weaknesses, resistances, stats
        StringBuilder ab = new StringBuilder(t("Ability: ", "Habilidade: "));
        ab.append(String.join(" / ", c.abilities()));
        if (c.hiddenAbility() != null) {
            ab.append(" · ").append(t("Hidden: ", "Oculta: ")).append(c.hiddenAbility());
        }
        line(pen, fonts.regular, ab.toString(), cx, cy, cw, lineH, 4.8 * f, TEXT);
        cy += lineH;
        line(pen, fonts.regular, t("Weak: ", "Fraqueza: ") + multipliers(c.weaknesses()), cx, cy, cw, lineH, 4.6 * f, TEXT);
        cy += lineH;
        String res = t("Resist: ", "Resiste: ") + multipliers(c.resistances());
        if (!c.immunities().isEmpty()) {
            res += (c.resistances().isEmpty() ? "" : " · ") + t("Immune: ", "Imune: ") + String.join(", ", c.immunities());
        }
        line(pen, fonts.regular, res, cx, cy, cw, lineH, 4.6 * f, TEXT);
        cy += lineH;
        CardData.Stats s = c.stats();
        line(pen, fonts.bold, "ATK " + s.attack() + " · DEF " + s.defense() + " · SPA " + s.specialAttack() + " · SPD " + s.specialDefense()
                + " · SPE " + s.speed(), cx, cy, cw, lineH, 4.8 * f, TEXT);
        cy += lineH + gap;

        // ---- flavour text, up to three lines
        if (!c.flavour().isBlank()) {
            double fs = size(4.2 * f);
            List<String> lines = wrap(fonts.italic, fs, cw, "“" + c.flavour() + "”", 3);
            double leading = fs / Pen.PT_PER_MM * 1.3;
            double by = cy + fs / Pen.PT_PER_MM * 0.95;
            for (String l : lines) {
                pen.text(fonts.italic, fs, cx, by, l, GREY);
                by += leading;
            }
        }
    }

    private void move(Pen pen, MoveLine m, double x, double y, double w, double f, boolean ink) throws IOException {
        Color type = TypeColours.of(m.typeId());
        double nameBase = y + 2.5 * f;
        pen.circle(x + 1.0 * f, y + 1.85 * f, 0.95 * f, ink ? Color.WHITE : type, TypeColours.darken(type, 0.4), 0.2 * f);

        // right side: PP, dice, category icon, from the edge inwards
        String pp = CardFonts.safe(fonts.regular, "PP " + m.pp());
        double ppSize = size(4.4 * f);
        double right = x + w;
        pen.textRight(fonts.regular, ppSize, right, nameBase, pp, GREY);
        right -= Pen.width(fonts.regular, ppSize, pp) + 1.6 * f;
        String dice = CardFonts.safe(fonts.bold, m.dice());
        double diceSize = size(6.0 * f);
        pen.textRight(fonts.bold, diceSize, right, nameBase, dice, TEXT);
        right -= Pen.width(fonts.bold, diceSize, dice) + 1.2 * f;
        categoryIcon(pen, m.category(), right - 0.9 * f, y + 1.85 * f, 0.9 * f);
        right -= 2.4 * f;

        Fitted name = fit(fonts.bold, size(6.0 * f), size(4.2 * f), right - (x + 2.6 * f), m.name());
        pen.text(fonts.bold, name.size, x + 2.6 * f, nameBase, name.text, TEXT);

        // second line: the hit roll, then as much of the effect as fits
        double lineBase = y + 5.0 * f;
        double hitSize = size(4.3 * f);
        String hit = CardFonts.safe(fonts.regular, m.hit());
        pen.text(fonts.regular, hitSize, x + 2.6 * f, lineBase, hit, TEXT);
        double effectX = x + 2.6 * f + Pen.width(fonts.regular, hitSize, hit) + 1.8 * f;
        double room = x + w - effectX;
        if (room > 8 * f && !m.effect().isBlank()) {
            Fitted e = fit(fonts.italic, size(4.1 * f), size(4.1 * f), room, m.effect());
            pen.text(fonts.italic, e.size, effectX, lineBase, e.text, GREY);
        }
    }

    private void categoryIcon(Pen pen, String category, double cx, double cy, double r) throws IOException {
        switch (category) {
            case "physical" -> {
                pen.circle(cx, cy, r, new Color(0xD9482B), null, 0);
                pen.circle(cx, cy, r * 0.38, Color.WHITE, null, 0);
            }
            case "special" -> {
                pen.circle(cx, cy, r, new Color(0x3B6FD1), null, 0);
                pen.circle(cx, cy, r * 0.5, null, Color.WHITE, r * 0.22);
            }
            default -> {
                pen.circle(cx, cy, r, new Color(0x8A8A92), null, 0);
                pen.fillRect(cx - r * 0.55, cy - r * 0.15, r * 1.1, r * 0.3, Color.WHITE);
            }
        }
    }

    private void line(Pen pen, PDFont font, String text, double x, double y, double w, double h, double sizePt, Color c) throws IOException {
        Fitted fitted = fit(font, size(sizePt), size(sizePt * 0.78), w, text);
        pen.text(font, fitted.size, x, centredBaseline(y + h / 2, fitted.size), fitted.text, c);
    }

    /** Types grouped by multiplier, biggest first: {@code Fairy, Fire, Grass ×½ · Bug ×¼}. */
    private static String multipliers(List<Multiplier> list) {
        if (list.isEmpty()) {
            return "—";
        }
        java.util.TreeMap<Double, List<String>> groups = new java.util.TreeMap<>(java.util.Comparator.reverseOrder());
        for (Multiplier m : list) {
            groups.computeIfAbsent(m.factor(), k -> new ArrayList<>()).add(m.typeName());
        }
        StringBuilder sb = new StringBuilder();
        groups.forEach((factor, names) -> {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(String.join(", ", names)).append(" ×").append(factor(factor));
        });
        return sb.toString();
    }

    private static String factor(double f) {
        if (f == 0.5) {
            return "½";
        }
        if (f == 0.25) {
            return "¼";
        }
        return f == Math.rint(f) ? String.valueOf((long) f) : String.valueOf(f);
    }

    // ---------------------------------------------------------------- art

    private void drawArt(Pen pen, CardData c, double x, double y, double w, double h, Color background) throws IOException {
        if (c.artFile() == null) {
            pen.text(fonts.italic, 6, x + w / 2 - 6, y + h / 2, "?", GREY);
            return;
        }
        PDImageXObject image = image(c.artFile(), background);
        if (image == null) {
            warnings.add(c.key() + ": the art file " + c.artFile().getFileName() + " cannot be read");
            return;
        }
        double scale = Math.min(w / image.getWidth(), h / image.getHeight());
        double iw = image.getWidth() * scale;
        double ih = image.getHeight() * scale;
        pen.image(image, x + (w - iw) / 2, y + (h - ih) / 2, iw, ih);
    }

    /**
     * The art, laid on the colour of the art box and embedded as a high-quality JPEG: a whole generation of PNGs with transparency
     * would make a PDF of about 50 MB, a JPEG one about 10 MB. (The picture is cut down to {@value #ART_MAX_PX} px first.)
     */
    private PDImageXObject image(Path file, Color background) throws IOException {
        String key = file + "|" + background.getRGB();
        PDImageXObject cached = images.get(key);
        if (cached != null) {
            return cached;
        }
        BufferedImage src = ImageIO.read(file.toFile());
        if (src == null) {
            return null;
        }
        double s = Math.min(1.0, (double) ART_MAX_PX / Math.max(src.getWidth(), src.getHeight()));
        BufferedImage flat = new BufferedImage(Math.max(1, (int) Math.round(src.getWidth() * s)), Math.max(1, (int) Math.round(src.getHeight() * s)),
                BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = flat.createGraphics();
        g.setColor(background);
        g.fillRect(0, 0, flat.getWidth(), flat.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, flat.getWidth(), flat.getHeight(), null);
        g.dispose();
        PDImageXObject pd = JPEGFactory.createFromImage(doc, flat, 0.9f);
        images.put(key, pd);
        return pd;
    }

    // ---------------------------------------------------------------- back

    void back(Pen pen, CardData c, double x, double y, Bleed bleed) throws IOException {
        double w = options.size().widthMm();
        double h = options.size().heightMm();
        double f = Math.min(w / 63.0, h / 88.0);
        boolean ink = options.inkSaving();
        Color bg = ink ? Color.WHITE : NAVY;
        Color fg = ink ? NAVY : Color.WHITE;
        Color soft = ink ? new Color(0x9AA3B5) : new Color(0x3C4B73);

        pen.fillRect(x - bleed.left(), y - bleed.top(), w + bleed.left() + bleed.right(), h + bleed.top() + bleed.bottom(), bg);
        pen.roundRect(x + 2.2 * f, y + 2.2 * f, w - 4.4 * f, h - 4.4 * f, 1.8 * f, null, soft, 0.5 * f);

        // a plain ring with a band: the back's emblem
        double cx = x + w / 2;
        double ringY = y + 0.27 * h;
        double r = 0.17 * w;
        pen.circle(cx, ringY, r, null, soft, 1.1 * f);
        pen.line(cx - r, ringY, cx + r, ringY, 1.1 * f, soft);
        pen.circle(cx, ringY, r * 0.30, bg, soft, 1.1 * f);

        double titleSize = size(13 * f);
        pen.textCentred(fonts.bold, titleSize, cx, y + 0.27 * h + r + 7.5 * f, "PokeDnD", fg);
        String sub = CardFonts.safe(fonts.regular, t("species card", "carta de espécie"));
        pen.textCentred(fonts.regular, size(5.0 * f), cx, y + 0.27 * h + r + 11.5 * f, sub, soft.equals(fg) ? fg : fg);

        double q = Math.min(w * 0.52, h * 0.30);
        double qy = y + 0.62 * h;
        pen.roundRect(cx - q / 2, qy, q, q, 1.2 * f, Color.WHITE, ink ? NAVY : null, 0.3 * f);
        qr(pen, c.qrId(), cx - q / 2, qy, q);
        if (options.showBackId()) {
            String id = CardFonts.safe(fonts.regular, c.qrId());
            pen.textCentred(fonts.regular, size(4.4 * f), cx, qy + q + 3.4 * f, id, fg);
        }
    }

    /** Draws the QR code (as vector squares) in a white square, with a quiet zone of three modules. */
    private void qr(Pen pen, String text, double x, double y, double side) throws IOException {
        BitMatrix m = qrCodes.computeIfAbsent(text, id -> {
            try {
                return new QRCodeWriter().encode(id, BarcodeFormat.QR_CODE, 0, 0,
                        Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 0, EncodeHintType.CHARACTER_SET, "UTF-8"));
            } catch (WriterException e) {
                throw new IllegalStateException("cannot make a QR code for " + id, e);
            }
        });
        int n = m.getWidth();
        double cell = side / (n + 6);
        double ox = x + 3 * cell;
        double oy = y + 3 * cell;
        for (int row = 0; row < n; row++) {
            int col = 0;
            while (col < n) {
                if (!m.get(col, row)) {
                    col++;
                    continue;
                }
                int start = col;
                while (col < n && m.get(col, row)) {
                    col++;
                }
                pen.fillRect(ox + start * cell, oy + row * cell, (col - start) * cell, cell, Color.BLACK);
            }
        }
    }

    // ---------------------------------------------------------------- page furniture

    /** Crop marks at the cut lines, outside the grid. */
    void cropMarks(Pen pen, PageLayout layout) throws IOException {
        double w = options.size().widthMm();
        double h = options.size().heightMm();
        double off = options.bleedMm() + PageLayout.MARK_OFFSET;
        double len = PageLayout.MARK_LENGTH;
        double top = layout.originY();
        double bottom = layout.cardY(layout.rows() - 1) + h;
        double left = layout.originX();
        double rightEdge = layout.cardX(layout.cols() - 1) + w;
        for (int c = 0; c < layout.cols(); c++) {
            for (double xx : new double[] {layout.cardX(c), layout.cardX(c) + w}) {
                pen.line(xx, top - off - len, xx, top - off, 0.1, Color.BLACK);
                pen.line(xx, bottom + off, xx, bottom + off + len, 0.1, Color.BLACK);
            }
        }
        for (int r = 0; r < layout.rows(); r++) {
            for (double yy : new double[] {layout.cardY(r), layout.cardY(r) + h}) {
                pen.line(left - off - len, yy, left - off, yy, 0.1, Color.BLACK);
                pen.line(rightEdge + off, yy, rightEdge + off + len, yy, 0.1, Color.BLACK);
            }
        }
    }

    void footer(Pen pen, double paperWidthMm, double paperHeightMm) throws IOException {
        String text = CardFonts.safe(fonts.regular, creditLine());
        double size = 5.0;
        Fitted fitted = fit(fonts.regular, size, 3.5, paperWidthMm - 20, text);
        pen.textCentred(fonts.regular, fitted.size, paperWidthMm / 2, paperHeightMm - 3.2, fitted.text, GREY);
    }

    // ---------------------------------------------------------------- text helpers

    private static double size(double pt) {
        return Math.max(3.0, pt);
    }

    /** Baseline that puts the middle of the lower-case letters on {@code centreMm}. */
    private static double centredBaseline(double centreMm, double sizePt) {
        return centreMm + 0.36 * sizePt / Pen.PT_PER_MM;
    }

    private record Fitted(double size, String text) {
    }

    /** Shrinks the font down to {@code minPt} to fit the width; if it still does not fit, cuts the text and adds an ellipsis. */
    private static Fitted fit(PDFont font, double sizePt, double minPt, double maxWidthMm, String raw) {
        String text = CardFonts.safe(font, raw);
        double size = sizePt;
        while (Pen.width(font, size, text) > maxWidthMm && size > minPt) {
            size = Math.max(minPt, size - 0.1);
        }
        if (Pen.width(font, size, text) <= maxWidthMm) {
            return new Fitted(size, text);
        }
        String cut = text;
        while (cut.length() > 1 && Pen.width(font, size, cut + "…") > maxWidthMm) {
            cut = cut.substring(0, cut.length() - 1).stripTrailing();
        }
        return new Fitted(size, CardFonts.safe(font, cut + "…"));
    }

    /** Wraps text into at most {@code maxLines} lines; the last one ends with an ellipsis if text is left over. */
    private static List<String> wrap(PDFont font, double sizePt, double maxWidthMm, String raw, int maxLines) {
        String[] words = CardFonts.safe(font, raw).split(" ");
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int i = 0;
        for (; i < words.length; i++) {
            String candidate = current.length() == 0 ? words[i] : current + " " + words[i];
            if (Pen.width(font, sizePt, candidate) <= maxWidthMm || current.length() == 0) {
                current = new StringBuilder(candidate);
            } else {
                if (lines.size() == maxLines - 1) {
                    break;
                }
                lines.add(current.toString());
                current = new StringBuilder(words[i]);
            }
        }
        if (i < words.length) {
            // more text than room: fill the last line as far as it goes
            StringBuilder rest = new StringBuilder(current);
            for (; i < words.length; i++) {
                rest.append(' ').append(words[i]);
            }
            current = new StringBuilder(fit(font, sizePt, sizePt, maxWidthMm, rest.toString()).text);
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }
}
