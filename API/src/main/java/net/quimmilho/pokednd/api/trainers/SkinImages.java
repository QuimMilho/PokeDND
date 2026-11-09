/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.trainers;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import net.quimmilho.pokednd.api.error.ApiException;

/**
 * Checks and cleans a skin (chapter 3.6): it must be a PNG of exactly 64×64 pixels and not too big. The picture is read and written
 * again, so whatever else was in the file (text chunks, EXIF data, colour profiles, trailing bytes, extra animation frames) is gone and
 * only the pixels stay.
 */
public final class SkinImages {

    public static final int SIZE = 64;
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    /** A skin that passed the checks: the clean PNG and its SHA-256. */
    public record Clean(byte[] png, String sha256) {
    }

    private SkinImages() {
    }

    /**
     * @param input    the bytes of the file
     * @param maxBytes the largest file accepted
     * @throws ApiException 422 with {@code SKIN_EMPTY}, {@code SKIN_TOO_LARGE}, {@code SKIN_NOT_PNG}, {@code SKIN_WRONG_SIZE} or {@code SKIN_UNREADABLE}
     */
    public static Clean clean(byte[] input, int maxBytes) {
        if (input == null || input.length == 0) {
            throw skin("SKIN_EMPTY", "No skin file was sent.");
        }
        if (input.length > maxBytes) {
            throw skin("SKIN_TOO_LARGE", "The skin is " + input.length + " bytes, but the limit is " + maxBytes + " bytes.");
        }
        if (input.length < 33 || !startsWith(input, SIGNATURE) || input[12] != 'I' || input[13] != 'H' || input[14] != 'D' || input[15] != 'R') {
            throw skin("SKIN_NOT_PNG", "The skin must be a PNG picture.");
        }
        // the size is read from the header before anything is decoded, so a huge picture cannot use up the memory
        int width = readInt(input, 16);
        int height = readInt(input, 20);
        if (width != SIZE || height != SIZE) {
            throw skin("SKIN_WRONG_SIZE", "The skin must be " + SIZE + "×" + SIZE + " pixels, not " + width + "×" + height + ".");
        }
        BufferedImage decoded;
        try {
            ImageIO.setUseCache(false);
            decoded = ImageIO.read(new ByteArrayInputStream(input));
        } catch (IOException | RuntimeException e) {
            throw skin("SKIN_UNREADABLE", "The skin could not be read as a PNG picture.");
        }
        if (decoded == null || decoded.getWidth() != SIZE || decoded.getHeight() != SIZE) {
            throw skin("SKIN_UNREADABLE", "The skin could not be read as a PNG picture.");
        }
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        var g = out.createGraphics();
        try {
            g.drawImage(decoded, 0, 0, null);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(out, "png", bytes)) {
                throw skin("SKIN_UNREADABLE", "The skin could not be saved as a PNG picture.");
            }
        } catch (IOException e) {
            throw skin("SKIN_UNREADABLE", "The skin could not be saved as a PNG picture.");
        }
        byte[] png = bytes.toByteArray();
        return new Clean(png, sha256(png));
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static int readInt(byte[] b, int at) {
        return ((b[at] & 0xFF) << 24) | ((b[at + 1] & 0xFF) << 16) | ((b[at + 2] & 0xFF) << 8) | (b[at + 3] & 0xFF);
    }

    private static ApiException skin(String code, String message) {
        return new ApiException(422, code, message);
    }
}
