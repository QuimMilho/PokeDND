/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.realtime;

/**
 * The rate limit of one connection (chapter 14.6, "WebSocket messages"): at most {@code perSecond} messages in each second. A message over
 * the limit is refused with an error; a peer that sends more than twice the limit in a second is cut off.
 */
public final class MessageBudget {

    /** What to do with the message. */
    public enum Verdict {
        OK, REFUSE, CLOSE
    }

    private final int perSecond;
    private long windowStart = Long.MIN_VALUE;
    private int count;

    public MessageBudget(int perSecond) {
        this.perSecond = perSecond;
    }

    public synchronized Verdict take(long nowMillis) {
        if (windowStart == Long.MIN_VALUE || nowMillis - windowStart >= 1000) {
            windowStart = nowMillis;
            count = 0;
        }
        count++;
        if (count <= perSecond) {
            return Verdict.OK;
        }
        return count > perSecond * 2 ? Verdict.CLOSE : Verdict.REFUSE;
    }
}
