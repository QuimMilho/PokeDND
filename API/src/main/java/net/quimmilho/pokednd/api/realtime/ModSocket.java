/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.realtime;

import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.inject.Inject;

/**
 * {@code wss://…/ws/mod}: the Minecraft mod (chapter 18.2). The server token goes in the {@code Authorization} header of the upgrade
 * request ({@link ModUpgradeCheck} refuses a bad one with 401). Everything else is in {@link ModProtocol}.
 */
@WebSocket(path = "/ws/mod", endpointId = ModSocket.ENDPOINT)
public class ModSocket {

    public static final String ENDPOINT = "mod";

    @Inject
    ModProtocol protocol;

    @OnOpen
    public void open(WebSocketConnection connection) {
        protocol.opened(connection);
    }

    @OnTextMessage
    public void message(WebSocketConnection connection, String text) {
        protocol.received(connection, text);
    }

    @OnClose
    public void close(WebSocketConnection connection) {
        protocol.closed(connection);
    }
}
