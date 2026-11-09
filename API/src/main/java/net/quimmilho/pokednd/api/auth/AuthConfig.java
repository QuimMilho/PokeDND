/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import io.smallrye.config.ConfigMapping;
import java.util.Optional;

/** The {@code pokednd.auth.*} settings (see application.properties). */
@ConfigMapping(prefix = "pokednd.auth")
public interface AuthConfig {

    /**
     * The RSA private key that signs the access tokens, as a PKCS#8 PEM text ({@code POKEDND_JWT_PRIVATE_KEY}). Line breaks may be
     * written as {@code \n} so the key fits on one line. Required in production.
     */
    Optional<String> jwtPrivateKey();

    /** The matching public key as an X.509 ("PUBLIC KEY") PEM text ({@code POKEDND_JWT_PUBLIC_KEY}). If left out it is derived from the private key. */
    Optional<String> jwtPublicKey();

    int accessTokenMinutes();

    int refreshTokenDays();

    /** Hours a verification link works. */
    int verifyTokenHours();

    /** Minutes a password-reset link works. */
    int resetTokenMinutes();

    /** When true, an account with a password cannot log in until its e-mail is verified (phase 29). */
    boolean requireEmailVerification();

    /** Send the refresh cookie only over HTTPS. */
    boolean cookieSecure();

    /** {@code POKEDND_BOOTSTRAP_ADMIN_EMAIL}: the first site admin, created on first start when no admin exists. */
    Optional<String> bootstrapAdminEmail();

    /** {@code POKEDND_BOOTSTRAP_ADMIN_PASSWORD}: its password (at least 10 characters; change it after logging in). */
    Optional<String> bootstrapAdminPassword();

    RateLimit rateLimit();

    interface RateLimit {
        int loginPerIpPerMinute();

        int loginFailuresPerEmail();

        int loginFailuresWindowMinutes();

        int registerPerIpPerHour();

        /** Verify, resend, forgot and reset calls from one address per hour. */
        int emailFlowsPerIpPerHour();

        /** Mails of one kind sent to one e-mail address per hour (more are skipped without telling the caller). */
        int mailsPerAddressPerHour();

        /** Linking-code attempts (typing a code) per account within the 10 minutes a code lives. */
        int linkAttemptsPerCodeLifetime();

        /** Linking codes made per account or per Minecraft account within 10 minutes. */
        int linkCodesPerCodeLifetime();
    }
}
