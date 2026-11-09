/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import jakarta.enterprise.context.RequestScoped;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository;
import net.quimmilho.pokednd.api.error.ApiException;

/** The person making the current request, taken from the access token by {@link AuthFilters}. */
@RequestScoped
public class CurrentUser {

    private UUID id;
    private String siteRole = AccountRepository.SITE_ROLE_NONE;
    private UUID sessionId;

    void set(UUID id, String siteRole, UUID sessionId) {
        this.sessionId = sessionId;
        this.id = id;
        this.siteRole = siteRole == null ? AccountRepository.SITE_ROLE_NONE : siteRole;
    }

    /** The login (refresh-token family) this access token belongs to; null for tokens made without one. */
    public UUID sessionId() {
        return sessionId;
    }

    public boolean authenticated() {
        return id != null;
    }

    /** The account id.
     *
     * @throws ApiException 401 if nobody is logged in
     */
    public UUID id() {
        if (id == null) {
            throw ApiException.unauthenticated();
        }
        return id;
    }

    /** The site-wide roles (the server and game roles are found by the module that knows the server or game). */
    public Set<Role> siteRoles() {
        return AccountRepository.SITE_ROLE_ADMIN.equals(siteRole) ? EnumSet.of(Role.SITE_ADMIN) : EnumSet.noneOf(Role.class);
    }

    /** Checks an action that depends on a server or game, with the roles found there. */
    public void require(Action action, Set<Role> scopeRoles, boolean adminPlays) {
        EnumSet<Role> all = EnumSet.noneOf(Role.class);
        all.addAll(siteRoles());
        all.addAll(scopeRoles);
        Permissions.require(authenticated(), all, action, adminPlays);
    }
}
