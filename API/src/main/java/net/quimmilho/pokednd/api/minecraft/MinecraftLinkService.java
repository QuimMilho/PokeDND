/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.minecraft;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import net.quimmilho.pokednd.api.auth.AuthConfig;
import net.quimmilho.pokednd.api.auth.Ids;
import net.quimmilho.pokednd.api.auth.RateLimiter;
import net.quimmilho.pokednd.api.domain.minecraft.MinecraftLinkRepository;
import net.quimmilho.pokednd.api.domain.minecraft.MinecraftLinkRepository.LinkCode;
import net.quimmilho.pokednd.api.domain.minecraft.MinecraftLinkRepository.McAlreadyLinkedException;
import net.quimmilho.pokednd.api.domain.minecraft.MinecraftLinkRepository.McLink;
import net.quimmilho.pokednd.api.error.ApiException;

/**
 * Links Minecraft accounts to app accounts by a short code (chapters 3.2, 16.4 and 17.2). There are two directions:
 *
 * <ul>
 *   <li><b>Code from Minecraft, typed in the app:</b> the mod asks for a code for the player ({@link #codeForMinecraft}), the
 *       player types it in the app ({@link #redeemInApp}).
 *   <li><b>Code from the app, typed in Minecraft:</b> the app shows a code ({@link #codeForUser}), the player types it in the game
 *       and the mod hands it over ({@link #completeInMinecraft}).
 * </ul>
 *
 * <p>A code has 8 characters without look-alikes, works once and for 10 minutes. The mod-facing methods are called by the
 * mod endpoints, which need a server token (phase 31); the app-facing ones by {@code /me/minecraft}.
 */
@ApplicationScoped
public class MinecraftLinkService {

    /** How long a code works. */
    public static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    public static final int CODE_LENGTH = 8;
    /**
     * The characters a code is made of. The look-alikes are left out: 0 O Q, 1 I L, 2 Z, 5 S and 8 B. What is left is easy to read
     * aloud and to type from a chat line: 24 characters, so 24^8 (about 1.1 × 10^11) codes.
     */
    public static final String ALPHABET = "ACDEFGHJKMNPRTUVWXY34679";
    /** The characters that are never in a code. */
    public static final String LOOK_ALIKES = "0OQ1ILZ2S5B8";

    private static final Pattern MC_NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    /** A code and the moment it stops working. */
    public record IssuedCode(String code, Instant expiresAt) {
    }

    /** A completed linking started in Minecraft: who the Minecraft account now belongs to. */
    public record Completed(UUID userId, McLink link) {
    }

    @Inject
    MinecraftLinkRepository links;

    @Inject
    RateLimiter limiter;

    @Inject
    AuthConfig config;

    // ------------------------------------------------------------------ making codes

    /**
     * Makes a code for a Minecraft account (the mod calls this when the player types {@code /pokednd link}). An earlier unused code
     * of the same account stops working.
     *
     * @throws ApiException 409 MC_ALREADY_LINKED if the account belongs to an app account already
     */
    @Transactional
    public IssuedCode codeForMinecraft(UUID mcUuid, String mcName) {
        if (mcUuid == null || mcName == null || !MC_NAME.matcher(mcName).matches()) {
            throw ApiException.badRequest("INVALID_MINECRAFT_ACCOUNT", "A Minecraft id and a name of up to 16 letters, digits or underscores are needed.");
        }
        limiter.hit("link-code-mc", mcUuid.toString(), config.rateLimit().linkCodesPerCodeLifetime(), CODE_LIFETIME);
        if (links.findByMcUuid(mcUuid).isPresent()) {
            throw new ApiException(409, "MC_ALREADY_LINKED", "This Minecraft account is linked to an app account already.");
        }
        Instant now = Instant.now();
        links.discardCodes(MinecraftLinkRepository.FOR_MINECRAFT, null, mcUuid, now);
        return issue(MinecraftLinkRepository.FOR_MINECRAFT, null, mcUuid, mcName, now);
    }

    /** Makes a code for an app account, to be typed in Minecraft. An earlier unused code of the same account stops working. */
    @Transactional
    public IssuedCode codeForUser(UUID userId) {
        limiter.hit("link-code-user", userId.toString(), config.rateLimit().linkCodesPerCodeLifetime(), CODE_LIFETIME);
        Instant now = Instant.now();
        links.discardCodes(MinecraftLinkRepository.FOR_APP_USER, userId, null, now);
        return issue(MinecraftLinkRepository.FOR_APP_USER, userId, null, null, now);
    }

