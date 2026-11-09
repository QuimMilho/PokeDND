/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.trainers;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

/** Makes small PNG files for the skin tests. */
public final class Pngs {

    private Pngs() {
    }

    /** A PNG of this size, with a recognisable pattern so that two skins differ. */
    public static byte[] png(int width, int height, int seed) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, 0xFF000000 | ((x * 4 + seed) & 0xFF) << 16 | ((y * 4) & 0xFF) << 8 | (seed & 0xFF));
            }
        }
        // a transparent corner, as in a real skin
        image.setRGB(0, 0, 0);
        return write(image);
    }

    /** A normal skin: 64×64. */
    public static byte[] skin(int seed) {
        return png(64, 64, seed);
    }

    private static byte[] write(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The same PNG with a text chunk (metadata) after the header and some bytes of garbage at the end. */
    public static byte[] withMetadata(byte[] png, String secret) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int afterHeader = 8 + 4 + 4 + 13 + 4; // signature, length, "IHDR", 13 bytes of data, CRC
        out.write(png, 0, afterHeader);
        byte[] text = ("Comment\0" + secret).getBytes(StandardCharsets.ISO_8859_1);
        out.writeBytes(chunk("tEXt", text));
        out.write(png, afterHeader, png.length - afterHeader);
        out.writeBytes("TRAILING-GARBAGE".getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }

    private static byte[] chunk(String type, byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(int32(data.length));
        byte[] t = type.getBytes(StandardCharsets.US_ASCII);
        out.writeBytes(t);
        out.writeBytes(data);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        out.writeBytes(int32((int) crc.getValue()));
        return out.toByteArray();
    }

    private static byte[] int32(int v) {
        return new byte[] {(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v};
    }
}
