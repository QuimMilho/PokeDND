/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Reads RSA keys from the PEM text of the environment variables. */
final class PemKeys {

    private PemKeys() {
    }

    /** A PKCS#8 private key ({@code -----BEGIN PRIVATE KEY-----}), as made by {@code openssl genpkey}. */
    static PrivateKey privateKey(String pem) {
        String text = clean(pem);
        if (text.contains("BEGIN RSA PRIVATE KEY")) {
            throw new IllegalArgumentException("POKEDND_JWT_PRIVATE_KEY is in the old PKCS#1 format. Convert it: openssl pkcs8 -topk8 -nocrypt -in old.pem -out private.pem");
        }
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der(text, "PRIVATE KEY")));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("POKEDND_JWT_PRIVATE_KEY is not a valid RSA private key in PKCS#8 PEM form (-----BEGIN PRIVATE KEY-----)", e);
        }
    }

    /** An X.509 public key ({@code -----BEGIN PUBLIC KEY-----}). */
    static PublicKey publicKey(String pem) {
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der(clean(pem), "PUBLIC KEY")));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("POKEDND_JWT_PUBLIC_KEY is not a valid RSA public key in PEM form (-----BEGIN PUBLIC KEY-----)", e);
        }
    }

    /** The public half of a private key. */
    static PublicKey publicFrom(PrivateKey key) {
        if (!(key instanceof RSAPrivateCrtKey crt)) {
            throw new IllegalArgumentException("The private key has no public exponent; set POKEDND_JWT_PUBLIC_KEY too");
        }
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /** True if both keys are the two halves of one pair. */
    static boolean matches(PrivateKey priv, PublicKey pub) {
        return priv instanceof RSAPrivateCrtKey crt && pub instanceof RSAPublicKey rsa && crt.getModulus().equals(rsa.getModulus())
                && crt.getPublicExponent().equals(rsa.getPublicExponent());
    }

    /** The PEM text of a key, for tests and for the ephemeral development key. */
    static String pem(String label, byte[] der) {
        String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
    }

    private static String clean(String pem) {
        return pem.strip().replace("\\n", "\n").replace("\r", "");
    }

    private static byte[] der(String pem, String label) {
        String body = pem.replace("-----BEGIN " + label + "-----", "").replace("-----END " + label + "-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}
