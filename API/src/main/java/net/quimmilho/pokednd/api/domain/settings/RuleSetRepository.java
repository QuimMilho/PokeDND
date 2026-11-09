/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.domain.settings;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Where saved rule sets are kept (chapter 17.2, {@code RuleSet}). The built-in ones are not here: they come with the code. */
public interface RuleSetRepository {

    /** Who a rule set belongs to. */
    String SCOPE_SITE = "site";
    String SCOPE_SERVER = "server";
    String SCOPE_USER = "user";

    /**
     * A saved rule set.
     *
     * @param ownerId  the person who made it
     * @param serverId the server it belongs to; only for the scope {@code server}
     * @param basedOn  where it was started from, as a note ({@code builtin:defaults} or the id of another rule set)
     * @param settings the settings it changes, a JSON object of key to value
     * @param data     data that comes with it (class list, skill list, power table, type chart, loot), a JSON object
     */
    record RuleSet(UUID id, String scope, UUID ownerId, UUID serverId, String name, int formatVersion, String basedOn, JsonNode settings, JsonNode data,
                   Instant createdAt, Instant updatedAt) {
    }

    RuleSet create(RuleSet ruleSet);

    Optional<RuleSet> find(UUID id);

    /** The rule sets of one scope ({@code site}), by name. */
    List<RuleSet> ofScope(String scope);

    /** The rule sets of the scope {@code user} that this person made, by name. */
    List<RuleSet> ownedBy(UUID userId);

    /** The rule sets of the scope {@code server} for these servers, by name. */
    List<RuleSet> ofServers(Collection<UUID> serverIds);

    long countOwnedBy(UUID userId);

    /** Replaces the name, the note, the settings and the data. Returns false if there is no such rule set. */
    boolean update(UUID id, String name, String basedOn, JsonNode settings, JsonNode data, Instant at);

    /** Deletes the rule set. Returns false if there is no such rule set. */
    boolean delete(UUID id);
}
