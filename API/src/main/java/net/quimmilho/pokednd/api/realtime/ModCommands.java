/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Set;
import java.util.UUID;
import net.quimmilho.pokednd.api.domain.games.GameRepository.Game;
import net.quimmilho.pokednd.api.domain.trainers.TrainerRepository;
import net.quimmilho.pokednd.api.domain.trainers.TrainerRepository.StoredTrainer;

/**
 * The commands the API sends to the mod, as chapter 18.5 lists them. Each method builds one message and hands it to the hub; none of them
 * keeps anything for a mod that is offline, because a mod that connects again is brought up to date by {@link #catchUp}.
 */
@ApplicationScoped
public class ModCommands {

    @Inject
    RealtimeHub hub;

    @Inject
    ObjectMapper json;

    @Inject
    TrainerRepository trainers;

    /** {@code game.state}: the game is in this state. */
    public boolean gameState(Game game) {
        ObjectNode d = json.createObjectNode();
        d.put("state", game.state().id());
        d.put("name", game.name());
        return hub.sendToMod(game.serverId(), "game.state", game.id(), d);
    }

    /** {@code lobby.open} when the game's lobby is open (with the join code), {@code lobby.close} otherwise. */
    public boolean lobby(Game game) {
        if (game.state().lobbyOpen() && game.joinCode() != null) {
            ObjectNode d = json.createObjectNode();
            d.put("code", game.joinCode());
            return hub.sendToMod(game.serverId(), "lobby.open", game.id(), d);
        }
        return hub.sendToMod(game.serverId(), "lobby.close", game.id(), json.createObjectNode());
    }

    /**
     * {@code trainer.spawn}: make the fake player of a trainer. No position is sent: the mod places it at the game's spawn point (the
     * positions are known to the world configuration, chapter 15). The skin, if the trainer has one, is described so that the mod can fetch it.
     */
    public boolean spawn(Game game, StoredTrainer t) {
        ObjectNode d = json.createObjectNode();
        d.put("trainerId", t.id().toString());
        d.put("name", t.name());
        d.put("userId", t.userId().toString());
        if (t.hasSkin()) {
            d.set("skin", skin(t));
        }
        return hub.sendToMod(game.serverId(), "trainer.spawn", game.id(), d);
    }

    public boolean remove(Game game, UUID trainerId) {
        ObjectNode d = json.createObjectNode();
        d.put("trainerId", trainerId.toString());
        return hub.sendToMod(game.serverId(), "trainer.remove", game.id(), d);
    }

    /** {@code trainer.update}: the name of the fake player changed. */
    public boolean update(Game game, StoredTrainer t) {
        ObjectNode d = json.createObjectNode();
        d.put("trainerId", t.id().toString());
        d.put("name", t.name());
        return hub.sendToMod(game.serverId(), "trainer.update", game.id(), d);
    }

    /** {@code skin.apply}: the trainer has a new skin. */
    public boolean skin(Game game, StoredTrainer t) {
        ObjectNode d = skin(t);
        d.put("trainerId", t.id().toString());
        return hub.sendToMod(game.serverId(), "skin.apply", game.id(), d);
    }

    /** {@code resync.request}: the mod must send everything again (hello, manifest, the fake players). */
    public boolean resync(UUID serverId) {
        return hub.sendToMod(serverId, "resync.request", null, json.createObjectNode());
    }

    private ObjectNode skin(StoredTrainer t) {
        ObjectNode s = json.createObjectNode();
        s.put("url", "/api/v1/trainers/" + t.id() + "/skin");
        s.put("model", t.skinModel());
        s.put("sha256", t.skinSha256());
        return s;
    }

    /**
     * Brings a mod that just said hello up to date: the state of the game, the join code if the lobby is open, and a fake player for every
     * trainer the mod does not have. The hello lists the fake players the mod has (those it keeps while the connection is down), so a
     * short drop re-creates nothing, and a server that restarted gets all of them again (chapter 14.7).
     */
    public void catchUp(Game game, Set<String> modHas) {
        gameState(game);
        lobby(game);
        if (game.state().active()) {
            for (StoredTrainer t : trainers.ofGame(game.id())) {
                if (!modHas.contains(t.id().toString())) {
                    spawn(game, t);
                }
            }
        }
    }
}
