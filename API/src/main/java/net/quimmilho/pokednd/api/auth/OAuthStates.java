/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import jakarta.enterprise.context.ApplicationScoped;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The random {@code state} values of the OAuth redirects that are under way. Each works once and for ten minutes. It lives in
 * one API process, like the rate limiter (a single-server installation).
 */
@ApplicationScoped
public class OAuthStates {

    static final Duration LIFETIME = Duration.ofMinutes(10);

    /** What a state remembers: whether it is a login or the linking of a signed-in account ({@code userId}). */
    public record Pending(String provider, UUID userId, Instant expiresAt) {
        public boolean linking() {
            return userId != null;
        }
    }

    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /** Makes a state for a login ({@code userId} null) or for linking this account. */
    public String create(String provider, UUID userId) {
        Instant now = Instant.now();
        pending.values().removeIf(p -> !p.expiresAt().isAfter(now));
        byte[] b = new byte[32];
        random.nextBytes(b);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        pending.put(state, new Pending(provider, userId, now.plus(LIFETIME)));
        return state;
    }

    /** Uses the state up. Empty if it is unknown, used already, or expired. */
    public Optional<Pending> take(String state) {
        if (state == null) {
            return Optional.empty();
        }
        Pending p = pending.remove(state);
        return p == null || !p.expiresAt().isAfter(Instant.now()) ? Optional.empty() : Optional.of(p);
    }
}
