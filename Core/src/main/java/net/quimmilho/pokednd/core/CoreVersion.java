/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core;

/**
 * Tells which version of the Core library is running.
 * Placeholder that proves the build, test and publish pipeline works; it is not game logic.
 */
public final class CoreVersion {

    /** Version of the Core library (keep in step with {@code core_version} in gradle.properties). */
    public static final String VERSION = "1.0-SNAPSHOT";

    private CoreVersion() {
    }

    /** Returns the version of the Core library. */
    public static String get() {
        return VERSION;
    }
}
