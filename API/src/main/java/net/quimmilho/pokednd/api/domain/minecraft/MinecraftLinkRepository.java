/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.domain.minecraft;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Where linked Minecraft accounts and their linking codes are kept (chapter 17.2). The SQL adapter implements it. */
public interface MinecraftLinkRepository {

    /** Codes made for a Minecraft account (typed in the app) and codes made for an app account (typed in Minecraft). */
    String FOR_MINECRAFT = "mc";
    String FOR_APP_USER = "app";

    /** A Minecraft account linked to an app account. {@code mcUuid} is unique: it belongs to one app account. */
    record McLink(UUID id, UUID userId, UUID mcUuid, String mcName, String source, Instant linkedAt) {
    }

    /**
     * A linking code. For {@link #FOR_MINECRAFT} the Minecraft account is known ({@code mcUuid}, {@code mcName}); for
     * {@link #FOR_APP_USER} the app account is known ({@code userId}).
     */
    record LinkCode(UUID id, String code, String direction, UUID userId, UUID mcUuid, String mcName, Instant expiresAt, Instant usedAt) {
    }

    /** Raised when the Minecraft account already belongs to an app account. */
    class McAlreadyLinkedException extends RuntimeException {
        public McAlreadyLinkedException(UUID mcUuid) {
            super("The Minecraft account is already linked: " + mcUuid);
        }
    }

    Optional<McLink> findByMcUuid(UUID mcUuid);

    List<McLink> linksOfUser(UUID userId);

    /** @throws McAlreadyLinkedException if the unique {@code mcUuid} is taken */
    McLink addLink(McLink link);

    /** Removes the link if it belongs to this user. Returns false if there was none. */
    boolean removeLink(UUID userId, UUID mcUuid);

    void saveCode(LinkCode code);

    Optional<LinkCode> findCode(String code);

    /** Marks the code used if it still is not. Returns false if it was used already (a second use of the same code). */
    boolean useCode(UUID codeId, Instant at);

    /** Marks the unused codes of one subject as used: all codes of that direction made for this user, or for this Minecraft account. */
    void discardCodes(String direction, UUID userId, UUID mcUuid, Instant at);

    /** Deletes codes that expired before this moment. Returns how many. */
    int deleteCodesExpiredBefore(Instant before);
}
