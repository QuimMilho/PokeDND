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
 * {@code wss://…/ws/app}: the app (chapter 18.2). Anyone can open the connection; nothing is said to it until its first message,
 * {@code auth}, has proved who it is. Everything else is in {@link AppProtocol}.
 */
@WebSocket(path = "/ws/app", endpointId = AppSocket.ENDPOINT)
public class AppSocket {

    public static final String ENDPOINT = "app";

    @Inject
    AppProtocol protocol;

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
