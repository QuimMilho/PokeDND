/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import java.util.Optional;
import net.quimmilho.pokednd.api.domain.games.GameRepository;
import net.quimmilho.pokednd.api.domain.games.GameRepository.Game;
import net.quimmilho.pokednd.api.domain.games.GameState;
import net.quimmilho.pokednd.api.domain.trainers.TrainerRepository;
import net.quimmilho.pokednd.api.domain.trainers.TrainerRepository.StoredTrainer;
import net.quimmilho.pokednd.api.games.GameService;
import org.jboss.logging.Logger;

/**
 * Turns what happened in a game ({@link GameEvents}) into messages for the apps that listen to it and for the server's mod. The observers
 * run after the transaction has been committed, so nobody is told about a change that was rolled back, and a problem here never
 * undoes the change itself.
 *
 * <p>Who gets what: everyone who listens to a game hears that its state or settings changed; only the admins hear the join code and who
 * asks to join; a person always hears the answer to their own request, even before they listen to the game.
 */
@ApplicationScoped
public class RealtimeEvents {

    private static final Logger LOG = Logger.getLogger(RealtimeEvents.class);

    @Inject
    ObjectMapper json;

    @Inject
    RealtimeHub hub;

    @Inject
    ModCommands mod;

    @Inject
    GameService games;

    @Inject
    TrainerRepository trainers;

    void onState(@Observes(during = TransactionPhase.AFTER_SUCCESS) GameEvents.StateChanged e) {
        try {
            Game g = e.game();
            // the apps: everybody hears the state; only admins hear the join code
            ObjectNode plain = json.createObjectNode();
            plain.put("state", g.state().id());
            plain.put("previous", e.from().id());
            plain.put("name", g.name());
            hub.sendToGame(g.id(), s -> !games.runs(g, s.userId, s.siteAdmin), "game.state", plain);
            ObjectNode admin = plain.deepCopy();
            if (g.joinCode() != null) {
                admin.put("joinCode", g.joinCode());
            }
            hub.sendToGame(g.id(), s -> games.runs(g, s.userId, s.siteAdmin), "game.state", admin);
            // the mod: the state, the join code, and the fake players of a game that has just become active
            mod.gameState(g);
            mod.lobby(g);
            if (g.state().active() && !e.from().active()) {
                for (StoredTrainer t : trainers.ofGame(g.id())) {
                    mod.spawn(g, t);
                }
            }
        } catch (RuntimeException ex) {
            LOG.error("Could not announce a change of game state", ex);
        }
    }

    void onMembership(@Observes(during = TransactionPhase.AFTER_SUCCESS) GameEvents.MembershipChanged e) {
        try {
            ObjectNode d = json.createObjectNode();
            d.put("membershipId", e.membershipId().toString());
            d.put("userId", e.userId().toString());
            d.put("role", e.role());
            d.put("status", e.status());
            Optional<Game> game = games.find(e.gameId());
            boolean asking = GameRepository.REQUESTED.equals(e.status());
            // the admins hear every change (a request is "membership.request", the rest "membership.update")
            game.ifPresent(g -> hub.sendToGame(g.id(), s -> games.runs(g, s.userId, s.siteAdmin), asking ? "membership.request" : "membership.update", d));
            // the person hears the answer to their own request, wherever they listen
            if (!asking) {
                hub.sendToUser(e.userId(), "membership.update", e.gameId(), d);
            }
            if (GameRepository.KICKED.equals(e.status()) || GameRepository.REJECTED.equals(e.status())) {
                hub.unsubscribeUser(e.userId(), e.gameId());
            }
        } catch (RuntimeException ex) {
            LOG.error("Could not announce a membership change", ex);
        }
    }

    void onSettings(@Observes(during = TransactionPhase.AFTER_SUCCESS) GameEvents.SettingsChanged e) {
        try {
            games.find(e.gameId()).ifPresent(g -> {
                ObjectNode d = json.createObjectNode();
                d.put("version", g.settingsVersion());
                hub.sendToGame(g.id(), s -> true, "game.settings", d);
            });
        } catch (RuntimeException ex) {
            LOG.error("Could not announce a settings change", ex);
        }
    }

    void onTrainer(@Observes(during = TransactionPhase.AFTER_SUCCESS) GameEvents.TrainerChanged e) {
        try {
            // the apps only hear that something changed; they ask for the sheet (the private parts stay behind the REST permissions)
            ObjectNode d = json.createObjectNode();
            d.put("trainerId", e.trainerId().toString());
            d.put("change", e.change().name().toLowerCase(java.util.Locale.ROOT));
            hub.sendToGame(e.gameId(), s -> true, "trainer.update", d);

            Optional<Game> game = games.find(e.gameId());
            if (game.isEmpty() || !game.get().state().active() || game.get().state() == GameState.ARCHIVED) {
                return;
            }
            Game g = game.get();
            switch (e.change()) {
                case REMOVED -> mod.remove(g, e.trainerId());
                case CREATED -> trainers.find(e.trainerId()).ifPresent(t -> mod.spawn(g, t));
                case UPDATED -> trainers.find(e.trainerId()).ifPresent(t -> mod.update(g, t));
                case SKIN -> trainers.find(e.trainerId()).filter(StoredTrainer::hasSkin).ifPresent(t -> mod.skin(g, t));
            }
        } catch (RuntimeException ex) {
            LOG.error("Could not announce a trainer change", ex);
        }
    }
}
