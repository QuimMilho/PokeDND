/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.cards;

import java.awt.Color;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Draws on one PDF page with millimetres measured from the top-left corner (PDF itself counts points from the bottom-left).
 */
final class Pen {

    static final double PT_PER_MM = 72.0 / 25.4;
    private static final double KAPPA = 0.5522847498;

    private final PDPageContentStream cs;
    private final double pageHeightMm;

    Pen(PDPageContentStream cs, double pageHeightMm) {
        this.cs = cs;
        this.pageHeightMm = pageHeightMm;
    }

    private float x(double mm) {
        return (float) (mm * PT_PER_MM);
    }

    private float y(double mm) {
        return (float) ((pageHeightMm - mm) * PT_PER_MM);
    }

    void save() throws IOException {
        cs.saveGraphicsState();
    }

    void restore() throws IOException {
        cs.restoreGraphicsState();
    }

    void clipRect(double x, double y, double w, double h) throws IOException {
        cs.addRect(x(x), y(y + h), x(w), x(h));
        cs.clip();
    }

    void fillRect(double x, double y, double w, double h, Color c) throws IOException {
        cs.setNonStrokingColor(c);
        cs.addRect(x(x), y(y + h), x(w), x(h));
        cs.fill();
    }

    void strokeRect(double x, double y, double w, double h, double lineMm, Color c) throws IOException {
        cs.setStrokingColor(c);
        cs.setLineWidth(x(lineMm));
        cs.addRect(x(x), y(y + h), x(w), x(h));
        cs.stroke();
    }

    /** A rounded rectangle, filled and/or outlined (null colour = not drawn). */
    void roundRect(double x, double y, double w, double h, double r, Color fill, Color stroke, double lineMm) throws IOException {
        double k = r * KAPPA;
        float left = x(x);
        float right = x(x + w);
        float top = y(y);
        float bottom = y(y + h);
        float rr = x(r);
        float kk = x(k);
        cs.moveTo(left + rr, top);
        cs.lineTo(right - rr, top);
        cs.curveTo(right - rr + kk, top, right, top - rr + kk, right, top - rr);
        cs.lineTo(right, bottom + rr);
        cs.curveTo(right, bottom + rr - kk, right - rr + kk, bottom, right - rr, bottom);
        cs.lineTo(left + rr, bottom);
        cs.curveTo(left + rr - kk, bottom, left, bottom + rr - kk, left, bottom + rr);
        cs.lineTo(left, top - rr);
        cs.curveTo(left, top - rr + kk, left + rr - kk, top, left + rr, top);
        cs.closePath();
        paint(fill, stroke, lineMm);
    }

    void circle(double cx, double cy, double r, Color fill, Color stroke, double lineMm) throws IOException {
        double k = r * KAPPA;
        cs.moveTo(x(cx + r), y(cy));
        cs.curveTo(x(cx + r), y(cy - k), x(cx + k), y(cy - r), x(cx), y(cy - r));
        cs.curveTo(x(cx - k), y(cy - r), x(cx - r), y(cy - k), x(cx - r), y(cy));
        cs.curveTo(x(cx - r), y(cy + k), x(cx - k), y(cy + r), x(cx), y(cy + r));
        cs.curveTo(x(cx + k), y(cy + r), x(cx + r), y(cy + k), x(cx + r), y(cy));
        cs.closePath();
        paint(fill, stroke, lineMm);
    }

    private void paint(Color fill, Color stroke, double lineMm) throws IOException {
        if (fill != null) {
            cs.setNonStrokingColor(fill);
        }
        if (stroke != null) {
            cs.setStrokingColor(stroke);
            cs.setLineWidth(x(lineMm));
        }
        if (fill != null && stroke != null) {
            cs.fillAndStroke();
        } else if (fill != null) {
            cs.fill();
        } else if (stroke != null) {
            cs.stroke();
        } else {
            cs.closeAndStroke();
        }
    }

    void line(double x1, double y1, double x2, double y2, double lineMm, Color c) throws IOException {
        cs.setStrokingColor(c);
        cs.setLineWidth(x(lineMm));
        cs.moveTo(x(x1), y(y1));
        cs.lineTo(x(x2), y(y2));
        cs.stroke();
    }

    void image(PDImageXObject image, double x, double y, double w, double h) throws IOException {
        cs.drawImage(image, x(x), y(y + h), x(w), x(h));
    }

    /** Draws text with its baseline at {@code baselineY}. The text must already be safe for the font ({@link CardFonts#safe}). */
    void text(PDFont font, double sizePt, double x, double baselineY, String text, Color c) throws IOException {
        if (text.isEmpty()) {
            return;
        }
        cs.beginText();
        cs.setFont(font, (float) sizePt);
        cs.setNonStrokingColor(c);
        cs.newLineAtOffset(x(x), y(baselineY));
        cs.showText(text);
        cs.endText();
    }

    void textRight(PDFont font, double sizePt, double rightX, double baselineY, String text, Color c) throws IOException {
        text(font, sizePt, rightX - width(font, sizePt, text), baselineY, text, c);
    }

    void textCentred(PDFont font, double sizePt, double centreX, double baselineY, String text, Color c) throws IOException {
        text(font, sizePt, centreX - width(font, sizePt, text) / 2, baselineY, text, c);
    }

    /** The width of a text in millimetres. */
    static double width(PDFont font, double sizePt, String text) {
        try {
            return font.getStringWidth(text) / 1000.0 * sizePt / PT_PER_MM;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
