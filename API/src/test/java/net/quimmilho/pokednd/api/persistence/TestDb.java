/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.persistence;

import java.util.List;
import java.util.Map;

/**
 * A look at what the database really holds, for the few tests that must see behind the repositories (secrets are never stored in the
 * clear). There is one implementation per kind of database: {@code SqlTestDb} and {@code MongoTestDb}; the build compiles the one
 * that fits the flavour, so the tests are the same on every database.
 */
public interface TestDb {

    /** The stored password hash of the live account with this e-mail address. */
    String passwordHashOf(String email);

    /** How many accounts hold this text as password hash, display name or e-mail address. */
    long accountsHolding(String text);

    /** Is there a refresh token stored under exactly this text? */
    boolean hasRefreshTokenWithHash(String tokenHash);

    /** Every stored row of every table or collection as text, by table or collection name. */
    Map<String, List<String>> everyRowAsText();

    /** The stored token hash and token prefix of a server. */
    List<String> serverTokenFields(String serverId);
}
