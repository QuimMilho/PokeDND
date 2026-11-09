/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * The message envelope of chapter 18.3 and the numbers of the realtime protocol. The fake mod ({@code tools/fake-mod/protocol.mjs})
 * speaks the same version.
 */
public final class Protocol {

    /** The protocol version in the {@code v} of every message. A peer refuses versions it does not know. */
    public static final int VERSION = 1;
    /** The versions this API understands. */
    public static final Set<Integer> SUPPORTED = Set.of(1);
    /** Seconds between two heartbeats (18.2). */
    public static final int HEARTBEAT_SECONDS = 20;

    /** Close codes of our own (WebSocket allows 4000 to 4999). */
    public static final int CLOSE_UNAUTHENTICATED = 4001;
    public static final int CLOSE_UNSUPPORTED_VERSION = 4002;
    public static final int CLOSE_REPLACED = 4003;
    public static final int CLOSE_RATE_LIMITED = 4004;
    public static final int CLOSE_IDLE = 4005;

    public static final int ID_MAX = 64;
    public static final int TYPE_MAX = 64;

    private static final char[] CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Protocol() {
    }

    /**
     * A message that was read.
     *
     * @param ref    the id this message answers; null if none
     * @param gameId the game it is about; null if none
     */
    public record Message(int v, String id, String ref, String type, String gameId, JsonNode data) {
    }

    /** A message that cannot be accepted. {@code fatal} means the connection must end after the error is sent. */
    public static final class ProtocolException extends Exception {
        private final String code;
        private final boolean fatal;
        private final String ref;

        public ProtocolException(String code, String message, boolean fatal, String ref) {
            super(message);
            this.code = code;
            this.fatal = fatal;
            this.ref = ref;
        }

        public String code() {
            return code;
        }

        public boolean fatal() {
            return fatal;
        }

        /** The id of the bad message, when it had one. */
        public String ref() {
            return ref;
        }
    }

    /** Reads one text message. @throws ProtocolException BAD_MESSAGE, or UNSUPPORTED_VERSION (fatal) */
    public static Message parse(ObjectMapper json, String text) throws ProtocolException {
        JsonNode n;
        try {
            n = json.readTree(text);
        } catch (JsonProcessingException e) {
            throw bad("The message is not JSON.", null);
        }
        if (n == null || !n.isObject()) {
            throw bad("The message is not a JSON object.", null);
        }
        String id = n.path("id").isTextual() ? n.get("id").asText() : null;
        if (id == null || id.isEmpty() || id.length() > ID_MAX) {
            throw bad("The message needs an \"id\" of 1 to " + ID_MAX + " characters.", null);
        }
        if (!n.path("type").isTextual() || n.get("type").asText().isEmpty() || n.get("type").asText().length() > TYPE_MAX) {
            throw bad("The message needs a \"type\".", id);
        }
        if (!n.path("v").isInt()) {
            throw bad("The message needs the protocol version \"v\".", id);
        }
        int v = n.get("v").asInt();
        if (!SUPPORTED.contains(v)) {
            throw new ProtocolException("UNSUPPORTED_VERSION", "This API speaks protocol version " + VERSION + "; the message has version " + v
                    + ". Update the older side so both use the same version.", true, id);
        }
        JsonNode data = n.get("data");
        if (data != null && !data.isNull() && !data.isObject()) {
            throw bad("\"data\" must be an object.", id);
        }
        String ref = n.path("ref").isTextual() ? n.get("ref").asText() : null;
        String gameId = n.path("gameId").isTextual() ? n.get("gameId").asText() : null;
        return new Message(v, id, ref, n.get("type").asText(), gameId, data == null || data.isNull() ? json.createObjectNode() : data);
    }

    private static ProtocolException bad(String message, String ref) {
        return new ProtocolException("BAD_MESSAGE", message, false, ref);
    }

    /** Builds a message in the envelope of 18.3. */
    public static ObjectNode envelope(ObjectMapper json, String type, String ref, String gameId, JsonNode data) {
        ObjectNode m = json.createObjectNode();
        m.put("v", VERSION);
        m.put("id", ulid());
        if (ref == null) {
            m.putNull("ref");
        } else {
            m.put("ref", ref);
        }
        m.put("type", type);
        if (gameId != null) {
            m.put("gameId", gameId);
        }
        m.put("ts", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        m.set("data", data == null ? json.createObjectNode() : data);
        return m;
    }

    public static ObjectNode ack(ObjectMapper json, Message to, JsonNode data) {
        return envelope(json, "ack", to.id(), to.gameId(), data);
    }

    public static ObjectNode error(ObjectMapper json, String ref, String gameId, String code, String message) {
        ObjectNode d = json.createObjectNode();
        d.put("code", code);
        d.put("message", message);
        return envelope(json, "error", ref, gameId, d);
    }

    /** A new ULID: 10 characters of time and 16 of randomness; later ones sort after earlier ones. */
    public static String ulid() {
        char[] out = new char[26];
        long t = System.currentTimeMillis();
        for (int i = 9; i >= 0; i--) {
            out[i] = CROCKFORD[(int) (t % 32)];
            t /= 32;
        }
        for (int i = 10; i < 26; i++) {
            out[i] = CROCKFORD[RANDOM.nextInt(32)];
        }
        return new String(out);
    }
}
