/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import io.quarkus.runtime.LaunchMode;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.jboss.logging.Logger;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;

/**
 * The two kinds of token (chapter 14.6): a short-lived signed <b>access token</b> (a JWT, RS256) and a random <b>refresh token</b>
 * whose hash is kept in the database. The RSA key pair comes from {@code POKEDND_JWT_PRIVATE_KEY} and {@code POKEDND_JWT_PUBLIC_KEY}.
 */
@ApplicationScoped
public class TokenService {

    static final String ISSUER = "pokednd";
    private static final Logger LOG = Logger.getLogger(TokenService.class);

    /** What an access token says about its owner. */
    public record Claims(UUID userId, String siteRole, UUID sessionId, java.time.Instant expiresAt) {
    }

    @Inject
    AuthConfig config;

    private PrivateKey privateKey;
    private JwtConsumer consumer;
    private final SecureRandom random = new SecureRandom();

    @PostConstruct
    void init() {
        PublicKey publicKey;
        String priv = config.jwtPrivateKey().orElse("").strip();
        if (priv.isEmpty()) {
            if (LaunchMode.current() == LaunchMode.NORMAL) {
                throw new IllegalStateException("POKEDND_JWT_PRIVATE_KEY is not set. Make a key pair: openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem "
                        + "and openssl rsa -in private.pem -pubout -out public.pem, then put each file in the .env as one line with the line breaks written as \\n.");
            }
            try {
                KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
                gen.initialize(2048);
                KeyPair pair = gen.generateKeyPair();
                privateKey = pair.getPrivate();
                publicKey = pair.getPublic();
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
            LOG.warn("POKEDND_JWT_PRIVATE_KEY is not set: using a new key for this run. Logins will not survive a restart.");
        } else {
            privateKey = PemKeys.privateKey(priv);
            String pub = config.jwtPublicKey().orElse("").strip();
            publicKey = pub.isEmpty() ? PemKeys.publicFrom(privateKey) : PemKeys.publicKey(pub);
            if (!PemKeys.matches(privateKey, publicKey)) {
                throw new IllegalStateException("POKEDND_JWT_PRIVATE_KEY and POKEDND_JWT_PUBLIC_KEY are not a pair");
            }
        }
        consumer = new JwtConsumerBuilder().setVerificationKey(publicKey).setRequireExpirationTime().setRequireSubject().setExpectedIssuer(ISSUER)
                .setSkipDefaultAudienceValidation()
                .setJwsAlgorithmConstraints(AlgorithmConstraints.ConstraintType.PERMIT, AlgorithmIdentifiers.RSA_USING_SHA256).build();
    }

    public int accessTokenSeconds() {
        return config.accessTokenMinutes() * 60;
    }

    /** A signed access token for this user, valid for {@code pokednd.auth.access-token-minutes}. */
    public String accessToken(UUID userId, String siteRole, UUID sessionId) {
        try {
            JwtClaims claims = new JwtClaims();
            claims.setIssuer(ISSUER);
            claims.setSubject(userId.toString());
            claims.setIssuedAtToNow();
            NumericDate exp = NumericDate.now();
            exp.addSeconds(accessTokenSeconds());
            claims.setExpirationTime(exp);
            claims.setGeneratedJwtId();
            claims.setStringClaim("role", siteRole);
            claims.setStringClaim("sid", sessionId.toString());
            JsonWebSignature jws = new JsonWebSignature();
            jws.setPayload(claims.toJson());
            jws.setKey(privateKey);
            jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
            return jws.getCompactSerialization();
        } catch (org.jose4j.lang.JoseException e) {
            throw new IllegalStateException("cannot sign the access token", e);
        }
    }

    /** The claims of a valid, unexpired access token; empty for anything else. */
    public Optional<Claims> verify(String token) {
        try {
            JwtClaims claims = consumer.processToClaims(token);
            String sid = claims.getStringClaimValue("sid");
            return Optional.of(new Claims(UUID.fromString(claims.getSubject()), claims.getStringClaimValue("role"), sid == null ? null : UUID.fromString(sid),
                    java.time.Instant.ofEpochSecond(claims.getExpirationTime().getValue())));
        } catch (InvalidJwtException | org.jose4j.jwt.MalformedClaimException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** A new random refresh token (256 bits, URL-safe text). Only {@link #hash} of it is stored. */
    public String newRefreshToken() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** A new random single-use token for an e-mail link (256 bits, URL-safe text). Only {@link #hash} of it is stored. */
    public String newEmailToken() {
        return newRefreshToken();
    }

    /** SHA-256 of a token, as 64 hex characters. */
    public static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
