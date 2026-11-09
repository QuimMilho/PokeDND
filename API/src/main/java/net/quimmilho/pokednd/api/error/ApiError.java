/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.error;

import java.util.Map;

/** The body of every error answer. */
public record ApiError(String code, String message, Map<String, Object> details) {
}
