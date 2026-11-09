/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.games;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.quimmilho.pokednd.api.auth.Action;
import net.quimmilho.pokednd.api.auth.CurrentUser;
import net.quimmilho.pokednd.api.auth.Ids;
import net.quimmilho.pokednd.api.auth.RateLimiter;
import net.quimmilho.pokednd.api.auth.Role;
import net.quimmilho.pokednd.api.domain.games.GameRepository;
import net.quimmilho.pokednd.api.domain.games.GameRepository.ActiveGameExistsException;
import net.quimmilho.pokednd.api.domain.games.GameRepository.Game;
import net.quimmilho.pokednd.api.domain.games.GameRepository.GameAdmin;
import net.quimmilho.pokednd.api.domain.games.GameRepository.JoinCodeTakenException;
import net.quimmilho.pokednd.api.domain.games.GameRepository.Membership;
import net.quimmilho.pokednd.api.domain.games.GameState;
import net.quimmilho.pokednd.api.error.ApiException;
import net.quimmilho.pokednd.api.minecraft.MinecraftLinkService;
import net.quimmilho.pokednd.api.servers.ServerService;
import net.quimmilho.pokednd.api.settings.BuiltinRuleSets;
import net.quimmilho.pokednd.api.settings.RuleSetService;
import net.quimmilho.pokednd.api.settings.SettingsService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Games: their life from draft to ended, who runs them, who plays in them and how people join (chapters 3.3 to 3.5, 3.8, 16.1 and 17.2).
 *
 * <p>Every method that acts for a person checks the permission table of 16.2 first. Someone who has no role in a game at all is told
 * the game does not exist (404), so a game cannot be found by guessing its id; someone with a role that is not enough gets 403.
 */
@ApplicationScoped
public class GameService {

    public static final int NAME_MAX = 80;
    private static final Duration JOIN_WINDOW = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** What joining answers: the game, and where the request stands. */
    public record Joined(Game game, Membership membership) {
    }

    @Inject
    GameRepository games;

    @Inject
    ServerService servers;

    @Inject
    CurrentUser current;

    @Inject
    RateLimiter limiter;

    @Inject
    SettingsService settings;

    @Inject
    RuleSetService ruleSets;

    @Inject
    net.quimmilho.pokednd.api.domain.accounts.AccountRepository accounts;

    /** How many wrong join codes one person may try in ten minutes. */
    @ConfigProperty(name = "pokednd.games.join-attempts-per-10-minutes", defaultValue = "10")
    int joinAttempts;

    // ------------------------------------------------------------------ who may do what