    private IssuedCode issue(String direction, UUID userId, UUID mcUuid, String mcName, Instant now) {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = newCode();
            if (links.findCode(code).isEmpty()) {
                Instant expires = now.plus(CODE_LIFETIME);
                links.saveCode(new LinkCode(Ids.newId(), code, direction, userId, mcUuid, mcName, expires, null));
                return new IssuedCode(code, expires);
            }
        }
        throw new IllegalStateException("could not find a free linking code");
    }

    // ------------------------------------------------------------------ using codes

    /**
     * The player typed a code (made by the mod) in the app: the Minecraft account now belongs to {@code userId}.
     *
     * @throws ApiException 400 INVALID_CODE or CODE_EXPIRED; 409 MC_ALREADY_LINKED if another account got there first
     */
    @Transactional
    public McLink redeemInApp(UUID userId, String rawCode) {
        limiter.hit("link-attempt", userId.toString(), config.rateLimit().linkAttemptsPerCodeLifetime(), CODE_LIFETIME);
        LinkCode code = use(rawCode, MinecraftLinkRepository.FOR_MINECRAFT);
        Optional<McLink> existing = links.findByMcUuid(code.mcUuid());
        if (existing.isPresent()) {
            if (existing.get().userId().equals(userId)) {
                return existing.get();
            }
            throw new ApiException(409, "MC_ALREADY_LINKED", "This Minecraft account is linked to another app account already.");
        }
        return add(userId, code.mcUuid(), code.mcName());
    }

    /**
     * The player typed a code (made by the app) in Minecraft and the mod handed it over: the Minecraft account now belongs to the
     * app account that made the code.
     *
     * @throws ApiException 400 INVALID_CODE or CODE_EXPIRED; 409 MC_ALREADY_LINKED if the account belongs to someone else
     */
    @Transactional
    public Completed completeInMinecraft(String rawCode, UUID mcUuid, String mcName) {
        if (mcUuid == null || mcName == null || !MC_NAME.matcher(mcName).matches()) {
            throw ApiException.badRequest("INVALID_MINECRAFT_ACCOUNT", "A Minecraft id and a name of up to 16 letters, digits or underscores are needed.");
        }
        limiter.hit("link-attempt-mc", mcUuid.toString(), config.rateLimit().linkAttemptsPerCodeLifetime(), CODE_LIFETIME);
        LinkCode code = use(rawCode, MinecraftLinkRepository.FOR_APP_USER);
        Optional<McLink> existing = links.findByMcUuid(mcUuid);
        if (existing.isPresent()) {
            if (existing.get().userId().equals(code.userId())) {
                return new Completed(code.userId(), existing.get());
            }
            throw new ApiException(409, "MC_ALREADY_LINKED", "This Minecraft account is linked to another app account already.");
        }
        return new Completed(code.userId(), add(code.userId(), mcUuid, mcName));
    }

    /** Finds the code, checks it and uses it up. */
    private LinkCode use(String rawCode, String direction) {
        Instant now = Instant.now();
        LinkCode code = links.findCode(normalize(rawCode))
                .filter(c -> c.direction().equals(direction) && c.usedAt() == null)
                .orElseThrow(() -> ApiException.badRequest("INVALID_CODE", "This code is not right, or it was used already."));
        if (!code.expiresAt().isAfter(now)) {
            throw ApiException.badRequest("CODE_EXPIRED", "This code has expired. Ask for a new one.");
        }
        if (!links.useCode(code.id(), now)) {
            throw ApiException.badRequest("INVALID_CODE", "This code is not right, or it was used already.");
        }
        return code;
    }

    private McLink add(UUID userId, UUID mcUuid, String mcName) {
        try {
            return links.addLink(new McLink(Ids.newId(), userId, mcUuid, mcName, "code", Instant.now()));
        } catch (McAlreadyLinkedException e) {
            throw new ApiException(409, "MC_ALREADY_LINKED", "This Minecraft account is linked to another app account already.");
        }
    }

    // ------------------------------------------------------------------ the list

    public List<McLink> linksOf(UUID userId) {
        return links.linksOfUser(userId);
    }

    /** @throws ApiException 404 if this account has no such Minecraft link */
    public void unlink(UUID userId, UUID mcUuid) {
        if (!links.removeLink(userId, mcUuid)) {
            throw ApiException.notFound("This Minecraft link");
        }
    }

    // ------------------------------------------------------------------ the code itself

    /** A new random code of {@link #CODE_LENGTH} characters from {@link #ALPHABET}. */
    public static String newCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** What the person typed, made comparable: capitals, and no spaces or dashes (a code may be shown as {@code ABCD-EFGH}). */
    public static String normalize(String typed) {
        return typed == null ? "" : typed.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
    }
}
