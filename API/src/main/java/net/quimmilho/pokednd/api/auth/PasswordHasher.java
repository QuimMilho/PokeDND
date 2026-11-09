/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/**
 * Passwords are stored only as Argon2id hashes (chapter 14.6), in the standard text form
 * {@code $argon2id$v=19$m=19456,t=2,p=1$<salt>$<hash>}.
 *
 * <p>The cost (19 MiB, 2 passes, 1 lane) is the minimum the OWASP password storage guide recommends for Argon2id.
 * <b>Suggested</b>; the cost is read back from each stored hash, so it can be raised later without locking anyone out.
 */
@ApplicationScoped
public class PasswordHasher {

    static final int MEMORY_KIB = 19456;
    static final int ITERATIONS = 2;
    static final int PARALLELISM = 1;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;

    private static final Base64.Encoder B64 = Base64.getEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getDecoder();

    private final SecureRandom random = new SecureRandom();
    private final String dummy;

    public PasswordHasher() {
        // Checked when the e-mail is unknown, so that "no such account" takes as long as "wrong password".
        this.dummy = hash("dummy password for timing");
    }

    public String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] out = argon2(password, salt, MEMORY_KIB, ITERATIONS, PARALLELISM, HASH_BYTES);
        return "$argon2id$v=19$m=" + MEMORY_KIB + ",t=" + ITERATIONS + ",p=" + PARALLELISM + "$" + B64.encodeToString(salt) + "$"
                + B64.encodeToString(out);
    }

    public boolean verify(String stored, String password) {
        if (stored == null) {
            return false;
        }
        try {
            String[] parts = stored.split("\\$");
            // "", "argon2id", "v=19", "m=..,t=..,p=..", salt, hash
            if (parts.length != 6 || !parts[1].equals("argon2id")) {
                return false;
            }
            int m = 0;
            int t = 0;
            int p = 0;
            for (String kv : parts[3].split(",")) {
                String[] x = kv.split("=");
                switch (x[0]) {
                    case "m" -> m = Integer.parseInt(x[1]);
                    case "t" -> t = Integer.parseInt(x[1]);
                    case "p" -> p = Integer.parseInt(x[1]);
                    default -> {
                    }
                }
            }
            byte[] salt = B64D.decode(parts[4]);
            byte[] expected = B64D.decode(parts[5]);
            byte[] actual = argon2(password, salt, m, t, p, expected.length);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Spends the time of a check without an account; always false. */
    public boolean verifyDummy(String password) {
        verify(dummy, password);
        return false;
    }

    private static byte[] argon2(String password, byte[] salt, int memoryKib, int iterations, int parallelism, int length) {
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt).withMemoryAsKB(memoryKib).withIterations(iterations).withParallelism(parallelism).build();
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);
        byte[] out = new byte[length];
        generator.generateBytes(password.getBytes(StandardCharsets.UTF_8), out);
        return out;
    }
}
