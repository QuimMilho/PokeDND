/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.trainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import net.quimmilho.pokednd.api.auth.Action;
import net.quimmilho.pokednd.api.auth.CurrentUser;
import net.quimmilho.pokednd.api.auth.Ids;
import net.quimmilho.pokednd.api.auth.RateLimiter;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository;
import net.quimmilho.pokednd.api.domain.games.GameRepository.Game;
import net.quimmilho.pokednd.api.domain.games.GameState;
import net.quimmilho.pokednd.api.domain.minecraft.MinecraftLinkRepository;
import net.quimmilho.pokednd.api.domain.trainers.TrainerRepository;
import net.quimmilho.pokednd.api.domain.trainers.TrainerRepository.NameTakenException;
import net.quimmilho.pokednd.api.domain.trainers.TrainerRepository.StoredTrainer;
import net.quimmilho.pokednd.api.domain.trainers.TrainerRepository.TrainerExistsException;
import net.quimmilho.pokednd.api.error.ApiException;
import net.quimmilho.pokednd.api.realtime.GameEvents;
import net.quimmilho.pokednd.api.games.GameService;
import net.quimmilho.pokednd.api.settings.SettingsService;
import net.quimmilho.pokednd.core.trainer.Ability;
import net.quimmilho.pokednd.core.trainer.AbilityScores;
import net.quimmilho.pokednd.core.trainer.ScoreMethod;
import net.quimmilho.pokednd.core.trainer.Trainer;
import net.quimmilho.pokednd.core.trainer.TrainerNames;
import net.quimmilho.pokednd.core.trainer.TrainerProblem;
import net.quimmilho.pokednd.core.trainer.TrainerRulebook;
import net.quimmilho.pokednd.core.trainer.TrainerRulebook.CreateRequest;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Trainers (chapters 3.6, 4.3 and 5.11): a player makes one trainer per game, the rules of Core decide whether it is allowed, and the
 * skin is a cleaned 64×64 PNG. Every method that acts for a person checks the permission table of 16.2 first.
 *
 * <p>A player may change or remove their trainer while the game is still a draft or a lobby (chapter 11.3: "a way to edit before the game
 * starts"); a game admin may do so at any time, but only for a trainer that has not played yet. Changing a running trainer (XP, money,
 * HP) is an admin power that comes with phase 54.
 */
@ApplicationScoped
public class TrainerService {

    private static final Pattern MINECRAFT_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * What a player chooses in the wizard. {@code scores} maps an ability id ({@code str}, {@code dex}, ...) to its score.
     *
     * @param starter the chosen starter species id, or null (the admin assigns it, or the game picks at random)
     */
    public record Choices(String name, String classId, String method, Map<String, Integer> scores, List<String> skills, Map<String, Integer> improvements,
                          String starter) {
    }

    @Inject
    TrainerRepository trainers;

    @Inject
    GameService games;

    @Inject
    TrainerRules rules;

    @Inject
    SettingsService settings;

    @Inject
    SkinStore skins;

    /** Changes are announced to the realtime channel (phase 40), after the transaction is committed. */
    @Inject
    Event<Object> events;

    @Inject
    MojangSkins mojang;

    @Inject
    CurrentUser current;

    @Inject
    RateLimiter limiter;

    @Inject
    MinecraftLinkRepository links;

    @Inject
    AccountRepository accounts;

    @Inject
    ObjectMapper json;

    @ConfigProperty(name = "pokednd.skins.max-bytes", defaultValue = "65536")
    int skinMaxBytes;

    @ConfigProperty(name = "pokednd.skins.username-lookups-per-minute", defaultValue = "10")
    int lookupsPerMinute;

    // ------------------------------------------------------------------ reading

    /** The game and the rules, for a member. */
    public Game gameForMember(UUID gameId) {
        return games.authorize(gameId, Action.VIEW_GAME);
    }

    public List<StoredTrainer> ofGame(UUID gameId) {
        games.authorize(gameId, Action.VIEW_GAME);
        return trainers.ofGame(gameId);
    }

    /** A trainer, for any member of its game. */
    public StoredTrainer get(UUID trainerId) {
        StoredTrainer t = trainers.find(trainerId).orElseThrow(() -> ApiException.notFound("This trainer"));
        games.authorize(t.gameId(), Action.VIEW_GAME);
        return t;
    }

    // ------------------------------------------------------------------ creating, changing, removing

