/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api;

import io.quarkus.test.common.QuarkusTestResource;

/** Registers the resources that every API test starts with (see {@link TestKeys}). */
@QuarkusTestResource(TestKeys.class)
public class TestResources {
}
