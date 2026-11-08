/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import net.quimmilho.pokednd.core.CoreVersion;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CoreLinkTest {

    @Test
    void apiCallsTheCoreClass() {
        // The class comes from the Core jar in Maven local; the endpoint answers with its value.
        given().when().get("/api/core-version").then().statusCode(200).body(is(CoreVersion.get()));
    }
}
