package io.bettermindustry.desktop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class GatewayClientTest {
    @Test
    void parsesSessionIdentity() {
        GatewayClient.SessionInfo session = GatewayClient.parseSession(
            "{\"clientId\":\"client-1\",\"username\":\"captain\","
                + "\"role\":\"PLAYER\",\"expiresAtMillis\":12345}"
        );

        assertEquals("client-1", session.clientId());
        assertEquals("captain", session.username());
        assertEquals("PLAYER", session.role());
        assertEquals(12345, session.expiresAtMillis());
    }

    @Test
    void parsesSimultaneousSectorFrames() {
        GatewayClient.Snapshot snapshot = GatewayClient.parseSnapshot("""
            {
              "clientId":"client-1",
              "focusSectorId":"sector-a",
              "controlOwnerId":null,
              "sectors":[
                {"id":"sector-a","map":"Wasteland","port":16567,"state":"RUNNING","alive":true,"portOpen":true,
                 "gameTick":120.5,"gameTimeSeconds":2.0,"paused":false,"players":0,"units":1,
                 "enemies":0,"buildings":3,"viewMode":"FULL","worldDataAvailable":true,
                 "worldSnapshotVersion":111},
                {"id":"sector-b","map":"Veins","port":16568,"state":"RUNNING","alive":true,"portOpen":true,
                 "gameTick":220.5,"gameTimeSeconds":3.6,"paused":true,"players":1,"units":2,
                 "enemies":4,"buildings":5,"viewMode":"SUMMARY","worldDataAvailable":false,
                 "worldSnapshotVersion":-1}
              ]
            }
            """);

        assertEquals("client-1", snapshot.clientId());
        assertEquals("sector-a", snapshot.focusSectorId());
        assertNull(snapshot.controlOwnerId());
        assertEquals(2, snapshot.sectors().size());
        assertEquals("Wasteland", snapshot.sectors().get(0).map());
        assertEquals(16567, snapshot.sectors().get(0).port());
        assertEquals(220.5, snapshot.sectors().get(1).gameTick());
        assertEquals("SUMMARY", snapshot.sectors().get(1).viewMode());
        assertFalse(snapshot.sectors().get(1).worldDataAvailable());
    }

    @Test
    void unescapesSessionFields() {
        GatewayClient.SessionInfo session = GatewayClient.parseSession(
            "{\"clientId\":\"client-2\",\"username\":\"line\\nname\","
                + "\"role\":\"GUEST\",\"expiresAtMillis\":1}"
        );

        assertEquals("line\nname", session.username());
    }

    @Test
    void capturesSessionCookiePairAndExpiry() {
        assertEquals(
            "bm_session=token-value",
            GatewayClient.sessionCookie(
                "bm_session=token-value; Path=/; HttpOnly; SameSite=Strict; Max-Age=604800"
            )
        );
        assertEquals("", GatewayClient.sessionCookie(
            "bm_session=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0"
        ));
        assertNull(GatewayClient.sessionCookie("other=value; Path=/"));
    }
}
