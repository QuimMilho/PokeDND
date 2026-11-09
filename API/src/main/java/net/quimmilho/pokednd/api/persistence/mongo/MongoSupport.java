/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.persistence.mongo;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.result.UpdateResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.bson.conversions.Bson;

/** Small helpers shared by the MongoDB repositories. */
final class MongoSupport {

    private MongoSupport() {
    }

    /** Did this write fail because a unique index refused it? */
    static boolean isDuplicate(RuntimeException e) {
        return e instanceof MongoWriteException w && w.getError().getCategory() == ErrorCategory.DUPLICATE_KEY;
    }

    /** The text of a failed write, to see which index refused it. */
    static String text(RuntimeException e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            sb.append(t.getMessage()).append(' ');
            if (t instanceof MongoWriteException w) {
                sb.append(w.getError().getMessage()).append(' ');
            }
        }
        return sb.toString();
    }

    static boolean changedOne(UpdateResult r) {
        return r.getMatchedCount() == 1;
    }

    /** Oldest first; a tie on the time (MongoDB keeps milliseconds) is broken by the id, which grows with time. */
    static Bson oldestFirst() {
        return Sorts.ascending("createdAt", "_id");
    }

    static Bson newestFirst() {
        return Sorts.descending("createdAt", "_id");
    }

    static Instant now() {
        return Instant.now();
    }

    static <T> List<T> list(Iterable<T> found) {
        java.util.ArrayList<T> out = new java.util.ArrayList<>();
        found.forEach(out::add);
        return out;
    }

    static UUID uuid(String s) {
        return UUID.fromString(s);
    }
}
