/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.IOException;
import java.util.Map;

/**
 * Starts the fake mail server and the fake Discord before the API starts in a test, and points the API at them. Tests reach them
 * through {@link #smtp()} and {@link #discord()}.
 */
@QuarkusTestResource(FakeServices.class)
public class FakeServices implements QuarkusTestResourceLifecycleManager {

    private static FakeSmtpServer smtp;
    private static FakeDiscord discord;

    public static FakeSmtpServer smtp() {
        return smtp;
    }

    public static FakeDiscord discord() {
        return discord;
    }

    @Override
    public Map<String, String> start() {
        try {
            smtp = new FakeSmtpServer();
            discord = new FakeDiscord();
        } catch (IOException e) {
            throw new IllegalStateException("cannot start the fake mail server and Discord", e);
        }
        return Map.of(
                "quarkus.mailer.host", "127.0.0.1",
                "quarkus.mailer.port", String.valueOf(smtp.port()),
                "pokednd.oauth.discord.client-id", FakeDiscord.CLIENT_ID,
                "pokednd.oauth.discord.client-secret", FakeDiscord.CLIENT_SECRET,
                "pokednd.oauth.discord.authorize-url", discord.authorizeUrl(),
                "pokednd.oauth.discord.token-url", discord.tokenUrl(),
                "pokednd.oauth.discord.api-url", discord.apiUrl());
    }

    @Override
    public void stop() {
        try {
            if (smtp != null) {
                smtp.close();
            }
        } catch (IOException ignored) {
            // shutting down
        }
        if (discord != null) {
            discord.close();
        }
    }
}