    /** The roles a person holds in a game (the site role comes from the access token and is added by {@link CurrentUser#require}). */
    public Set<Role> rolesOf(Game game, UUID userId) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        if (servers.isAdmin(game.serverId(), userId)) {
            roles.add(Role.SERVER_ADMIN);
        }
        if (games.adminOf(game.id(), userId).isPresent()) {
            roles.add(Role.GAME_ADMIN);
        }
        games.membership(game.id(), userId).filter(m -> GameRepository.ACCEPTED.equals(m.status())).ifPresent(m -> roles.add(
                GameRepository.ROLE_SPECTATOR.equals(m.role()) ? Role.SPECTATOR : Role.PLAYER));
        return roles;
    }

    /**
     * Loads the game and checks that the signed-in person may do {@code action} in it.
     *
     * @throws ApiException 404 if the game does not exist or the person has no role in it; 403 if their role is not enough
     */
    public Game authorize(UUID gameId, Action action) {
        Game game = games.find(gameId).orElseThrow(() -> ApiException.notFound("This game"));
        Set<Role> roles = rolesOf(game, current.id());
        if (roles.isEmpty() && current.siteRoles().isEmpty()) {
            throw ApiException.notFound("This game");
        }
        // only the right to play a trainer depends on a setting, so the settings are read only then
        current.require(action, roles, action == Action.PLAY_TRAINER && settings.bool(game, "game.admin.plays"));
        return game;
    }

    /** Does this person run the game: a game admin, or the site admin? Only they see the join code. */
    public boolean runs(Game game, UUID userId) {
        return rolesOf(game, userId).contains(Role.GAME_ADMIN) || current.siteRoles().contains(Role.SITE_ADMIN);
    }

    // ------------------------------------------------------------------ creating and reading

    /**
     * Creates a game in the draft state; the signed-in person becomes its owner. The game starts with the rule set {@code ruleSetId}
     * (a built-in one such as {@code builtin:casual}, or a saved one the person may use), or with the plain defaults when that is null.
     *
     * @throws ApiException 403 unless a site admin or an admin of that server; 404 if the server or the rule set does not exist; 400 INVALID_NAME
     */
    @Transactional
    public Game create(UUID serverId, String name, String ruleSetId) {
        UUID me = current.id();
        current.require(Action.CREATE_GAME, servers.isAdmin(serverId, me) ? EnumSet.of(Role.SERVER_ADMIN) : EnumSet.noneOf(Role.class), false);
        servers.get(serverId);
        String cleaned = cleanName(name);
        String ruleSet = null;
        if (ruleSetId != null && !ruleSetId.isBlank()) {
            RuleSetService.Found found = ruleSets.requireUsable(ruleSetId);
            ruleSet = BuiltinRuleSets.DEFAULTS.equals(found.id()) ? null : found.id();
        }
        Game game = new Game(Ids.newId(), serverId, me, cleaned, GameState.DRAFT, null, ruleSet, JsonNodeFactory.instance.objectNode(), 0, Instant.now());
        games.create(game);
        games.addAdmin(game.id(), me, GameRepository.ADMIN_OWNER);
        return game;
    }

    public Game get(UUID gameId) {
        return authorize(gameId, Action.VIEW_GAME);
    }

    /**
     * The games the signed-in person can see, newest first: those they run, those they play in or asked to join, and (for server admins
     * and the site admin) those on their servers. Archived games are left out.
     */
    public List<Game> myGames() {
        UUID me = current.id();
        Map<UUID, Game> found = new LinkedHashMap<>();
        if (current.siteRoles().contains(Role.SITE_ADMIN)) {
            games.allNotArchived().forEach(g -> found.put(g.id(), g));
        } else {
            games.administeredBy(me).forEach(g -> found.put(g.id(), g));
            games.onServersAdministeredBy(me).forEach(g -> found.put(g.id(), g));
            games.joinedBy(me).forEach(g -> found.put(g.id(), g));
        }
        return found.values().stream().sorted(Comparator.comparing(Game::createdAt).reversed().thenComparing(Game::id)).toList();
    }

    @Transactional
    public Game rename(UUID gameId, String name) {
        Game game = authorize(gameId, Action.RUN_GAME);
        if (game.state() == GameState.ARCHIVED) {
            throw invalidState(game.state(), "rename");
        }
        games.rename(gameId, cleanName(name));
        return games.find(gameId).orElseThrow();
    }

    // ------------------------------------------------------------------ the life of a game

    /**
     * Moves the game along the diagram of 3.4. Opening the lobby of a draft is refused while another game of the server is active (3.8).
     * Opening a lobby makes a new join code; leaving it removes the code.
     *
     * @throws ApiException 409 INVALID_TRANSITION, SERVER_HAS_ACTIVE_GAME or GAME_CHANGED
     */
    @Transactional
    public Game command(UUID gameId, GameState.Command command) {
        Game game = authorize(gameId, Action.RUN_GAME);
        GameState from = game.state();
        GameState to = from.next(command).orElseThrow(() -> invalidState(from, command.name().toLowerCase(java.util.Locale.ROOT)));
        if (to.active() && !from.active()) {
            Optional<Game> other = games.activeOnServer(game.serverId());
            if (other.isPresent()) {
                throw activeGame(other.get());
            }
        }
        String code = to.lobbyOpen() ? newUniqueCode(settings.integer(game, "game.lobby.codeLength")) : null;
        boolean moved;
        try {
            moved = games.transition(gameId, from, to, code, Instant.now());
        } catch (ActiveGameExistsException e) {
            throw new ApiException(409, "SERVER_HAS_ACTIVE_GAME", "This server already has an active game. Pause or end it first.");
        } catch (JoinCodeTakenException e) {
            throw new ApiException(409, "JOIN_CODE_TAKEN", "The join code was taken a moment ago. Try again.");
        }
        if (!moved) {
            throw new ApiException(409, "GAME_CHANGED", "Someone changed this game a moment ago. Look again and retry.");
        }
        return games.find(gameId).orElseThrow();
    }

    // ------------------------------------------------------------------ joining

    /**
     * The signed-in person asks to join the game whose lobby is open with this code. Asking again while waiting changes nothing; a
     * person the admin rejected may ask again; a person the admin removed may not.
     *
     * @param role {@code player} or {@code spectator}; null means player
     * @throws ApiException 400 INVALID_JOIN_CODE or INVALID_ROLE; 409 ALREADY_MEMBER; 403 KICKED; 429 RATE_LIMITED
     */
    @Transactional
    public Joined join(String rawCode, String role) {
        UUID me = current.id();
        limiter.hit("game-join", me.toString(), joinAttempts, JOIN_WINDOW);
        String wanted = role == null || role.isBlank() ? GameRepository.ROLE_PLAYER : role.strip().toLowerCase(java.util.Locale.ROOT);
        if (!GameRepository.ROLE_PLAYER.equals(wanted) && !GameRepository.ROLE_SPECTATOR.equals(wanted)) {
            throw ApiException.badRequest("INVALID_ROLE", "Join as a player or as a spectator.");
        }
        Game game = games.findByJoinCode(MinecraftLinkService.normalize(rawCode))
                .orElseThrow(() -> ApiException.badRequest("INVALID_JOIN_CODE", "This code is not right, or the lobby is closed."));
        Optional<Membership> existing = games.membership(game.id(), me);
        if (existing.isPresent()) {
            Membership m = existing.get();
            switch (m.status()) {
                case GameRepository.ACCEPTED -> throw new ApiException(409, "ALREADY_MEMBER", "You are in this game already.");
                case GameRepository.KICKED -> throw new ApiException(403, "KICKED", "The admin removed you from this game.");
                case GameRepository.REJECTED -> {
                    games.setMembershipStatus(m.id(), GameRepository.REJECTED, GameRepository.REQUESTED);
                    games.setMembershipRole(m.id(), wanted);
                }
                default -> {
                    // still waiting: asking again changes nothing
                }
            }
            return new Joined(game, games.membership(game.id(), me).orElseThrow());
        }
        Membership m = new Membership(Ids.newId(), game.id(), me, wanted, GameRepository.REQUESTED, Instant.now());
        if (!games.addMembership(m)) {
            // asked twice at the same moment
            m = games.membership(game.id(), me).orElseThrow();
        }
        return new Joined(game, m);
    }

    /** The people waiting to be accepted, oldest first. */
    public List<Membership> requests(UUID gameId) {
        authorize(gameId, Action.RUN_GAME);
        return games.memberships(gameId, GameRepository.REQUESTED);
    }

    /**
     * The admin accepts a request.
     *
     * @throws ApiException 404 if there is no such request; 409 NOT_PENDING if it was answered already
     */
    @Transactional
    public Membership accept(UUID gameId, UUID requestId) {
        return answer(gameId, requestId, GameRepository.ACCEPTED);
    }

    /** The admin rejects a request. */
    @Transactional
    public Membership reject(UUID gameId, UUID requestId) {
        return answer(gameId, requestId, GameRepository.REJECTED);
    }

    private Membership answer(UUID gameId, UUID requestId, String status) {
        Game game = authorize(gameId, Action.RUN_GAME);
        if (game.state() == GameState.ENDED || game.state() == GameState.ARCHIVED) {
            throw invalidState(game.state(), "answer join requests");
        }
        Membership m = games.membershipById(gameId, requestId).orElseThrow(() -> ApiException.notFound("This request"));
        if (!games.setMembershipStatus(m.id(), GameRepository.REQUESTED, status)) {
            throw new ApiException(409, "NOT_PENDING", "This request was answered already.");
        }
        return games.membershipById(gameId, requestId).orElseThrow();
    }

    // ------------------------------------------------------------------ members

    /** The people who are in the game. */
    public List<Membership> members(UUID gameId) {
        authorize(gameId, Action.VIEW_GAME);
        return games.memberships(gameId, GameRepository.ACCEPTED);
    }

    /**
     * The admin removes a member. They cannot join again with the code.
     *
     * @throws ApiException 404 if the person is not a member
     */
    @Transactional
    public void kick(UUID gameId, UUID userId) {
        authorize(gameId, Action.RUN_GAME);
        Membership m = games.membership(gameId, userId).filter(x -> GameRepository.ACCEPTED.equals(x.status())).orElseThrow(() -> ApiException.notFound("This member"));
        if (!games.setMembershipStatus(m.id(), GameRepository.ACCEPTED, GameRepository.KICKED)) {
            throw ApiException.notFound("This member");
        }
    }

    // ------------------------------------------------------------------ admins

    public List<GameAdmin> adminsOf(UUID gameId) {
        return games.adminsOf(gameId);
    }

    /** The membership of a person in a game, if any. */
    public Optional<Membership> membershipOf(UUID gameId, UUID userId) {
        return games.membership(gameId, userId);
    }

    /**
     * The owner (or the site admin) makes another admin of the same server a co-admin. Nothing changes if they already are an admin.
     *
     * @throws ApiException 403 for a co-admin; 400 NOT_A_SERVER_ADMIN
     */
    @Transactional
    public void addCoAdmin(UUID gameId, UUID userId) {
        Game game = requireOwner(gameId);
        if (!servers.isAdmin(game.serverId(), userId)) {
            throw ApiException.badRequest("NOT_A_SERVER_ADMIN", "Only an admin of this game's server can be made a co-admin.");
        }
        games.addAdmin(gameId, userId, GameRepository.ADMIN_CO);
    }

    /**
     * Takes a co-admin away.
     *
     * @throws ApiException 400 CANNOT_REMOVE_OWNER; 404 if the person is not a co-admin
     */
    @Transactional
    public void removeCoAdmin(UUID gameId, UUID userId) {
        Game game = requireOwner(gameId);
        if (game.ownerId().equals(userId)) {
            throw ApiException.badRequest("CANNOT_REMOVE_OWNER", "The owner cannot be removed.");
        }
        if (!games.removeCoAdmin(gameId, userId)) {
            throw ApiException.notFound("This co-admin");
        }
    }

    /** Only the owner adds or removes co-admins (18.1); the site admin may too, since they hold every power. */
    private Game requireOwner(UUID gameId) {
        Game game = authorize(gameId, Action.RUN_GAME);
        boolean owner = game.ownerId().equals(current.id());
        if (!owner && !current.siteRoles().contains(Role.SITE_ADMIN)) {
            throw ApiException.forbidden();
        }
        return game;
    }

    // ------------------------------------------------------------------ helpers

    public Optional<net.quimmilho.pokednd.api.domain.accounts.AccountRepository.User> user(UUID id) {
        return accounts.findUser(id);
    }

    /** A new code of {@code length} characters that no open lobby has. */
    private String newUniqueCode(int length) {
        String alphabet = MinecraftLinkService.ALPHABET;
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
            }
            if (games.findByJoinCode(sb.toString()).isEmpty()) {
                return sb.toString();
            }
        }
        throw new IllegalStateException("could not find a free join code");
    }

    private static String cleanName(String name) {
        String n = name == null ? "" : name.strip();
        if (n.isEmpty() || n.length() > NAME_MAX || n.chars().anyMatch(Character::isISOControl)) {
            throw ApiException.badRequest("INVALID_NAME", "A game name has 1 to " + NAME_MAX + " characters.");
        }
        return n;
    }

    private static ApiException invalidState(GameState state, String what) {
        return new ApiException(409, "INVALID_TRANSITION", "A game that is " + state.id() + " cannot " + what.replace('_', ' ') + ".",
                Map.of("state", state.id(), "command", what));
    }

    private static ApiException activeGame(Game other) {
        return new ApiException(409, "SERVER_HAS_ACTIVE_GAME", "This server already has an active game (\"" + other.name() + "\"). Pause or end it first.",
                Map.of("gameId", other.id().toString(), "gameName", other.name(), "state", other.state().id()));
    }

    /** For the DTOs: the roles of the signed-in person, as words. */
    public List<String> roleNames(Game game, UUID userId) {
        List<String> names = new ArrayList<>();
        for (Role r : rolesOf(game, userId)) {
            names.add(r.name().toLowerCase(java.util.Locale.ROOT));
        }
        if (current.siteRoles().contains(Role.SITE_ADMIN)) {
            names.add(Role.SITE_ADMIN.name().toLowerCase(java.util.Locale.ROOT));
        }
        return names;
    }
}