    /** Makes the signed-in person's trainer in the game. */
    public StoredTrainer create(UUID gameId, Choices choices) {
        Game game = games.authorize(gameId, Action.PLAY_TRAINER);
        requireOpenForTrainers(game);
        UUID me = current.id();
        if (trainers.findByUser(gameId, me).isPresent()) {
            throw new ApiException(409, "TRAINER_EXISTS", "You already have a trainer in this game. Change or remove it instead.");
        }
        Built built = build(game, choices, me, null);
        StoredTrainer t = built.stored(Ids.newId(), gameId, me, Instant.now(), Instant.now());
        try {
            trainers.create(t);
        } catch (NameTakenException e) {
            throw invalid(List.of(new TrainerProblem("name_taken", "name", "Another trainer in this game is already called " + t.name() + ".")));
        } catch (TrainerExistsException e) {
            throw new ApiException(409, "TRAINER_EXISTS", "You already have a trainer in this game. Change or remove it instead.");
        }
        events.fire(new GameEvents.TrainerChanged(gameId, t.id(), GameEvents.TrainerChange.CREATED));
        return t;
    }

    /** Replaces the choices of a trainer (the wizard again). The skin stays. */
    public StoredTrainer change(UUID trainerId, Choices choices) {
        StoredTrainer old = trainers.find(trainerId).orElseThrow(() -> ApiException.notFound("This trainer"));
        Game game = authorizeEdit(old);
        Built built = build(game, choices, old.userId(), old);
        StoredTrainer t = built.stored(old.id(), old.gameId(), old.userId(), old.createdAt(), Instant.now());
        try {
            trainers.replaceSheet(t);
        } catch (NameTakenException e) {
            throw invalid(List.of(new TrainerProblem("name_taken", "name", "Another trainer in this game is already called " + t.name() + ".")));
        }
        events.fire(new GameEvents.TrainerChanged(t.gameId(), trainerId, GameEvents.TrainerChange.UPDATED));
        return trainers.find(trainerId).orElseThrow();
    }

    /** Removes a trainer (and its skin file). */
    public void remove(UUID trainerId) {
        StoredTrainer old = trainers.find(trainerId).orElseThrow(() -> ApiException.notFound("This trainer"));
        authorizeEdit(old);
        trainers.delete(trainerId);
        skins.delete(trainerId);
        events.fire(new GameEvents.TrainerChanged(old.gameId(), trainerId, GameEvents.TrainerChange.REMOVED));
    }

    /** Who may change this trainer now: the owner while the game has not started, a game admin while the trainer has not played. */
    private Game authorizeEdit(StoredTrainer t) {
        boolean owner = t.userId().equals(current.id());
        Game game = games.authorize(t.gameId(), owner ? Action.PLAY_TRAINER : Action.RUN_GAME);
        requireOpenForTrainers(game);
        if (owner && !games.runs(game, current.id()) && game.state() != GameState.DRAFT && game.state() != GameState.LOBBY) {
            throw new ApiException(409, "GAME_STARTED", "The game has started, so only the admin can change this trainer now.");
        }
        TrainerRulebook rulebook = rules.rulebook(game);
        if (t.level() != rulebook.settings().xp().levelStart() || t.xp() != rulebook.settings().xp().totalXp(rulebook.settings().xp().levelStart())) {
            throw new ApiException(409, "TRAINER_PROGRESSED", "This trainer has played already, so its sheet can no longer be remade.");
        }
        return game;
    }

    private static void requireOpenForTrainers(Game game) {
        if (game.state() == GameState.ENDED || game.state() == GameState.ARCHIVED) {
            throw new ApiException(409, "INVALID_STATE", "Trainers cannot be made or changed in a game that is " + game.state().id() + ".");
        }
    }

    // ------------------------------------------------------------------ validation and building

    /** A trainer ready to be stored. */
    private record Built(Trainer trainer, ScoreMethod method, String starter, int starterLevel, ObjectMapper json) {

        StoredTrainer stored(UUID id, UUID gameId, UUID userId, Instant createdAt, Instant at) {
            Map<String, Integer> sc = new LinkedHashMap<>();
            for (Ability a : Ability.values()) {
                sc.put(a.id(), trainer.scores().get(a));
            }
            return new StoredTrainer(id, gameId, userId, trainer.name(), trainer.classId(), trainer.level(), trainer.xp(), json.valueToTree(sc),
                    json.valueToTree(trainer.skills()), trainer.hp(), trainer.maxHp(), trainer.money(), json.valueToTree(new TreeMap<>(trainer.items())),
                    method.id(), starter, starter == null ? null : starterLevel, null, null, null, null, null, createdAt, at);
        }
    }

