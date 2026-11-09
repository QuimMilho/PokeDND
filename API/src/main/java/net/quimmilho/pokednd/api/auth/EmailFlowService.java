/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository.EmailToken;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository.User;
import net.quimmilho.pokednd.api.error.ApiException;

/**
 * E-mail verification and password reset (chapter 14.6). Each mail holds a random token that works once and expires; only the
 * hash of the token is stored. Asking for a reset never tells whether the address has an account.
 */
@ApplicationScoped
public class EmailFlowService {

    @Inject
    AccountRepository accounts;

    @Inject
    TokenService tokens;

    @Inject
    PasswordHasher hasher;

    @Inject
    MailService mail;

    @Inject
    RateLimiter limiter;

    @Inject
    AuthConfig config;

    @Inject
    SiteConfig site;

    // ------------------------------------------------------------------ verification

    /** Mails a new verification link to the account, unless it is verified already or too many mails went to this address. */
    public void sendVerification(User user) {
        if (user.emailVerified() || !mayMail("verify", user.email())) {
            return;
        }
        String token = issue(user, AccountRepository.TOKEN_VERIFY, Duration.ofHours(config.verifyTokenHours()));
        mail.send(user.email(), user.locale(), "verify", Map.of("NAME", user.displayName(),
                "LINK", site.baseUrl() + "/verify-email?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8),
                "HOURS", String.valueOf(config.verifyTokenHours())));
    }

    /** {@code POST /auth/verify-email}: the link of the mail was opened. */
    @Transactional
    public void verifyEmail(String token, String clientIp) {
        hitIp(clientIp);
        EmailToken t = consume(token, AccountRepository.TOKEN_VERIFY);
        accounts.markEmailVerified(t.userId());
    }

    /** {@code POST /auth/resend-verification}: always looks the same to the caller, whether or not a mail was sent. */
    public void resendVerification(String email, String clientIp) {
        hitIp(clientIp);
        accounts.findUserByEmail(AuthService.normalizeEmail(email)).ifPresent(this::sendVerification);
    }

    // ------------------------------------------------------------------ password reset

    /** {@code POST /auth/forgot}: mails a reset link if the address has an account. Always looks the same to the caller. */
    public void forgotPassword(String email, String clientIp) {
        hitIp(clientIp);
        Optional<User> user = accounts.findUserByEmail(AuthService.normalizeEmail(email));
        if (user.isEmpty() || !mayMail("reset", user.get().email())) {
            return;
        }
        String token = issue(user.get(), AccountRepository.TOKEN_RESET, Duration.ofMinutes(config.resetTokenMinutes()));
        mail.send(user.get().email(), user.get().locale(), "reset", Map.of("NAME", user.get().displayName(),
                "LINK", site.baseUrl() + "/reset-password?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8),
                "MINUTES", String.valueOf(config.resetTokenMinutes())));
    }

    /**
     * {@code POST /auth/reset}: sets the new password, ends every login of the account (so whoever knew the old password or held
     * a session is out) and counts the e-mail as verified, since the person read the mail.
     */
    @Transactional
    public void resetPassword(String token, String newPassword, String clientIp) {
        hitIp(clientIp);
        AuthService.checkPassword(newPassword); // before the token is used up, so a weak password does not waste the link
        EmailToken t = consume(token, AccountRepository.TOKEN_RESET);
        Instant now = Instant.now();
        accounts.setPasswordHash(t.userId(), hasher.hash(newPassword));
        accounts.markEmailVerified(t.userId());
        accounts.invalidateEmailTokens(t.userId(), AccountRepository.TOKEN_RESET, now);
        accounts.revokeAllRefreshTokens(t.userId(), now);
    }

    // ------------------------------------------------------------------ helpers

    /** Makes a token for the account (older unused ones of the same kind stop working) and returns the text for the link. */
    private String issue(User user, String purpose, Duration lifetime) {
        Instant now = Instant.now();
        accounts.invalidateEmailTokens(user.id(), purpose, now);
        String token = tokens.newEmailToken();
        accounts.saveEmailToken(new EmailToken(Ids.newId(), user.id(), purpose, TokenService.hash(token), now.plus(lifetime), null));
        return token;
    }

    /** Finds the token, checks it and uses it up. */
    private EmailToken consume(String token, String purpose) {
        if (token == null || token.isBlank()) {
            throw ApiException.badRequest("INVALID_TOKEN", "The link is not valid.");
        }
        EmailToken t = accounts.findEmailToken(TokenService.hash(token.strip()))
                .filter(found -> found.purpose().equals(purpose) && found.usedAt() == null)
                .orElseThrow(() -> ApiException.badRequest("INVALID_TOKEN", "The link is not valid, or it was used already."));
        Instant now = Instant.now();
        if (!t.expiresAt().isAfter(now)) {
            throw ApiException.badRequest("TOKEN_EXPIRED", "The link has expired. Ask for a new one.");
        }
        if (!accounts.useEmailToken(t.id(), now)) {
            throw ApiException.badRequest("INVALID_TOKEN", "The link is not valid, or it was used already.");
        }
        return t;
    }

    private void hitIp(String clientIp) {
        limiter.hit("email-flow-ip", clientIp, config.rateLimit().emailFlowsPerIpPerHour(), Duration.ofHours(1));
    }

    /** True if one more mail of this kind may go to the address within the hour. Too many are skipped silently. */
    private boolean mayMail(String kind, String address) {
        try {
            limiter.hit("mail-" + kind, address.toLowerCase(java.util.Locale.ROOT), config.rateLimit().mailsPerAddressPerHour(), Duration.ofHours(1));
            return true;
        } catch (ApiException tooMany) {
            return false;
        }
    }
}
