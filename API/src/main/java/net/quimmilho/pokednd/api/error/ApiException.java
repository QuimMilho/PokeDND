/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.error;

import java.util.Map;

/** An error the caller can understand: it becomes the single error shape of chapter 18 ({@code code}, {@code message}, {@code details}). */
public class ApiException extends RuntimeException {

    private final int status;
    private final String code;
    private final transient Map<String, Object> details;

    public ApiException(int status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public ApiException(int status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }

    public static ApiException notFound(String what) {
        return new ApiException(404, "NOT_FOUND", what + " was not found.");
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(400, code, message);
    }

    public static ApiException unauthenticated() {
        return new ApiException(401, "UNAUTHENTICATED", "Log in first.");
    }

    public static ApiException forbidden() {
        return new ApiException(403, "FORBIDDEN", "You are not allowed to do this.");
    }
}
