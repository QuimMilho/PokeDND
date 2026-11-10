/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.config.ConfigProvider;

/** A small WebSocket client for the tests: it keeps what it receives and waits for a message of a type. */
final class WsClient implements WebSocket.Listener {

    static final ObjectMapper JSON = new ObjectMapper();
    static final long WAIT_MS = 10_000;

    /** Raised when the server refuses the upgrade; {@code status} is the HTTP status it answered with. */
    static final class Refused extends RuntimeException {
        final int status;

        Refused(int status) {
            super("The upgrade was refused with HTTP " + status);
            this.status = status;
        }
    }

    private final BlockingQueue<JsonNode> queue = new LinkedBlockingQueue<>();
    /** Everything received, in order (also what {@link #waitFor} skipped). */
    final List<JsonNode> all = new ArrayList<>();
    private final StringBuilder partial = new StringBuilder();
    private final CountDownLatch closedLatch = new CountDownLatch(1);
    volatile int closeCode = -1;
    volatile String closeReason;
    private WebSocket socket;

    static int port() {
        return ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
    }

    static String url(String path) {
        return "ws://localhost:" + port() + path;
    }

    /** Connects to {@code /ws/mod} or {@code /ws/app}; {@code authorization} is the whole header value, or null for none. */
    static WsClient connect(String path, String authorization) {
        WsClient client = new WsClient();
        WebSocket.Builder builder = HttpClient.newHttpClient().newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5));
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        try {
            client.socket = builder.buildAsync(URI.create(url(path)), client).get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof WebSocketHandshakeException h) {
                throw new Refused(h.getResponse().statusCode());
            }
            throw new IllegalStateException(e);
        } catch (InterruptedException | java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException(e);
        }
        return client;
    }

    // ------------------------------------------------------------------ receiving

    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        partial.append(data);
        if (last) {
            try {
                JsonNode n = JSON.readTree(partial.toString());
                synchronized (all) {
                    all.add(n);
                }
                queue.add(n);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            partial.setLength(0);
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        closeCode = statusCode;
        closeReason = reason;
        closedLatch.countDown();
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        closedLatch.countDown();
    }

    /** The next message of this type; the ones before it are skipped (they stay in {@link #all}). Fails after ten seconds. */
    JsonNode waitFor(String type) {
        return waitFor(type, WAIT_MS);
    }

    JsonNode waitFor(String type, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (true) {
            long left = end - System.currentTimeMillis();
            if (left <= 0) {
                throw new AssertionError("No \"" + type + "\" arrived within " + timeoutMs + " ms. Received: " + types());
            }
            try {
                JsonNode n = queue.poll(left, TimeUnit.MILLISECONDS);
                if (n != null && type.equals(n.path("type").asText())) {
                    return n;
                }
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** Waits for a message of this type that satisfies the test (others are skipped). */
    JsonNode waitFor(String type, java.util.function.Predicate<JsonNode> test) {
        long end = System.currentTimeMillis() + WAIT_MS;
        while (true) {
            JsonNode n = waitFor(type, Math.max(1, end - System.currentTimeMillis()));
            if (test.test(n)) {
                return n;
            }
        }
    }

    /** Asserts that nothing arrives within the time (messages already skipped by an earlier wait do not count). */
    void expectNothing(long millis) {
        try {
            JsonNode n = queue.poll(millis, TimeUnit.MILLISECONDS);
            if (n != null) {
                throw new AssertionError("Expected no message but received: " + n);
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Throws away what has arrived so far. */
    void clear() {
        queue.clear();
    }

    List<String> types() {
        synchronized (all) {
            return all.stream().map(n -> n.path("type").asText()).toList();
        }
    }

    /** Waits until the server has closed the connection; returns the close code. */
    int awaitClose() {
        try {
            if (!closedLatch.await(WAIT_MS, TimeUnit.MILLISECONDS)) {
                throw new AssertionError("The connection was not closed. Received: " + types());
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        return closeCode;
    }

    boolean isClosed() {
        return closedLatch.getCount() == 0;
    }

    // ------------------------------------------------------------------ sending

    /** Sends a message in the envelope and returns its id. */
    String send(String type, Object... data) {
        return send(1, type, null, data);
    }

    String send(int version, String type, String gameId, Object... data) {
        ObjectNode m = JSON.createObjectNode();
        String id = Protocol.ulid();
        m.put("v", version);
        m.put("id", id);
        m.putNull("ref");
        m.put("type", type);
        if (gameId != null) {
            m.put("gameId", gameId);
        }
        ObjectNode d = m.putObject("data");
        for (int i = 0; i < data.length; i += 2) {
            d.set((String) data[i], JSON.valueToTree(data[i + 1]));
        }
        sendRaw(m.toString());
        return id;
    }

    /** Sends the same id again (a repeat of a message). */
    void sendWithId(String id, String type, Object... data) {
        ObjectNode m = JSON.createObjectNode();
        m.put("v", 1);
        m.put("id", id);
        m.putNull("ref");
        m.put("type", type);
        ObjectNode d = m.putObject("data");
        for (int i = 0; i < data.length; i += 2) {
            d.set((String) data[i], JSON.valueToTree(data[i + 1]));
        }
        sendRaw(m.toString());
    }

    void sendRaw(String text) {
        try {
            socket.sendText(text, true).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    void close() {
        try {
            if (!isClosed()) {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get(2, TimeUnit.SECONDS);
            }
        } catch (Exception ignored) {
            // already closed
        }
    }
}
