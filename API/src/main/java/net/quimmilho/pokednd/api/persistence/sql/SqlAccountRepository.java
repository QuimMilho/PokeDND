/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.persistence.sql;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.quimmilho.pokednd.api.auth.Ids;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository;
import net.quimmilho.pokednd.api.persistence.sql.AccountTables.AppUser;
import net.quimmilho.pokednd.api.persistence.sql.AccountTables.EmailTokenRow;
import net.quimmilho.pokednd.api.persistence.sql.AccountTables.RefreshTokenRow;

/** The SQL adapter of the accounts: Hibernate ORM with Panache (chapter 16.3). */
@ApplicationScoped
public class SqlAccountRepository implements AccountRepository {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public Optional<User> findUser(UUID id) {
        return Optional.ofNullable(em.find(AppUser.class, id)).filter(u -> u.deletedAt == null).map(SqlAccountRepository::toUser);
    }

    @Override
    @Transactional
    public Optional<User> findUserByEmail(String email) {
        return em.createQuery("from AppUser u where lower(u.email) = :e and u.deletedAt is null", AppUser.class)
                .setParameter("e", email.strip().toLowerCase(Locale.ROOT)).getResultStream().findFirst().map(SqlAccountRepository::toUser);
    }

    @Override
    @Transactional
    public User createUser(User user) {
        return insert(user, new Identity(Ids.newId(), user.id(), PROVIDER_PASSWORD, user.id().toString(), user.createdAt()));
    }

    @Override
    @Transactional
    public User createOAuthUser(User user, Identity identity) {
        return insert(user, identity);
    }

    private User insert(User user, Identity identity) {
        if (findUserByEmail(user.email()).isPresent()) {
            throw new EmailTakenException(user.email());
        }
        AppUser row = new AppUser();
        row.id = user.id();
        row.email = user.email();
        row.emailVerified = user.emailVerified();
        row.passwordHash = user.passwordHash();
        row.displayName = user.displayName();
        row.locale = user.locale();
        row.theme = user.theme();
        row.siteRole = user.siteRole();
        row.createdAt = user.createdAt();
        row.updatedAt = user.createdAt();
        try {
            em.persist(row);
            em.flush(); // the user row must exist before the identity that points at it
            em.persist(toRow(identity));
            em.flush();
        } catch (PersistenceException e) {
            // two sign-ups with the same address at the same moment: the unique index stops the second
            throw new EmailTakenException(user.email());
        }
        return toUser(row);
    }

    @Override
    @Transactional
    public Optional<Identity> findIdentity(String provider, String providerUserId) {
        return em.createQuery("from Identity i where i.provider = :p and i.providerUserId = :u", AccountTables.Identity.class)
                .setParameter("p", provider).setParameter("u", providerUserId).getResultStream().findFirst()
                .map(SqlAccountRepository::toIdentity);
    }

    @Override
    @Transactional
    public List<Identity> identitiesOfUser(UUID userId) {
        return em.createQuery("from Identity i where i.userId = :u order by i.createdAt", AccountTables.Identity.class)
                .setParameter("u", userId).getResultList().stream().map(SqlAccountRepository::toIdentity).toList();
    }

    @Override
    @Transactional
    public void addIdentity(Identity identity) {
        em.persist(toRow(identity));
        em.flush();
    }

    @Override
    @Transactional
    public boolean removeIdentity(UUID userId, String provider) {
        return em.createQuery("delete from Identity i where i.userId = :u and i.provider = :p").setParameter("u", userId)
                .setParameter("p", provider).executeUpdate() > 0;
    }

    @Override
    @Transactional
    public void markEmailVerified(UUID userId) {
        em.createQuery("update AppUser u set u.emailVerified = true, u.updatedAt = :at where u.id = :id").setParameter("at", Instant.now())
                .setParameter("id", userId).executeUpdate();
    }

    @Override
    @Transactional
    public void setPasswordHash(UUID userId, String passwordHash) {
        em.createQuery("update AppUser u set u.passwordHash = :h, u.updatedAt = :at where u.id = :id").setParameter("h", passwordHash)
                .setParameter("at", Instant.now()).setParameter("id", userId).executeUpdate();
    }

    @Override
    @Transactional
    public void setSiteRole(UUID userId, String siteRole) {
        AppUser u = em.find(AppUser.class, userId);
        if (u != null) {
            u.siteRole = siteRole;
            u.updatedAt = Instant.now();
        }
    }

    @Override
    @Transactional
    public long countUsers() {
        return em.createQuery("select count(u) from AppUser u where u.deletedAt is null", Long.class).getSingleResult();
    }

    @Override
    @Transactional
    public long countSiteAdmins() {
        return em.createQuery("select count(u) from AppUser u where u.deletedAt is null and u.siteRole = :r", Long.class)
                .setParameter("r", SITE_ROLE_ADMIN).getSingleResult();
    }

    // ------------------------------------------------------------------ e-mail tokens

