/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.cards;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

/** Reads a PDF back: page count, text, page images and QR codes. */
final class PdfTools {

    private PdfTools() {
    }

    static int pageCount(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return doc.getNumberOfPages();
        }
    }

    /** The text of one page (1-based), as PDFBox reads it. */
    static String text(byte[] pdf, int page) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            stripper.setLineSeparator("\n");
            return stripper.getText(doc).replace("\r\n", "\n");
        }
    }

    /** One page (1-based) as an image. */
    static BufferedImage render(byte[] pdf, int page, float dpi) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFRenderer(doc).renderImageWithDPI(page - 1, dpi);
        }
    }

    /** A decoded QR code and where its centre is (pixels). */
    record Qr(String text, double x, double y) {
    }

    static List<Qr> qrCodes(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(w, h, pixels)));
        MultiFormatReader reader = new MultiFormatReader();
        Map<DecodeHintType, Object> hints = Map.of(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER, Boolean.TRUE);
        List<Qr> out = new ArrayList<>();
        try {
            for (Result r : new GenericMultipleBarcodeReader(reader).decodeMultiple(bitmap, hints)) {
                double x = 0;
                double y = 0;
                ResultPoint[] pts = r.getResultPoints();
                for (ResultPoint p : pts) {
                    x += p.getX();
                    y += p.getY();
                }
                out.add(new Qr(r.getText(), x / pts.length, y / pts.length));
            }
        } catch (NotFoundException e) {
            // none found: an empty list
        }
        return out;
    }
}
