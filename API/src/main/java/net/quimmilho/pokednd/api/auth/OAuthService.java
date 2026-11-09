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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository.EmailTakenException;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository.Identity;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository.User;
import net.quimmilho.pokednd.api.error.ApiException;
import org.jboss.logging.Logger;

/**
 * Sign-in with Discord, and linking Discord to an account that is signed in (chapters 3.2, 14.6 and 16.4).
 *
 * <p>Rules (decided): a Discord account that is already linked signs in to its account. A new Discord account needs an e-mail
 * address that Discord has verified. If an account with that address exists, Discord is linked to it automatically; when that
 * account never verified its address, its password is removed and its logins are ended, because whoever signed up with
 * somebody else's address must not keep access. Otherwise a new account is made, already verified, without a password.
 */
@ApplicationScoped
public class OAuthService {

    private static final Logger LOG = Logger.getLogger(OAuthService.class);
    public static final String DISCORD = AccountRepository.PROVIDER_DISCORD;

    /** The page the browser is sent to, and the new session when the login worked. */
    public record Outcome(String redirectTo, AuthService.Session session) {
    }

    /** A started redirect: where to send the browser and the state value that must come back. */
    public record Start(String url, String state) {
    }

    @Inject
    AccountRepository accounts;

    @Inject
    AuthService auth;

    @Inject
    DiscordClient discord;

    @Inject
    DiscordConfig discordConfig;

    @Inject
    OAuthStates states;

    @Inject
    SiteConfig site;

    @Inject
    AuthConfig config;

    @Inject
    RateLimiter limiter;

    // ------------------------------------------------------------------ the redirect there

    public boolean enabled(String provider) {
        return DISCORD.equals(provider) && discordConfig.enabled();
    }

    public List<String> providers() {
        return discordConfig.enabled() ? List.of(DISCORD) : List.of();
    }

    /** Starts a login ({@code linkUserId} null) or the linking of a signed-in account. */
    public Start start(String provider, UUID linkUserId, String clientIp) {
        requireEnabled(provider);
        limiter.hit("oauth-ip", clientIp, config.rateLimit().loginPerIpPerMinute(), Duration.ofMinutes(1));
        String state = states.create(provider, linkUserId);
        return new Start(discord.authorizeUrl(state, redirectUri(provider)), state);
    }

    // ------------------------------------------------------------------ the redirect back

    /**
     * Handles the redirect back from Discord. It never throws for a problem of the person or of Discord: the browser is sent to the
     * app with a short error code instead ({@code INVALID_STATE}, {@code OAUTH_DENIED}, {@code OAUTH_FAILED},
     * {@code DISCORD_EMAIL_UNVERIFIED}, {@code DISCORD_ALREADY_LINKED}).
     *
     * @param stateFromCookie the state value that the browser kept in its cookie when the redirect started
     */
    public Outcome callback(String provider, String code, String state, String error, String stateFromCookie, String userAgent, String clientIp) {
        requireEnabled(provider);
        limiter.hit("oauth-ip", clientIp, config.rateLimit().loginPerIpPerMinute(), Duration.ofMinutes(1));
        // the state must come back unchanged in the address and in the cookie of the same browser, and works once
        Optional<OAuthStates.Pending> pending = state != null && state.equals(stateFromCookie) ? states.take(state) : Optional.empty();
        if (pending.isEmpty() || !DISCORD.equals(pending.get().provider())) {
            return failed(false, "INVALID_STATE");
        }
        boolean linking = pending.get().linking();
        if (error != null) {
            return failed(linking, "access_denied".equals(error) ? "OAUTH_DENIED" : "OAUTH_FAILED");
        }
        if (code == null || code.isBlank()) {
            return failed(linking, "OAUTH_FAILED");
        }
        DiscordClient.Profile profile;
        try {
            profile = discord.profile(code, redirectUri(provider));
        } catch (DiscordClient.DiscordException e) {
            LOG.warnf("The Discord sign-in failed: %s", e.getMessage());
            return failed(linking, "OAUTH_FAILED");
        }
        try {
            if (linking) {
                link(pending.get().userId(), profile);
                return new Outcome(site.baseUrl() + "/account?oauth=linked", null);
            }
            return new Outcome(site.baseUrl() + "/login?oauth=ok", signIn(profile, userAgent));
        } catch (ApiException e) {
            return failed(linking, e.code());
        }
    }

    // ------------------------------------------------------------------ sign in

