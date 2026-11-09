/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository.RefreshToken;
import net.quimmilho.pokednd.api.error.ApiException;

/**
 * The logins ("sessions") of an account. One session is one family of refresh tokens: each refresh swaps the token for a new
 * one of the same family, so the family id is the session id.
 */
@ApplicationScoped
public class SessionService {

    /** An active session. {@code lastUsedAt} is when its newest refresh token was made. */
    public record ActiveSession(UUID id, String userAgent, Instant createdAt, Instant lastUsedAt, Instant expiresAt) {
    }

    @Inject
    AccountRepository accounts;

    /** The sessions that can still be refreshed, the most recently used first. */
    public List<ActiveSession> activeSessions(UUID userId) {
        Instant now = Instant.now();
        Map<UUID, List<RefreshToken>> families = new LinkedHashMap<>();
        for (RefreshToken t : accounts.refreshTokensOfUser(userId)) {
            families.computeIfAbsent(t.familyId(), f -> new java.util.ArrayList<>()).add(t);
        }
        return families.entrySet().stream().map(e -> toSession(e.getKey(), e.getValue(), now)).filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(ActiveSession::lastUsedAt).reversed()).toList();
    }

    /**
     * Ends one session of this account.
     *
     * @throws ApiException 404 if the account has no such active session (a session of someone else looks the same)
     */
    public void end(UUID userId, UUID sessionId) {
        boolean found = activeSessions(userId).stream().anyMatch(s -> s.id().equals(sessionId));
        if (!found) {
            throw ApiException.notFound("This session");
        }
        accounts.revokeFamily(sessionId, Instant.now());
    }

    private static ActiveSession toSession(UUID family, List<RefreshToken> tokens, Instant now) {
        // active: the newest token of the family is not revoked and has not expired
        RefreshToken newest = tokens.stream().max(Comparator.comparing(RefreshToken::createdAt)).orElseThrow();
        if (newest.revokedAt() != null || !newest.expiresAt().isAfter(now)) {
            return null;
        }
        RefreshToken first = tokens.stream().min(Comparator.comparing(RefreshToken::createdAt)).orElseThrow();
        return new ActiveSession(family, first.userAgent(), first.createdAt(), newest.createdAt(), newest.expiresAt());
    }
}
