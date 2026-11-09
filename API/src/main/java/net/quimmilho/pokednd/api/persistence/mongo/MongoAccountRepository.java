/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.persistence.mongo;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.lt;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Updates;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.quimmilho.pokednd.api.auth.Ids;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository;
import net.quimmilho.pokednd.api.persistence.mongo.MongoDocs.AppUser;
import net.quimmilho.pokednd.api.persistence.mongo.MongoDocs.EmailTokenRow;
import net.quimmilho.pokednd.api.persistence.mongo.MongoDocs.RefreshTokenRow;

/** The MongoDB adapter of the accounts: MongoDB with Panache (chapter 16.3). */
@ApplicationScoped
public class MongoAccountRepository implements AccountRepository {

    private static MongoCollection<AppUser> users() {
        return AppUser.mongoCollection();
    }

    private static MongoCollection<MongoDocs.Identity> identities() {
        return MongoDocs.Identity.mongoCollection();
    }

    private static MongoCollection<EmailTokenRow> emailTokens() {
        return EmailTokenRow.mongoCollection();
    }

    private static MongoCollection<RefreshTokenRow> refreshTokens() {
        return RefreshTokenRow.mongoCollection();
    }

    @Override
    public Optional<User> findUser(UUID id) {
        return Optional.ofNullable(users().find(eq("_id", id)).first()).filter(u -> u.deletedAt == null).map(MongoAccountRepository::toUser);
    }

    @Override
    public Optional<User> findUserByEmail(String email) {
        return Optional.ofNullable(users().find(eq("liveEmail", lower(email))).first()).map(MongoAccountRepository::toUser);
    }

    @Override
    public User createUser(User user) {
        return insert(user, new Identity(Ids.newId(), user.id(), PROVIDER_PASSWORD, user.id().toString(), user.createdAt()));
    }

