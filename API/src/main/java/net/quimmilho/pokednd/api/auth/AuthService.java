/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository.EmailTakenException;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository.RefreshToken;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository.User;
import net.quimmilho.pokednd.api.error.ApiException;
import org.jboss.logging.Logger;

/** Sign-up, login, refresh and logout with e-mail and password (chapters 14.6 and 16.4). */
@ApplicationScoped
public class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Set<String> LOCALES = Set.of("en", "pt");
    static final int MIN_PASSWORD = 10;
    static final int MAX_PASSWORD = 128;

    /** A logged-in session: the access token for the response body and the refresh token for the cookie. */
    public record Session(User user, String accessToken, int expiresInSeconds, String refreshToken, Instant refreshExpiresAt) {
    }

    @Inject
    AccountRepository accounts;

    @Inject
    PasswordHasher hasher;

    @Inject
    TokenService tokens;

    @Inject
    AuthConfig config;

    @Inject
    RateLimiter limiter;

    @Inject
    EmailFlowService emailFlows;

    // ------------------------------------------------------------------ sign-up

    public User register(String email, String password, String displayName, String locale, String clientIp) {
        limiter.hit("register-ip", clientIp, config.rateLimit().registerPerIpPerHour(), Duration.ofHours(1));
        String normalized = normalizeEmail(email);
        checkPassword(password);
        String name = displayName == null || displayName.isBlank() ? defaultName(normalized) : displayName.strip();
        if (name.length() > 40) {
            throw ApiException.badRequest("INVALID_NAME", "The display name can have at most 40 characters.");
        }
        String loc = locale == null || locale.isBlank() ? "en" : locale.strip().toLowerCase(Locale.ROOT);
        if (!LOCALES.contains(loc)) {
            throw ApiException.badRequest("INVALID_LOCALE", "The language must be en or pt.");
        }
        User user = new User(Ids.newId(), normalized, false, hasher.hash(password), name, loc, "system",
                AccountRepository.SITE_ROLE_NONE, Instant.now(), null);
        User created;
        try {
            created = accounts.createUser(user);
        } catch (EmailTakenException e) {
            throw new ApiException(409, "EMAIL_TAKEN", "There is already an account with this e-mail address.");
        }
        emailFlows.sendVerification(created);
        return created;
    }

    // ------------------------------------------------------------------ login

    public Session login(String email, String password, String clientIp, String userAgent) {
        RateLimit rl = new RateLimit(config);
        limiter.hit("login-ip", clientIp, rl.perIpPerMinute, Duration.ofMinutes(1));
        String normalized = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        limiter.check("login-fail", normalized, rl.failuresPerEmail, rl.failureWindow);

        Optional<User> found = normalized.isEmpty() ? Optional.empty() : accounts.findUserByEmail(normalized);
        boolean ok = password != null && (found.isPresent() ? hasher.verify(found.get().passwordHash(), password) : hasher.verifyDummy(password));
        if (!ok) {
            limiter.record("login-fail", normalized, rl.failureWindow);
            throw new ApiException(401, "INVALID_CREDENTIALS", "The e-mail address or the password is wrong.");
        }
        User user = found.orElseThrow();
        if (config.requireEmailVerification() && !user.emailVerified()) {
            throw new ApiException(403, "EMAIL_NOT_VERIFIED", "Verify your e-mail address first.");
        }
        limiter.clear("login-fail", normalized);
        return startSession(user, Ids.newId(), userAgent);
    }

    // ------------------------------------------------------------------ refresh and logout

    /**
     * Swaps a refresh token for a new one (and a new access token). Each refresh token works once: showing a used one again
     * means it was stolen or copied, so every token of that login is revoked.
     */
    public Session refresh(String refreshToken, String userAgent) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(401, "INVALID_REFRESH_TOKEN", "There is no refresh token. Log in again.");
        }
        Instant now = Instant.now();
        RefreshToken stored = accounts.findRefreshToken(TokenService.hash(refreshToken))
                .orElseThrow(() -> new ApiException(401, "INVALID_REFRESH_TOKEN", "The refresh token is not valid. Log in again."));
        if (stored.revokedAt() != null) {
            accounts.revokeFamily(stored.familyId(), now);
            LOG.warnf("A used refresh token was presented again; the whole login (family %s) was revoked.", stored.familyId());
            throw new ApiException(401, "REFRESH_TOKEN_REUSED", "This login was closed because an old token was used again. Log in again.");
        }
        if (!stored.expiresAt().isAfter(now)) {
            accounts.revokeFamily(stored.familyId(), now);
            throw new ApiException(401, "REFRESH_TOKEN_EXPIRED", "The login has expired. Log in again.");
        }
        User user = accounts.findUser(stored.userId())
                .orElseThrow(() -> new ApiException(401, "INVALID_REFRESH_TOKEN", "The account does not exist any more."));
        if (!accounts.revokeRefreshToken(stored.id(), now)) {
            // someone used the same token a moment ago
            accounts.revokeFamily(stored.familyId(), now);
            throw new ApiException(401, "REFRESH_TOKEN_REUSED", "This login was closed because an old token was used again. Log in again.");
        }
        return startSession(user, stored.familyId(), userAgent);
    }

    /** Revokes the refresh token (if it is one). Never fails: logging out twice is fine. */
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        accounts.findRefreshToken(TokenService.hash(refreshToken)).ifPresent(t -> accounts.revokeFamily(t.familyId(), Instant.now()));
    }

    // ------------------------------------------------------------------ the first site admin

    /**
     * Creates the first site admin from {@code POKEDND_BOOTSTRAP_ADMIN_EMAIL} and {@code POKEDND_BOOTSTRAP_ADMIN_PASSWORD} when the
     * site has no admin yet (chapter 13.5). An existing account with that address is promoted instead. Does nothing when an admin
     * exists, when the settings are missing, or when the password is still the example value.
     */
    public void bootstrapAdmin() {
        String email = config.bootstrapAdminEmail().map(e -> e.strip().toLowerCase(Locale.ROOT)).orElse("");
        String password = config.bootstrapAdminPassword().orElse("");
        if (email.isEmpty() || accounts.countSiteAdmins() > 0) {
            return;
        }
        Optional<User> existing = accounts.findUserByEmail(email);
        if (existing.isPresent()) {
            accounts.setSiteRole(existing.get().id(), AccountRepository.SITE_ROLE_ADMIN);
            LOG.infof("%s is now site admin.", email);
            return;
        }
        if (password.equals("CHANGE_ME") || password.length() < MIN_PASSWORD) {
            LOG.warnf("No site admin exists, but POKEDND_BOOTSTRAP_ADMIN_PASSWORD is missing, still CHANGE_ME or shorter than %d characters, so %s was not created.",
                    MIN_PASSWORD, email);
            return;
        }
        accounts.createUser(new User(Ids.newId(), email, true, hasher.hash(password), "Admin", "en", "system", AccountRepository.SITE_ROLE_ADMIN,
                Instant.now(), null));
        LOG.infof("The first site admin %s was created. Change the password after logging in.", email);
    }

    // ------------------------------------------------------------------ helpers

    /** Refuses a password that is too short or too long. */
    static void checkPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD) {
            throw ApiException.badRequest("WEAK_PASSWORD", "The password must have " + MIN_PASSWORD + " to " + MAX_PASSWORD + " characters.");
        }
    }

    /** Starts a new login (a new session) for an account that has just proved who it is, for example with Discord. */
    public Session loginAs(User user, String userAgent) {
        return startSession(user, Ids.newId(), userAgent);
    }

    private Session startSession(User user, java.util.UUID family, String userAgent) {
        String refresh = tokens.newRefreshToken();
        Instant expires = Instant.now().plus(config.refreshTokenDays(), ChronoUnit.DAYS);
        accounts.saveRefreshToken(new RefreshToken(Ids.newId(), user.id(), family, TokenService.hash(refresh), expires, null,
                userAgent == null ? null : userAgent.substring(0, Math.min(255, userAgent.length())), Instant.now()));
        return new Session(user, tokens.accessToken(user.id(), user.siteRole(), family), tokens.accessTokenSeconds(), refresh, expires);
    }

    /** Without a name the part of the address before the @ is used, cut to the length a name may have. */
    private static String defaultName(String normalizedEmail) {
        String local = normalizedEmail.substring(0, normalizedEmail.indexOf('@'));
        return local.substring(0, Math.min(40, local.length()));
    }

    static String normalizeEmail(String email) {
        String e = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        if (e.length() > 254 || !EMAIL.matcher(e).matches()) {
            throw ApiException.badRequest("INVALID_EMAIL", "This does not look like an e-mail address.");
        }
        return e;
    }

    /** The rate-limit numbers of the configuration, read once per call. */
    private static final class RateLimit {
        final int perIpPerMinute;
        final int failuresPerEmail;
        final Duration failureWindow;

        RateLimit(AuthConfig config) {
            perIpPerMinute = config.rateLimit().loginPerIpPerMinute();
            failuresPerEmail = config.rateLimit().loginFailuresPerEmail();
            failureWindow = Duration.ofMinutes(config.rateLimit().loginFailuresWindowMinutes());
        }
    }
}