    private Built build(Game game, Choices c, UUID owner, StoredTrainer existing) {
        TrainerRulebook rulebook = rules.rulebook(game);
        TrainerRules.Starters starters = rules.starters(game);
        List<TrainerProblem> problems = new ArrayList<>();

        // the name
        String name = c.name();
        if ("accountName".equals(settings.value(game, "game.trainerNames.mode").asText())) {
            name = links.linksOfUser(owner).stream().map(MinecraftLinkRepository.McLink::mcName).findFirst().orElse(null);
            if (name == null) {
                throw new ApiException(409, "NO_MINECRAFT_ACCOUNT",
                        "This game uses your Minecraft name as the trainer name. Link a Minecraft account first (Account page).");
            }
        }
        List<String> otherNames = trainers.ofGame(game.id()).stream().filter(t -> existing == null || !t.id().equals(existing.id()))
                .map(StoredTrainer::name).toList();

        // the scores
        ScoreMethod method = null;
        if (c.method() != null) {
            try {
                method = ScoreMethod.parse(c.method());
            } catch (IllegalArgumentException e) {
                problems.add(new TrainerProblem("method_unknown", "scores", e.getMessage()));
            }
        }
        AbilityScores scores = scoresOf(c.scores(), problems);
        Map<Ability, Integer> improvements = new EnumMap<>(Ability.class);
        if (c.improvements() != null) {
            c.improvements().forEach((k, v) -> {
                try {
                    improvements.put(Ability.parse(k), v == null ? 0 : v);
                } catch (IllegalArgumentException e) {
                    problems.add(new TrainerProblem("improvement_unknown", "improvement", e.getMessage()));
                }
            });
        }

        CreateRequest request = new CreateRequest(name, c.classId(), method, scores, c.skills(), improvements);
        problems.addAll(rulebook.check(request, otherNames));

        // the starter (5.11)
        String starter = null;
        Set<String> allowed = new java.util.HashSet<>();
        starters.species().forEach(s -> allowed.add(s.id()));
        switch (starters.method()) {
            case "pick" -> {
                if (c.starter() == null || c.starter().isBlank()) {
                    problems.add(new TrainerProblem("starter_missing", "starter", "Choose a starter Pokémon."));
                } else if (!allowed.contains(c.starter())) {
                    problems.add(new TrainerProblem("starter_not_allowed", "starter", c.starter() + " is not one of the starter Pokémon of this game."));
                } else {
                    starter = c.starter();
                }
            }
            case "random" -> {
                if (starters.species().isEmpty()) {
                    problems.add(new TrainerProblem("starter_missing", "starter", "This game has no starter Pokémon to draw from."));
                } else {
                    starter = existing != null && existing.starterSpecies() != null ? existing.starterSpecies()
                            : starters.species().get(RANDOM.nextInt(starters.species().size())).id();
                }
            }
            default -> {
                // "assign": the admin gives the starter later; whatever the player sent is ignored
            }
        }

        if (!problems.isEmpty()) {
            throw invalid(problems);
        }
        Trainer trainer = rulebook.create(request, otherNames);
        return new Built(trainer, method, starter, starters.level(), json);
    }

    private static AbilityScores scoresOf(Map<String, Integer> raw, List<TrainerProblem> problems) {
        if (raw == null) {
            return null;
        }
        EnumMap<Ability, Integer> map = new EnumMap<>(Ability.class);
        for (Map.Entry<String, Integer> e : raw.entrySet()) {
            try {
                map.put(Ability.parse(e.getKey()), e.getValue());
            } catch (IllegalArgumentException ex) {
                problems.add(new TrainerProblem("scores_invalid", "scores", ex.getMessage()));
                return null;
            }
        }
        if (map.size() != Ability.values().length || map.containsValue(null)) {
            problems.add(new TrainerProblem("scores_missing", "scores", "Set all six ability scores."));
            return null;
        }
        try {
            return new AbilityScores(map);
        } catch (IllegalArgumentException ex) {
            problems.add(new TrainerProblem("scores_invalid", "scores", ex.getMessage()));
            return null;
        }
    }

    static ApiException invalid(List<TrainerProblem> problems) {
        List<Map<String, String>> list = problems.stream().map(p -> Map.of("code", p.code(), "field", p.field(), "message", p.message())).toList();
        String message = problems.stream().map(TrainerProblem::message).reduce((a, b) -> a + " " + b).orElse("The trainer is not valid.");
        return new ApiException(422, "INVALID_TRAINER", message, Map.of("problems", list));
    }

    // ------------------------------------------------------------------ skins

