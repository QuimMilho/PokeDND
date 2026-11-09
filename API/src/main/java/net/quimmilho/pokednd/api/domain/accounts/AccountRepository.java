/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.domain.accounts;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Where accounts and refresh tokens are kept (chapter 17.2). The SQL adapter implements it; a MongoDB one follows in phase 34. */
public interface AccountRepository {

    /** The two site roles of {@code User.site_role}. */
    String SITE_ROLE_NONE = "none";
    String SITE_ROLE_ADMIN = "siteAdmin";

    /**
     * An account.
     *
     * @param passwordHash null for accounts that only sign in with an outside provider
     * @param email        stored as written, compared without regard to capitals
     */
    record User(UUID id, String email, boolean emailVerified, String passwordHash, String displayName, String locale, String theme,
                String siteRole, Instant createdAt, Instant deletedAt) {

        public boolean siteAdmin() {
            return SITE_ROLE_ADMIN.equals(siteRole);
        }
    }

    /** A refresh token (only its hash is stored). Tokens swapped for each other share a {@code familyId}. */
    record RefreshToken(UUID id, UUID userId, UUID familyId, String tokenHash, Instant expiresAt, Instant revokedAt, String userAgent,
                        Instant createdAt) {
    }

    /** A way to log in that is tied to an outside provider ({@code discord}); {@code password} rows also exist but only the hash counts. */
    record Identity(UUID id, UUID userId, String provider, String providerUserId, Instant createdAt) {
    }

    /** A single-use token sent by e-mail. {@code purpose} is {@link #TOKEN_VERIFY} or {@link #TOKEN_RESET}. */
    record EmailToken(UUID id, UUID userId, String purpose, String tokenHash, Instant expiresAt, Instant usedAt) {
    }

    String TOKEN_VERIFY = "verify";
    String TOKEN_RESET = "reset";
    String PROVIDER_PASSWORD = "password";
    String PROVIDER_DISCORD = "discord";

    /** Raised when the e-mail address already belongs to a live account. */
    class EmailTakenException extends RuntimeException {
        public EmailTakenException(String email) {
            super("The e-mail address is already in use: " + email);
        }
    }

    Optional<User> findUser(UUID id);

    /** A live (not deleted) account by e-mail, ignoring capitals. */
    Optional<User> findUserByEmail(String email);

    /**
     * Creates an account and its {@code password} identity.
     *
     * @throws EmailTakenException if a live account has this address
     */
    User createUser(User user);

    /**
     * Creates an account that signs in with an outside provider: no password, and only this identity.
     *
     * @throws EmailTakenException if a live account has this address
     */
    User createOAuthUser(User user, Identity identity);

    Optional<Identity> findIdentity(String provider, String providerUserId);

    List<Identity> identitiesOfUser(UUID userId);

    void addIdentity(Identity identity);

    /** Removes the user's identity of this provider. Returns false if there was none. */
    boolean removeIdentity(UUID userId, String provider);

    void markEmailVerified(UUID userId);

    /** Sets the password hash; null removes the password. */
    void setPasswordHash(UUID userId, String passwordHash);

    void setSiteRole(UUID userId, String siteRole);

    void saveEmailToken(EmailToken token);

    Optional<EmailToken> findEmailToken(String tokenHash);

    /** Marks the token used if it still is not. Returns false if it was used already (or a moment ago by someone else). */
    boolean useEmailToken(UUID tokenId, Instant at);

    /** Marks the user's unused tokens of this purpose as used, so only the newest mail works. */
    void invalidateEmailTokens(UUID userId, String purpose, Instant at);

    /** Revokes every refresh token of the user (all sessions). */
    void revokeAllRefreshTokens(UUID userId, Instant at);

    /** Deletes e-mail tokens that expired before this moment. Returns how many. */
    int deleteEmailTokensExpiredBefore(Instant before);

    long countUsers();

    /** How many live accounts are site admins. */
    long countSiteAdmins();

    void saveRefreshToken(RefreshToken token);

    Optional<RefreshToken> findRefreshToken(String tokenHash);

    /** Marks the token revoked if it still is not. Returns false if someone else did it first (a second use of the same token). */
    boolean revokeRefreshToken(UUID tokenId, Instant at);

    /** Revokes every token of the family that is still valid. */
    void revokeFamily(UUID familyId, Instant at);

    List<RefreshToken> refreshTokensOfUser(UUID userId);
}