    @Override
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
        row.liveEmail = lower(user.email());
        row.emailVerified = user.emailVerified();
        row.passwordHash = user.passwordHash();
        row.displayName = user.displayName();
        row.locale = user.locale();
        row.theme = user.theme();
        row.siteRole = user.siteRole();
        row.createdAt = user.createdAt();
        row.updatedAt = user.createdAt();
        try {
            users().insertOne(row);
        } catch (RuntimeException e) {
            if (MongoSupport.isDuplicate(e)) {
                // two sign-ups with the same address at the same moment: the unique index stops the second
                throw new EmailTakenException(user.email());
            }
            throw e;
        }
        try {
            identities().insertOne(toRow(identity));
        } catch (RuntimeException e) {
            // there is no transaction to roll back: take the account back by hand
            users().deleteOne(eq("_id", user.id()));
            if (MongoSupport.isDuplicate(e)) {
                throw new EmailTakenException(user.email());
            }
            throw e;
        }
        return toUser(row);
    }

    @Override
    public Optional<Identity> findIdentity(String provider, String providerUserId) {
        return Optional.ofNullable(identities().find(and(eq("provider", provider), eq("providerUserId", providerUserId))).first())
                .map(MongoAccountRepository::toIdentity);
    }

    @Override
    public List<Identity> identitiesOfUser(UUID userId) {
        return MongoSupport.list(identities().find(eq("userId", userId)).sort(MongoSupport.oldestFirst())).stream()
                .map(MongoAccountRepository::toIdentity).toList();
    }

    @Override
    public void addIdentity(Identity identity) {
        identities().insertOne(toRow(identity));
    }

    @Override
    public boolean removeIdentity(UUID userId, String provider) {
        return identities().deleteMany(and(eq("userId", userId), eq("provider", provider))).getDeletedCount() > 0;
    }

    @Override
    public void markEmailVerified(UUID userId) {
        users().updateOne(eq("_id", userId), Updates.combine(Updates.set("emailVerified", true), Updates.set("updatedAt", Instant.now())));
    }

    @Override
    public void setPasswordHash(UUID userId, String passwordHash) {
        users().updateOne(eq("_id", userId), Updates.combine(passwordHash == null ? Updates.unset("passwordHash") : Updates.set("passwordHash", passwordHash),
                Updates.set("updatedAt", Instant.now())));
    }

    @Override
    public void setSiteRole(UUID userId, String siteRole) {
        users().updateOne(eq("_id", userId), Updates.combine(Updates.set("siteRole", siteRole), Updates.set("updatedAt", Instant.now())));
    }

    @Override
    public void updateProfile(UUID userId, String displayName, String locale, String theme) {
        java.util.List<org.bson.conversions.Bson> changes = new java.util.ArrayList<>();
        if (displayName != null) {
            changes.add(Updates.set("displayName", displayName));
        }
        if (locale != null) {
            changes.add(Updates.set("locale", locale));
        }
        if (theme != null) {
            changes.add(Updates.set("theme", theme));
        }
        changes.add(Updates.set("updatedAt", Instant.now()));
        users().updateOne(eq("_id", userId), Updates.combine(changes));
    }

    @Override
    public long countUsers() {
        return users().countDocuments(eq("deletedAt", null));
    }

    @Override
    public long countSiteAdmins() {
        return users().countDocuments(and(eq("deletedAt", null), eq("siteRole", SITE_ROLE_ADMIN)));
    }

    // ------------------------------------------------------------------ e-mail tokens

    @Override
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
        emailTokens().insertOne(row);
    }

    @Override
    public Optional<EmailToken> findEmailToken(String tokenHash) {
        return Optional.ofNullable(emailTokens().find(eq("tokenHash", tokenHash)).first())
                .map(t -> new EmailToken(t.id, t.userId, t.purpose, t.tokenHash, t.expiresAt, t.usedAt));
    }

    @Override
    public boolean useEmailToken(UUID tokenId, Instant at) {
        return emailTokens().updateOne(and(eq("_id", tokenId), eq("usedAt", null)), Updates.combine(Updates.set("usedAt", at), Updates.set("updatedAt", at)))
                .getModifiedCount() == 1;
    }

    @Override
    public void invalidateEmailTokens(UUID userId, String purpose, Instant at) {
        emailTokens().updateMany(and(eq("userId", userId), eq("purpose", purpose), eq("usedAt", null)),
                Updates.combine(Updates.set("usedAt", at), Updates.set("updatedAt", at)));
    }

    @Override
    public int deleteEmailTokensExpiredBefore(Instant before) {
        return (int) emailTokens().deleteMany(lt("expiresAt", before)).getDeletedCount();
    }

    // ------------------------------------------------------------------ refresh tokens

    @Override
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
        refreshTokens().insertOne(row);
    }

    @Override
    public Optional<RefreshToken> findRefreshToken(String tokenHash) {
        return Optional.ofNullable(refreshTokens().find(eq("tokenHash", tokenHash)).first()).map(MongoAccountRepository::toToken);
    }

    @Override
    public boolean revokeRefreshToken(UUID tokenId, Instant at) {
        return refreshTokens().updateOne(and(eq("_id", tokenId), eq("revokedAt", null)), Updates.combine(Updates.set("revokedAt", at), Updates.set("updatedAt", at)))
                .getModifiedCount() == 1;
    }

    @Override
    public void revokeFamily(UUID familyId, Instant at) {
        refreshTokens().updateMany(and(eq("familyId", familyId), eq("revokedAt", null)), Updates.combine(Updates.set("revokedAt", at), Updates.set("updatedAt", at)));
    }

    @Override
    public void revokeAllRefreshTokens(UUID userId, Instant at) {
        refreshTokens().updateMany(and(eq("userId", userId), eq("revokedAt", null)), Updates.combine(Updates.set("revokedAt", at), Updates.set("updatedAt", at)));
    }

    @Override
    public List<RefreshToken> refreshTokensOfUser(UUID userId) {
        return MongoSupport.list(refreshTokens().find(eq("userId", userId)).sort(MongoSupport.oldestFirst())).stream().map(MongoAccountRepository::toToken)
                .toList();
    }

    // ------------------------------------------------------------------ mapping

    private static String lower(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static User toUser(AppUser u) {
        return new User(u.id, u.email, u.emailVerified, u.passwordHash, u.displayName, u.locale, u.theme, u.siteRole, u.createdAt, u.deletedAt);
    }

    private static RefreshToken toToken(RefreshTokenRow t) {
        return new RefreshToken(t.id, t.userId, t.familyId, t.tokenHash, t.expiresAt, t.revokedAt, t.userAgent, t.createdAt);
    }

    private static Identity toIdentity(MongoDocs.Identity i) {
        return new Identity(i.id, i.userId, i.provider, i.providerUserId, i.createdAt);
    }

    private static MongoDocs.Identity toRow(Identity i) {
        MongoDocs.Identity row = new MongoDocs.Identity();
        row.id = i.id();
        row.userId = i.userId();
        row.provider = i.provider();
        row.providerUserId = i.providerUserId();
        row.createdAt = i.createdAt();
        row.updatedAt = i.createdAt();
        return row;
    }
}
