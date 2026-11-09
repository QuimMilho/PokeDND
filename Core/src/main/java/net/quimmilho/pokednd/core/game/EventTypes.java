/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The names of the event types and the conversion between an event and the two columns of the log: {@code type} and {@code payload}
 * (chapter 18, the {@code GameEvent} row). The payload is the JSON of the record's fields, so a log written today can be read later as
 * long as fields are only added.
 */
public final class EventTypes {

    public static final String STATUS_CHANGED = "game.status";
    public static final String ROUND_ADVANCED = "game.round";
    public static final String TRAINER_SET = "trainer.set";
    public static final String TRAINER_REMOVED = "trainer.removed";
    public static final String ROLL_REQUESTED = "roll.requested";
    public static final String ROLL_ANSWERED = "roll.answered";
    public static final String ROLL_CANCELLED = "roll.cancelled";
    public static final String ROLL_SUBMITTED = "roll.submitted";
    public static final String ROLL_REJECTED = "roll.rejected";
    public static final String ROLL_OVERRIDDEN = "roll.overridden";
    public static final String ROLL_EXPIRED = "roll.expired";
    public static final String RANDOM_DRAWN = "random.drawn";

    private static final Map<String, Class<? extends GameEvent>> CLASSES = new LinkedHashMap<>();

    static {
        CLASSES.put(STATUS_CHANGED, GameEvent.StatusChanged.class);
        CLASSES.put(ROUND_ADVANCED, GameEvent.RoundAdvanced.class);
        CLASSES.put(TRAINER_SET, GameEvent.TrainerSet.class);
        CLASSES.put(TRAINER_REMOVED, GameEvent.TrainerRemoved.class);
        CLASSES.put(ROLL_REQUESTED, GameEvent.RollRequested.class);
        CLASSES.put(ROLL_ANSWERED, GameEvent.RollAnswered.class);
        CLASSES.put(ROLL_CANCELLED, GameEvent.RollCancelled.class);
        CLASSES.put(ROLL_SUBMITTED, GameEvent.RollSubmitted.class);
        CLASSES.put(ROLL_REJECTED, GameEvent.RollRejected.class);
        CLASSES.put(ROLL_OVERRIDDEN, GameEvent.RollOverridden.class);
        CLASSES.put(ROLL_EXPIRED, GameEvent.RollExpired.class);
        CLASSES.put(RANDOM_DRAWN, GameEvent.RandomDrawn.class);
    }

    private EventTypes() {
    }

    /** Every type name that exists. */
    public static java.util.Set<String> all() {
        return java.util.Collections.unmodifiableSet(CLASSES.keySet());
    }

    /** The event as the JSON that goes in the {@code payload} column. */
    public static ObjectNode payload(GameEvent event) {
        return (ObjectNode) Json.MAPPER.valueToTree(event);
    }

    /**
     * The event of a stored {@code type} and {@code payload}.
     *
     * @throws InvalidEventException if the type is unknown (a log from a newer version) or the payload does not fit it
     */
    public static GameEvent read(String type, JsonNode payload) {
        Class<? extends GameEvent> cls = CLASSES.get(type);
        if (cls == null) {
            throw new InvalidEventException("The event type \"" + type + "\" is not known to this version");
        }
        try {
            return Json.MAPPER.treeToValue(payload, cls);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new InvalidEventException("The payload of a \"" + type + "\" event is not valid: " + e.getMessage());
        }
    }

    /** The shared JSON settings. */
    static final class Json {
        /** A log may have been written by a newer version that added fields: unknown fields are skipped, never an error. */
        static final ObjectMapper MAPPER = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        private Json() {
        }
    }
}