    @Transactional
    AuthService.Session signIn(DiscordClient.Profile profile, String userAgent) {
        Optional<Identity> existing = accounts.findIdentity(DISCORD, profile.id());
        if (existing.isPresent()) {
            User user = accounts.findUser(existing.get().userId()).orElseThrow(() -> new ApiException(401, "OAUTH_FAILED", "The account does not exist any more."));
            return auth.loginAs(user, userAgent);
        }
        if (profile.email() == null || !profile.verified()) {
            throw new ApiException(403, "DISCORD_EMAIL_UNVERIFIED", "Discord has no verified e-mail address for this account.");
        }
        String email = AuthService.normalizeEmail(profile.email());
        Instant now = Instant.now();
        Identity identity = new Identity(Ids.newId(), null, DISCORD, profile.id(), now);
        Optional<User> sameEmail = accounts.findUserByEmail(email);
        User user;
        if (sameEmail.isPresent()) {
            user = sameEmail.get();
            if (!user.emailVerified()) {
                // somebody may have signed up with this address without owning it: remove their way in
                accounts.setPasswordHash(user.id(), null);
                accounts.revokeAllRefreshTokens(user.id(), now);
                accounts.invalidateEmailTokens(user.id(), AccountRepository.TOKEN_RESET, now);
                accounts.markEmailVerified(user.id());
                LOG.infof("Discord was linked to account %s whose e-mail was not verified; its password was removed.", user.id());
            }
            accounts.addIdentity(new Identity(identity.id(), user.id(), DISCORD, profile.id(), now));
            user = accounts.findUser(user.id()).orElseThrow();
        } else {
            UUID id = Ids.newId();
            User fresh = new User(id, email, true, null, displayName(profile), locale(profile), "system", AccountRepository.SITE_ROLE_NONE, now, null);
            try {
                user = accounts.createOAuthUser(fresh, new Identity(identity.id(), id, DISCORD, profile.id(), now));
            } catch (EmailTakenException e) {
                throw new ApiException(409, "OAUTH_FAILED", "An account with this address was made a moment ago.");
            }
        }
        return auth.loginAs(user, userAgent);
    }

    // ------------------------------------------------------------------ link and unlink

    @Transactional
    void link(UUID userId, DiscordClient.Profile profile) {
        Optional<Identity> existing = accounts.findIdentity(DISCORD, profile.id());
        if (existing.isPresent()) {
            if (existing.get().userId().equals(userId)) {
                return;
            }
            throw new ApiException(409, "DISCORD_ALREADY_LINKED", "This Discord account belongs to another account.");
        }
        if (accounts.identitiesOfUser(userId).stream().anyMatch(i -> DISCORD.equals(i.provider()))) {
            throw new ApiException(409, "DISCORD_ALREADY_LINKED", "Another Discord account is linked already. Unlink it first.");
        }
        accounts.addIdentity(new Identity(Ids.newId(), userId, DISCORD, profile.id(), Instant.now()));
    }

    /** A way to log in: {@code password} or {@code discord}. */
    public record LoginMethod(String provider, Instant since) {
    }

    /** The ways this account can log in: the password (if it has one) and the linked providers. */
    public List<LoginMethod> loginMethods(UUID userId) {
        User user = accounts.findUser(userId).orElseThrow(ApiException::unauthenticated);
        List<LoginMethod> methods = new ArrayList<>();
        if (user.passwordHash() != null) {
            methods.add(new LoginMethod(AccountRepository.PROVIDER_PASSWORD, user.createdAt()));
        }
        accounts.identitiesOfUser(userId).stream().filter(i -> !AccountRepository.PROVIDER_PASSWORD.equals(i.provider()))
                .forEach(i -> methods.add(new LoginMethod(i.provider(), i.createdAt())));
        return methods;
    }

    /**
     * Removes a linked provider.
     *
     * @throws ApiException 409 LAST_LOGIN_METHOD if it is the only way left to log in
     */
    @Transactional
    public void unlink(UUID userId, String provider) {
        if (!DISCORD.equals(provider)) {
            throw ApiException.badRequest("UNKNOWN_PROVIDER", "Only Discord can be unlinked.");
        }
        List<LoginMethod> methods = loginMethods(userId);
        if (methods.stream().noneMatch(m -> m.provider().equals(provider))) {
            throw ApiException.notFound("This Discord link");
        }
        if (methods.size() <= 1) {
            throw new ApiException(409, "LAST_LOGIN_METHOD", "This is the only way you can log in. Set a password first (use \"forgot password\"), then unlink Discord.");
        }
        accounts.removeIdentity(userId, provider);
    }

    // ------------------------------------------------------------------ helpers

    private void requireEnabled(String provider) {
        if (!enabled(provider)) {
            throw new ApiException(404, "OAUTH_DISABLED", "Sign-in with " + provider + " is not available.");
        }
    }

    private String redirectUri(String provider) {
        return site.baseUrl() + "/api/v1/auth/oauth/" + provider + "/callback";
    }

    private Outcome failed(boolean linking, String code) {
        return new Outcome(site.baseUrl() + (linking ? "/account" : "/login") + "?oauth=error&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8), null);
    }

    /** The Discord display name, else the user name, cut to the length a name may have. */
    private static String displayName(DiscordClient.Profile p) {
        String name = p.globalName() != null ? p.globalName() : p.username();
        name = name == null ? "Discord user" : name.strip();
        return name.substring(0, Math.min(40, name.length()));
    }

    /** Discord sends a locale such as {@code pt-BR}; the app has English and Portuguese. */
    private static String locale(DiscordClient.Profile p) {
        return p.locale() != null && p.locale().toLowerCase(Locale.ROOT).startsWith("pt") ? "pt" : "en";
    }
}
