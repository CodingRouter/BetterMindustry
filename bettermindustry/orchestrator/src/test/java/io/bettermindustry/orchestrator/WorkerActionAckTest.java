package io.bettermindustry.orchestrator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerActionAckTest {
    @Test
    void parsesAppliedAcknowledgement() {
        WorkerActionAck ack = WorkerActionAck.parse(
            "{\"protocolVersion\":1,\"commandId\":\"command-1\","
                + "\"status\":\"APPLIED\",\"tick\":123.5,\"paused\":true,"
                + "\"detail\":\"Game paused.\"}"
        );

        assertEquals("command-1", ack.commandId());
        assertEquals(WorkerActionAck.Status.APPLIED, ack.status());
        assertEquals(123.5, ack.tick());
        assertTrue(ack.paused());
        assertEquals("Game paused.", ack.detail());
    }

    @Test
    void rejectsUnsupportedOrMalformedAcknowledgement() {
        assertThrows(IllegalArgumentException.class, () ->
            WorkerActionAck.parse("{\"protocolVersion\":2}"));
        assertThrows(IllegalArgumentException.class, () ->
            WorkerActionAck.parse("not-json"));
    }

    @Test
    void parsesFailedAcknowledgement() {
        WorkerActionAck ack = WorkerActionAck.parse(
            "{\"protocolVersion\":1,\"commandId\":\"command-2\","
                + "\"status\":\"FAILED\",\"tick\":9,\"paused\":false,"
                + "\"detail\":\"Cannot pause without a game running.\"}"
        );

        assertEquals(WorkerActionAck.Status.FAILED, ack.status());
        assertEquals("Cannot pause without a game running.", ack.detail());
    }
}