    /** Uses an uploaded PNG as the skin. */
    public StoredTrainer uploadSkin(UUID trainerId, byte[] file, String model) {
        StoredTrainer t = trainers.find(trainerId).orElseThrow(() -> ApiException.notFound("This trainer"));
        Game game = authorizeSkin(t);
        if (!games.runs(game, current.id()) && !settings.bool(game, "game.skins.allowUpload")) {
            throw new ApiException(403, "SKIN_UPLOAD_OFF", "This game does not allow uploaded skins.");
        }
        String m = model == null || model.isBlank() ? "classic" : model.strip().toLowerCase(Locale.ROOT);
        if (!m.equals("classic") && !m.equals("slim")) {
            throw ApiException.badRequest("INVALID_MODEL", "The skin model is classic or slim, not " + model + ".");
        }
        SkinImages.Clean clean = SkinImages.clean(file, skinMaxBytes);
        return keep(t, clean, m, "upload", null);
    }

    /** Copies the skin of a Minecraft account. */
    public StoredTrainer copySkin(UUID trainerId, String username) {
        StoredTrainer t = trainers.find(trainerId).orElseThrow(() -> ApiException.notFound("This trainer"));
        Game game = authorizeSkin(t);
        if (!games.runs(game, current.id()) && !settings.bool(game, "game.skins.allowCopyFromUsername")) {
            throw new ApiException(403, "SKIN_COPY_OFF", "This game does not allow copying a skin from a Minecraft name.");
        }
        String name = username == null ? "" : username.strip();
        if (!MINECRAFT_NAME.matcher(name).matches()) {
            throw ApiException.badRequest("INVALID_MINECRAFT_NAME", "A Minecraft name has 3 to 16 letters, digits or underscores.");
        }
        limiter.hit("skin-lookup", current.id().toString(), lookupsPerMinute, Duration.ofMinutes(1));
        MojangSkins.Found found = mojang.find(name);
        SkinImages.Clean clean = SkinImages.clean(found.png(), skinMaxBytes);
        return keep(t, clean, found.slim() ? "slim" : "classic", "username", found.username());
    }

    /** Takes the skin off. */
    public StoredTrainer clearSkin(UUID trainerId) {
        StoredTrainer t = trainers.find(trainerId).orElseThrow(() -> ApiException.notFound("This trainer"));
        authorizeSkin(t);
        trainers.clearSkin(trainerId);
        skins.delete(trainerId);
        events.fire(new GameEvents.TrainerChanged(t.gameId(), trainerId, GameEvents.TrainerChange.SKIN));
        return trainers.find(trainerId).orElseThrow();
    }

    /** The PNG of a trainer's skin, for any member. */
    public byte[] skinPng(UUID trainerId) {
        StoredTrainer t = get(trainerId);
        if (!t.hasSkin()) {
            throw ApiException.notFound("This skin");
        }
        return skins.read(trainerId).orElseThrow(() -> ApiException.notFound("This skin"));
    }

    private Game authorizeSkin(StoredTrainer t) {
        boolean owner = t.userId().equals(current.id());
        Game game = games.authorize(t.gameId(), owner ? Action.PLAY_TRAINER : Action.RUN_GAME);
        requireOpenForTrainers(game);
        return game;
    }

    private StoredTrainer keep(StoredTrainer t, SkinImages.Clean clean, String model, String source, String username) {
        skins.write(t.id(), clean.png());
        trainers.setSkin(t.id(), model, source, username, clean.sha256(), Instant.now());
        events.fire(new GameEvents.TrainerChanged(t.gameId(), t.id(), GameEvents.TrainerChange.SKIN));
        return trainers.find(t.id()).orElseThrow();
    }

    // ------------------------------------------------------------------ for the DTOs

    /** The name of the person who owns the trainer. */
    public String ownerName(UUID userId) {
        Optional<AccountRepository.User> u = accounts.findUser(userId);
        return u.map(AccountRepository.User::displayName).orElse("");
    }

    /** The trainer as Core sees it, to work out its sheet. */
    static Trainer coreTrainer(StoredTrainer t) {
        EnumMap<Ability, Integer> scores = new EnumMap<>(Ability.class);
        for (Ability a : Ability.values()) {
            scores.put(a, t.abilityScores().path(a.id()).asInt(10));
        }
        List<String> skills = new ArrayList<>();
        t.skills().forEach(n -> skills.add(n.asText()));
        Map<String, Integer> items = new TreeMap<>();
        JsonNode bag = t.items();
        bag.fields().forEachRemaining(e -> items.put(e.getKey(), e.getValue().asInt()));
        return new Trainer(t.name(), t.classId(), t.level(), t.xp(), new AbilityScores(scores), skills, t.hpMax(), t.hp(), t.money(), items);
    }

    /** Is the person's trainer name valid in shape? (used by the wizard for a quick answer) */
    public static List<TrainerProblem> checkName(String name) {
        return TrainerNames.check(name);
    }
}
