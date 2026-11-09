/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import net.quimmilho.pokednd.api.error.ApiException;

/**
 * A small in-memory limiter: at most N events per key within a window (chapter 14.6). It lives in one API process, which is
 * what a single-server installation has.
 */
@ApplicationScoped
public class RateLimiter {

    private final Map<String, Deque<Long>> events = new ConcurrentHashMap<>();
    private volatile LongSupplier clock = System::currentTimeMillis;

    /** For tests: replaces the clock (milliseconds). */
    void setClock(LongSupplier clock) {
        this.clock = clock;
    }

    /** Forgets everything (tests). */
    public void reset() {
        events.clear();
        clock = System::currentTimeMillis;
    }

    /**
     * Counts one event and refuses it when the key already had {@code max} events within the window.
     *
     * @throws ApiException 429 RATE_LIMITED
     */
    public void hit(String bucket, String key, int max, Duration window) {
        long now = clock.getAsLong();
        Deque<Long> q = events.computeIfAbsent(bucket + "|" + key, k -> new ArrayDeque<>());
        synchronized (q) {
            trim(q, now, window);
            if (q.size() >= max) {
                throw limited(q, now, window);
            }
            q.addLast(now);
        }
    }

    /** Refuses when the key already has {@code max} events within the window, without counting a new one. */
    public void check(String bucket, String key, int max, Duration window) {
        long now = clock.getAsLong();
        Deque<Long> q = events.get(bucket + "|" + key);
        if (q == null) {
            return;
        }
        synchronized (q) {
            trim(q, now, window);
            if (q.size() >= max) {
                throw limited(q, now, window);
            }
        }
    }

    /** Counts one event without checking. */
    public void record(String bucket, String key, Duration window) {
        long now = clock.getAsLong();
        Deque<Long> q = events.computeIfAbsent(bucket + "|" + key, k -> new ArrayDeque<>());
        synchronized (q) {
            trim(q, now, window);
            q.addLast(now);
        }
    }

    /** Forgets the events of one key. */
    public void clear(String bucket, String key) {
        events.remove(bucket + "|" + key);
    }

    private static void trim(Deque<Long> q, long now, Duration window) {
        while (!q.isEmpty() && now - q.peekFirst() >= window.toMillis()) {
            q.removeFirst();
        }
    }

    private static ApiException limited(Deque<Long> q, long now, Duration window) {
        long retry = Math.max(1, (window.toMillis() - (now - q.peekFirst()) + 999) / 1000);
        return new ApiException(429, "RATE_LIMITED", "Too many attempts. Try again in " + retry + " seconds.", Map.of("retryAfterSeconds", retry));
    }
}
