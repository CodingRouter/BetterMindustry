package io.bettermindustry.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InputEnvelopeTest {
    @Test
    void parsesAndRoundTripsEscapedChatMessage() {
        String json = """
            {
              "protocolVersion": 1,
              "commandId": "4cce4a55-08cb-4b05-bc28-c7b486612b49",
              "sectorId": "sector-a",
              "expectedTick": 12345,
              "deadlineTick": 12465,
              "type": "chat",
              "message": "hello\\n  world\\t!"
            }
            """;

        InputEnvelope envelope = InputEnvelope.parse(json);

        assertEquals(InputEnvelope.VERSION, envelope.protocolVersion());
        assertEquals("hello world !", envelope.message());
        assertEquals("hello world !", InputEnvelope.parse(envelope.toJson()).message());
    }

    @Test
    void stripsControlCharactersAndCompressesChatWhitespace() {
        InputEnvelope envelope = new InputEnvelope(
            1,
            "command-1",
            "sector-a",
            10,
            20,
            InputCommandType.CHAT,
            "  one\n\n two\u0000 three  "
        );

        assertEquals("one two three", envelope.message());
    }

    @Test
    void limitsChatByUnicodeCodePoint() {
        String maximum = "\uD83D\uDE3A".repeat(InputEnvelope.MAX_CHAT_CODE_POINTS);
        assertEquals(maximum, new InputEnvelope(
            1,
            "command-2",
            "sector-a",
            10,
            20,
            InputCommandType.CHAT,
            maximum
        ).message());

        assertThrows(IllegalArgumentException.class, () -> new InputEnvelope(
            1,
            "command-3",
            "sector-a",
            10,
            20,
            InputCommandType.CHAT,
            maximum + "\uD83D\uDE3A"
        ));
    }

    @Test
    void ignoresMessageForNonChatCommands() {
        InputEnvelope envelope = new InputEnvelope(
            1,
            "command-4",
            "sector-a",
            10,
            20,
            InputCommandType.PAUSE,
            "unexpected"
        );

        assertEquals("", envelope.message());
    }

    @Test
    void rejectsMalformedEnvelopeFields() {
        assertThrows(IllegalArgumentException.class, () -> InputEnvelope.parse("{}"));
        assertThrows(IllegalArgumentException.class, () -> new InputEnvelope(
            2,
            "command-5",
            "sector-a",
            10,
            20,
            InputCommandType.STATUS,
            ""
        ));
        assertThrows(IllegalArgumentException.class, () -> new InputEnvelope(
            1,
            "bad command id",
            "sector-a",
            10,
            20,
            InputCommandType.STATUS,
            ""
        ));
        assertThrows(IllegalArgumentException.class, () -> new InputEnvelope(
            1,
            "command-6",
            "sector-a",
            20,
            10,
            InputCommandType.STATUS,
            ""
        ));
    }
}
