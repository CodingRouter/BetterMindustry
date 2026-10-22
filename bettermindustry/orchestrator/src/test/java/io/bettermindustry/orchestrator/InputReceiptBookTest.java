package io.bettermindustry.orchestrator;

import io.bettermindustry.protocol.InputCommandType;
import io.bettermindustry.protocol.InputEnvelope;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputReceiptBookTest {
    @Test
    void returnsOriginalReceiptForDuplicateCommand() {
        InputReceiptBook book = new InputReceiptBook();
        InputEnvelope envelope = envelope("command-1", InputCommandType.PAUSE, "");
        book.begin("client-a", envelope, 1_000);
        book.complete("command-1", InputReceiptBook.Status.APPLIED, "on", 1_100);

        InputReceiptBook.Admission duplicate = book.begin("client-a", envelope, 1_200);

        assertTrue(duplicate.duplicate());
        assertEquals(InputReceiptBook.Status.APPLIED, duplicate.receipt().status());
        assertEquals("on", duplicate.receipt().detail());
        assertEquals(1_100, duplicate.receipt().updatedAtMillis());
    }

    @Test
    void rejectsReusingCommandIdForDifferentInput() {
        InputReceiptBook book = new InputReceiptBook();
        book.begin("client-a", envelope("command-2", InputCommandType.CHAT, "hello"), 1_000);

        assertThrows(InputReceiptBook.ConflictException.class, () -> book.find(
            "client-b",
            envelope("command-2", InputCommandType.CHAT, "hello"),
            1_100
        ));
        assertThrows(InputReceiptBook.ConflictException.class, () -> book.find(
            "client-a",
            envelope("command-2", InputCommandType.CHAT, "different"),
            1_100
        ));
    }

    @Test
    void persistsAppliedReceiptAcrossReload() {
        InputReceiptBook original = new InputReceiptBook();
        InputEnvelope envelope = envelope("command-3", InputCommandType.CHAT, "hello");
        original.begin("client-a", envelope, 1_000);
        original.complete("command-3", InputReceiptBook.Status.APPLIED, "hello", 1_100);

        Properties properties = new Properties();
        original.write(properties);
        InputReceiptBook restored = new InputReceiptBook();
        restored.load(properties, 2_000);
        InputReceiptBook.Admission duplicate = restored.find("client-a", envelope, 2_100);

        assertNotNull(duplicate);
        assertTrue(duplicate.duplicate());
        assertEquals(InputReceiptBook.Status.APPLIED, duplicate.receipt().status());
        assertEquals("hello", duplicate.receipt().detail());
    }

    @Test
    void convertsPersistedPendingReceiptToFailedAfterRestart() {
        InputReceiptBook original = new InputReceiptBook();
        InputEnvelope envelope = envelope("command-4", InputCommandType.RESUME, "");
        original.begin("client-a", envelope, 1_000);

        Properties properties = new Properties();
        original.write(properties);
        InputReceiptBook restored = new InputReceiptBook();
        restored.load(properties, 2_000);
        InputReceiptBook.Admission duplicate = restored.find("client-a", envelope, 2_100);

        assertNotNull(duplicate);
        assertEquals(InputReceiptBook.Status.FAILED, duplicate.receipt().status());
        assertEquals("orchestrator restarted before input completion", duplicate.receipt().detail());
    }

    @Test
    void expiresReceiptsAndKeepsBoundedHistory() {
        InputReceiptBook book = new InputReceiptBook();
        InputEnvelope first = envelope("expired-command", InputCommandType.STATUS, "");
        book.begin("client-a", first, 1_000);
        assertNull(book.find("client-a", first, 1_000 + InputReceiptBook.RETENTION_MILLIS + 1));

        for (int i = 0; i < InputReceiptBook.MAX_RECEIPTS + 1; i++) {
            book.begin("client-a", envelope("bounded-" + i, InputCommandType.STATUS, ""), 10_000);
        }

        assertNull(book.find("client-a", envelope("bounded-0", InputCommandType.STATUS, ""), 10_001));
        assertNotNull(book.find(
            "client-a",
            envelope("bounded-" + InputReceiptBook.MAX_RECEIPTS, InputCommandType.STATUS, ""),
            10_001
        ));
        assertTrue(book.begin(
            "client-a",
            envelope("bounded-" + InputReceiptBook.MAX_RECEIPTS, InputCommandType.STATUS, ""),
            10_002
        ).duplicate());
    }

    private static InputEnvelope envelope(String commandId, InputCommandType type, String message) {
        return new InputEnvelope(1, commandId, "sector-a", 10, 20, type, message);
    }
}
