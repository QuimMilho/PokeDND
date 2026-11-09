/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.reference;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.quarkus.test.junit.QuarkusTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The picture endpoint that the app's card preview uses. Needs {@code resources/} (skipped without it). */
@QuarkusTest
@Tag("database")
class ArtEndpointTest {

    @ConfigProperty(name = "pokednd.resources.dir")
    String resourcesDir;

    @Test
    void servesThePngOfACardWithoutLogin() throws Exception {
        Path file = Path.of(resourcesDir, "card-art", "0001_bulbasaur", "bulbasaur.png");
        assumeTrue(Files.isRegularFile(file), "resources/ is not built, so the art test is skipped");
        byte[] body = given().get("/api/v1/art/0001_bulbasaur/bulbasaur.png").then().statusCode(200).contentType("image/png")
                .header("Cache-Control", containsString("max-age=86400")).extract().asByteArray();
        assertEquals(Files.size(file), body.length);
        // the PNG signature
        assertEquals((byte) 0x89, body[0]);
        assertEquals('P', body[1]);
    }

    @Test
    void theIconSizeGivesTheSmallPicture() throws Exception {
        Path icon = Path.of(resourcesDir, "icons", "0001_bulbasaur", "bulbasaur.png");
        assumeTrue(Files.isRegularFile(icon), "resources/ is not built, so the art test is skipped");
        byte[] body = given().get("/api/v1/art/0001_bulbasaur/bulbasaur.png?size=icon").then().statusCode(200).extract().asByteArray();
        assertEquals(Files.size(icon), body.length);
    }

    @Test
    void anUnknownPictureIsA404AndPathTricksAreRefused() {
        given().get("/api/v1/art/0001_bulbasaur/missingno.png").then().statusCode(404).body("code", equalTo("NOT_FOUND"));
        given().get("/api/v1/art/9999_nobody/nobody.png").then().statusCode(404);
        given().get("/api/v1/art/..%2F..%2Fresources/x.png").then().statusCode(404);
        given().get("/api/v1/art/0001_bulbasaur/..%2F..%2FATTRIBUTION.md").then().statusCode(404);
        given().get("/api/v1/art/0001_bulbasaur/bulbasaur.txt").then().statusCode(404);
    }
}
