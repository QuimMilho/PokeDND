/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import net.quimmilho.pokednd.api.error.ApiException;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    @Test
    void allowsNThenRefusesThenAllowsAgainWhenTheWindowPasses() {
        RateLimiter limiter = new RateLimiter();
        AtomicLong now = new AtomicLong(1_000_000);
        limiter.setClock(now::get);
        Duration minute = Duration.ofMinutes(1);
        for (int i = 0; i < 3; i++) {
            limiter.hit("b", "k", 3, minute);
        }
        ApiException e = assertThrows(ApiException.class, () -> limiter.hit("b", "k", 3, minute));
        assertEquals(429, e.status());
        assertEquals("RATE_LIMITED", e.code());
        // another key has its own count
        assertDoesNotThrow(() -> limiter.hit("b", "other", 3, minute));
        now.addAndGet(61_000);
        assertDoesNotThrow(() -> limiter.hit("b", "k", 3, minute));
    }

    @Test
    void recordedFailuresBlockUntilTheyAreClearedOrExpire() {
        RateLimiter limiter = new RateLimiter();
        Duration window = Duration.ofMinutes(10);
        for (int i = 0; i < 5; i++) {
            limiter.check("f", "a@b.c", 5, window);
            limiter.record("f", "a@b.c", window);
        }
        assertThrows(ApiException.class, () -> limiter.check("f", "a@b.c", 5, window));
        limiter.clear("f", "a@b.c");
        assertDoesNotThrow(() -> limiter.check("f", "a@b.c", 5, window));
    }
}
