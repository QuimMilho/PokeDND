/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.trainers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.imageio.ImageIO;
import net.quimmilho.pokednd.api.error.ApiException;
import org.junit.jupiter.api.Test;

/** Which skin files are accepted and what is left of them (chapter 3.6). Needs no database. */
class SkinImagesTest {

    private static final int LIMIT = 65536;

    private static String codeOf(byte[] file) {
        ApiException e = assertThrows(ApiException.class, () -> SkinImages.clean(file, LIMIT));
        assertEquals(422, e.status());
        return e.code();
    }

    @Test
    void aValidSkinIsAccepted() throws IOException {
        byte[] file = Pngs.skin(7);
        SkinImages.Clean clean = SkinImages.clean(file, LIMIT);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(clean.png()));
        assertEquals(64, image.getWidth());
        assertEquals(64, image.getHeight());
        assertEquals(64, clean.sha256().length());
        assertTrue(clean.sha256().matches("[0-9a-f]{64}"));
        // the pixels are kept, transparency included
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(file));
        for (int y = 0; y < 64; y += 9) {
            for (int x = 0; x < 64; x += 9) {
                assertEquals(original.getRGB(x, y), image.getRGB(x, y), x + "," + y);
            }
        }
        assertEquals(0, image.getRGB(0, 0) >>> 24, "the transparent corner stays transparent");
    }

    @Test
    void metadataAndTrailingBytesAreGone() throws IOException {
        String secret = "my-secret-gps-position-12.34";
        byte[] dirty = Pngs.withMetadata(Pngs.skin(3), secret);
        assertTrue(new String(dirty, StandardCharsets.ISO_8859_1).contains(secret), "the test file really has the metadata");
        // the dirty file is still a PNG that Java can read
        assertEquals(64, ImageIO.read(new ByteArrayInputStream(dirty)).getWidth());

        SkinImages.Clean clean = SkinImages.clean(dirty, LIMIT);
        String text = new String(clean.png(), StandardCharsets.ISO_8859_1);
        assertFalse(text.contains(secret), "the text chunk is gone");
        assertFalse(text.contains("tEXt"));
        assertFalse(text.contains("TRAILING-GARBAGE"));
        // and the result is the same as cleaning the clean file
        assertArrayEquals(SkinImages.clean(Pngs.skin(3), LIMIT).png(), clean.png());
    }

    @Test
    void theSameFileGivesTheSameBytesAndDifferentSkinsDiffer() {
        assertArrayEquals(SkinImages.clean(Pngs.skin(1), LIMIT).png(), SkinImages.clean(Pngs.skin(1), LIMIT).png());
        assertFalse(Arrays.equals(SkinImages.clean(Pngs.skin(1), LIMIT).png(), SkinImages.clean(Pngs.skin(2), LIMIT).png()));
        assertFalse(SkinImages.clean(Pngs.skin(1), LIMIT).sha256().equals(SkinImages.clean(Pngs.skin(2), LIMIT).sha256()));
    }

    @Test
    void aPictureThatIsNot64By64IsRefused() {
        assertEquals("SKIN_WRONG_SIZE", codeOf(Pngs.png(64, 32, 0)));
        assertEquals("SKIN_WRONG_SIZE", codeOf(Pngs.png(128, 128, 0)));
        assertEquals("SKIN_WRONG_SIZE", codeOf(Pngs.png(1, 1, 0)));
        assertEquals("SKIN_WRONG_SIZE", codeOf(Pngs.png(65, 64, 0)));
    }

    @Test
    void aFileThatIsNotAPngIsRefused() throws IOException {
        assertEquals("SKIN_NOT_PNG", codeOf("this is not a picture, it is just text that is long enough".getBytes(StandardCharsets.UTF_8)));
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream jpeg = new java.io.ByteArrayOutputStream();
        ImageIO.write(image, "jpg", jpeg);
        assertEquals("SKIN_NOT_PNG", codeOf(jpeg.toByteArray()));
        java.io.ByteArrayOutputStream gif = new java.io.ByteArrayOutputStream();
        ImageIO.write(image, "gif", gif);
        assertEquals("SKIN_NOT_PNG", codeOf(gif.toByteArray()));
    }

    @Test
    void anEmptyOrBrokenOrHugeFileIsRefused() {
        assertEquals("SKIN_EMPTY", codeOf(new byte[0]));
        // a PNG with its picture data cut off
        byte[] whole = Pngs.skin(0);
        assertEquals("SKIN_UNREADABLE", codeOf(Arrays.copyOf(whole, 60)));
        ApiException big = assertThrows(ApiException.class, () -> SkinImages.clean(whole, 100));
        assertEquals("SKIN_TOO_LARGE", big.code());
        assertTrue(big.getMessage().contains("limit is 100"), big.getMessage());
    }

    @Test
    void aPictureWithAHugeSizeIsRefusedFromTheHeaderAlone() {
        // a 30000 x 30000 header with no picture data: it must be refused by its size, not by trying to decode it
        byte[] header = Arrays.copyOf(Pngs.png(64, 64, 0), 60);
        header[16] = 0;
        header[17] = 0;
        header[18] = 0x75;
        header[19] = 0x30;
        header[20] = 0;
        header[21] = 0;
        header[22] = 0x75;
        header[23] = 0x30;
        assertEquals("SKIN_WRONG_SIZE", codeOf(header));
    }
}