    @Override
    @Transactional
    public void saveEmailToken(EmailToken t) {
        EmailTokenRow row = new EmailTokenRow();
        row.id = t.id();
        row.userId = t.userId();
        row.purpose = t.purpose();
        row.tokenHash = t.tokenHash();
        row.expiresAt = t.expiresAt();
        row.usedAt = t.usedAt();
        row.createdAt = Instant.now();
        row.updatedAt = row.createdAt;
        em.persist(row);
    }

    @Override
    @Transactional
    public Optional<EmailToken> findEmailToken(String tokenHash) {
        return em.createQuery("from EmailTokenRow t where t.tokenHash = :h", EmailTokenRow.class).setParameter("h", tokenHash)
                .getResultStream().findFirst().map(t -> new EmailToken(t.id, t.userId, t.purpose, t.tokenHash, t.expiresAt, t.usedAt));
    }

    @Override
    @Transactional
    public boolean useEmailToken(UUID tokenId, Instant at) {
        return em.createQuery("update EmailTokenRow t set t.usedAt = :at, t.updatedAt = :at where t.id = :id and t.usedAt is null")
                .setParameter("at", at).setParameter("id", tokenId).executeUpdate() == 1;
    }

    @Override
    @Transactional
    public void invalidateEmailTokens(UUID userId, String purpose, Instant at) {
        em.createQuery("update EmailTokenRow t set t.usedAt = :at, t.updatedAt = :at where t.userId = :u and t.purpose = :p and t.usedAt is null")
                .setParameter("at", at).setParameter("u", userId).setParameter("p", purpose).executeUpdate();
    }

    @Override
    @Transactional
    public int deleteEmailTokensExpiredBefore(Instant before) {
        return em.createQuery("delete from EmailTokenRow t where t.expiresAt < :b").setParameter("b", before).executeUpdate();
    }

    // ------------------------------------------------------------------ refresh tokens

    @Override
    @Transactional
    public void saveRefreshToken(RefreshToken t) {
        RefreshTokenRow row = new RefreshTokenRow();
        row.id = t.id();
        row.userId = t.userId();
        row.familyId = t.familyId();
        row.tokenHash = t.tokenHash();
        row.expiresAt = t.expiresAt();
        row.revokedAt = t.revokedAt();
        row.userAgent = t.userAgent();
        row.createdAt = Instant.now();
        row.updatedAt = row.createdAt;
        em.persist(row);
    }

    @Override
    @Transactional
    public Optional<RefreshToken> findRefreshToken(String tokenHash) {
        return em.createQuery("from RefreshTokenRow t where t.tokenHash = :h", RefreshTokenRow.class).setParameter("h", tokenHash)
                .getResultStream().findFirst().map(SqlAccountRepository::toToken);
    }

    @Override
    @Transactional
    public boolean revokeRefreshToken(UUID tokenId, Instant at) {
        return em.createQuery("update RefreshTokenRow t set t.revokedAt = :at, t.updatedAt = :at where t.id = :id and t.revokedAt is null")
                .setParameter("at", at).setParameter("id", tokenId).executeUpdate() == 1;
    }

    @Override
    @Transactional
    public void revokeFamily(UUID familyId, Instant at) {
        em.createQuery("update RefreshTokenRow t set t.revokedAt = :at, t.updatedAt = :at where t.familyId = :f and t.revokedAt is null")
                .setParameter("at", at).setParameter("f", familyId).executeUpdate();
    }

    @Override
    @Transactional
    public void revokeAllRefreshTokens(UUID userId, Instant at) {
        em.createQuery("update RefreshTokenRow t set t.revokedAt = :at, t.updatedAt = :at where t.userId = :u and t.revokedAt is null")
                .setParameter("at", at).setParameter("u", userId).executeUpdate();
    }

    @Override
    @Transactional
    public List<RefreshToken> refreshTokensOfUser(UUID userId) {
        return em.createQuery("from RefreshTokenRow t where t.userId = :u order by t.createdAt", RefreshTokenRow.class)
                .setParameter("u", userId).getResultList().stream().map(SqlAccountRepository::toToken).toList();
    }

    // ------------------------------------------------------------------ mapping

    private static User toUser(AppUser u) {
        return new User(u.id, u.email, u.emailVerified, u.passwordHash, u.displayName, u.locale, u.theme, u.siteRole, u.createdAt, u.deletedAt);
    }

    private static RefreshToken toToken(RefreshTokenRow t) {
        return new RefreshToken(t.id, t.userId, t.familyId, t.tokenHash, t.expiresAt, t.revokedAt, t.userAgent, t.createdAt);
    }

    private static Identity toIdentity(AccountTables.Identity i) {
        return new Identity(i.id, i.userId, i.provider, i.providerUserId, i.createdAt);
    }

    private static AccountTables.Identity toRow(Identity i) {
        AccountTables.Identity row = new AccountTables.Identity();
        row.id = i.id();
        row.userId = i.userId();
        row.provider = i.provider();
        row.providerUserId = i.providerUserId();
        row.createdAt = i.createdAt();
        row.updatedAt = i.createdAt();
        return row;
    }
}
