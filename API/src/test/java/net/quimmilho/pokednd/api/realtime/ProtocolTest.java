/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.quimmilho.pokednd.api.realtime.MessageBudget.Verdict;
import net.quimmilho.pokednd.api.realtime.Protocol.Message;
import net.quimmilho.pokednd.api.realtime.Protocol.ProtocolException;
import org.junit.jupiter.api.Test;

/** The envelope of chapter 18.3, the memory of seen ids, and the rate limit of a connection. No server is started. */
class ProtocolTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    // ---------------------------------------------------------------- the envelope

    @Test
    void aMessageIsReadWithItsFields() throws Exception {
        Message m = Protocol.parse(JSON, "{\"v\":1,\"id\":\"01JC0ZJ7Q8\",\"ref\":\"r1\",\"type\":\"roll.submit\",\"gameId\":\"g1\",\"ts\":\"2026-11-08T21:14:03Z\",\"data\":{\"a\":1}}");
        assertEquals(1, m.v());
        assertEquals("01JC0ZJ7Q8", m.id());
        assertEquals("r1", m.ref());
        assertEquals("roll.submit", m.type());
        assertEquals("g1", m.gameId());
        assertEquals(1, m.data().path("a").asInt());
    }

    @Test
    void aMessageWithoutDataGetsAnEmptyObject() throws Exception {
        Message m = Protocol.parse(JSON, "{\"v\":1,\"id\":\"a\",\"type\":\"ping\"}");
        assertTrue(m.data().isObject());
        assertEquals(0, m.data().size());
        assertNull(m.ref());
        assertNull(m.gameId());
    }

    @Test
    void badMessagesAreRefusedWithBadMessage() {
        String[] bad = {
                "not json", "[]", "\"text\"", "{}", "{\"v\":1,\"type\":\"x\"}", "{\"v\":1,\"id\":\"\",\"type\":\"x\"}", "{\"v\":1,\"id\":\"a\"}",
                "{\"v\":1,\"id\":\"a\",\"type\":7}", "{\"id\":\"a\",\"type\":\"x\"}", "{\"v\":\"1\",\"id\":\"a\",\"type\":\"x\"}",
                "{\"v\":1,\"id\":\"a\",\"type\":\"x\",\"data\":[1]}", "{\"v\":1,\"id\":\"" + "x".repeat(65) + "\",\"type\":\"x\"}",
        };
        for (String text : bad) {
            ProtocolException e = assertThrows(ProtocolException.class, () -> Protocol.parse(JSON, text), text);
            assertEquals("BAD_MESSAGE", e.code(), text);
            assertFalse(e.fatal(), text);
        }
    }

    @Test
    void aBadMessageWithAnIdPointsAtIt() {
        ProtocolException e = assertThrows(ProtocolException.class, () -> Protocol.parse(JSON, "{\"v\":1,\"id\":\"abc\"}"));
        assertEquals("abc", e.ref());
    }

    @Test
    void anUnknownVersionIsFatalAndSaysWhatToDo() {
        ProtocolException e = assertThrows(ProtocolException.class, () -> Protocol.parse(JSON, "{\"v\":2,\"id\":\"abc\",\"type\":\"hello\"}"));
        assertEquals("UNSUPPORTED_VERSION", e.code());
        assertTrue(e.fatal());
        assertTrue(e.getMessage().contains("version 1") && e.getMessage().contains("version 2") && e.getMessage().contains("Update"), e.getMessage());
        assertEquals("abc", e.ref());
    }

    @Test
    void anEnvelopeHasTheFieldsOfChapter183() {
        JsonNode m = Protocol.envelope(JSON, "lobby.open", "r9", "g1", JSON.createObjectNode().put("code", "XK7Q2M"));
        assertEquals(1, m.path("v").asInt());
        assertTrue(m.path("id").asText().matches("[0-9A-HJKMNP-TV-Z]{26}"), m.path("id").asText());
        assertEquals("r9", m.path("ref").asText());
        assertEquals("lobby.open", m.path("type").asText());
        assertEquals("g1", m.path("gameId").asText());
        assertTrue(m.path("ts").asText().matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ"), m.path("ts").asText());
        assertEquals("XK7Q2M", m.path("data").path("code").asText());
        JsonNode plain = Protocol.envelope(JSON, "pong", null, null, null);
        assertTrue(plain.path("ref").isNull());
        assertFalse(plain.has("gameId"));
        assertTrue(plain.path("data").isObject());
    }

    @Test
    void anErrorCarriesACodeAndAMessageAndPointsAtTheMessage() {
        JsonNode e = Protocol.error(JSON, "m1", null, "UNKNOWN_TYPE", "Nope.");
        assertEquals("error", e.path("type").asText());
        assertEquals("m1", e.path("ref").asText());
        assertEquals("UNKNOWN_TYPE", e.path("data").path("code").asText());
        assertEquals("Nope.", e.path("data").path("message").asText());
    }

    @Test
    void idsAreUniqueAndSortByTime() throws Exception {
        String a = Protocol.ulid();
        Thread.sleep(3);
        String b = Protocol.ulid();
        assertNotEquals(a, b);
        assertTrue(a.compareTo(b) < 0, "a later id sorts after an earlier one");
        assertEquals(26, a.length());
    }

    // ---------------------------------------------------------------- repeated ids

    @Test
    void aSeenIdIsADuplicateAndKeepsItsAnswer() {
        Dedupe d = new Dedupe(10);
        assertFalse(d.check("a").duplicate());
        d.answered("a", "{\"type\":\"ack\"}");
        Dedupe.Verdict again = d.check("a");
        assertTrue(again.duplicate());
        assertEquals("{\"type\":\"ack\"}", again.earlierAnswer());
        assertTrue(d.check("a").duplicate(), "and every time after that");
        assertFalse(d.check("b").duplicate());
        assertNull(d.check("b").earlierAnswer(), "no answer was given to b");
    }

    @Test
    void onlyTheLastIdsAreRemembered() {
        Dedupe d = new Dedupe(3);
        for (String id : new String[] {"1", "2", "3", "4"}) {
            d.check(id);
        }
        assertEquals(3, d.size());
        assertFalse(d.check("1").duplicate(), "the oldest id was forgotten");
        assertTrue(d.check("4").duplicate());
    }

    // ---------------------------------------------------------------- the rate limit

    @Test
    void aBudgetAllowsNMessagesPerSecondThenRefusesThenCutsOff() {
        MessageBudget b = new MessageBudget(5);
        for (int i = 0; i < 5; i++) {
            assertEquals(Verdict.OK, b.take(1000 + i));
        }
        for (int i = 0; i < 5; i++) {
            assertEquals(Verdict.REFUSE, b.take(1100 + i));
        }
        assertEquals(Verdict.CLOSE, b.take(1200), "more than twice the limit in one second");
        assertEquals(Verdict.OK, b.take(2100), "a new second starts with a new budget");
    }
}
