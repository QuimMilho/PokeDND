/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class CoreVersionTest {

    @Test
    void versionIsNotBlank() {
        assertFalse(CoreVersion.get().isBlank());
    }

    @Test
    void versionMatchesGradleProperties() {
        // Gradle passes the real value in, so the constant cannot drift from gradle.properties.
        assertEquals(System.getProperty("core.version"), CoreVersion.get());
    }
}
