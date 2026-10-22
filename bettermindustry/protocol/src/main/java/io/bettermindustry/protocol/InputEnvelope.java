package io.bettermindustry.protocol;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record InputEnvelope(
    int protocolVersion,
    String commandId,
    String sectorId,
    long expectedTick,
    long deadlineTick,
    InputCommandType type,
    String message
) {
    public static final int VERSION = 1;
    public static final int MAX_CHAT_CODE_POINTS = 150;
    private static final Pattern FIELD = Pattern.compile(
        "\"([A-Za-z0-9_]+)\"\\s*:\\s*(?:\"((?:\\\\.|[^\"\\\\])*)\"|(-?\\d+))"
    );

    public InputEnvelope {
        if (protocolVersion != VERSION) {
            throw new IllegalArgumentException("unsupported input protocol version: " + protocolVersion);
        }
        if (commandId == null || !commandId.matches("[A-Za-z0-9_-]{1,96}")) {
            throw new IllegalArgumentException("invalid commandId");
        }
        if (sectorId == null || !sectorId.matches("[a-z0-9-]{1,64}")) {
            throw new IllegalArgumentException("invalid sectorId");
        }
        if (expectedTick < 0 || deadlineTick <= 0 || deadlineTick < expectedTick) {
            throw new IllegalArgumentException("invalid simulation tick window");
        }
        if (type == null) throw new IllegalArgumentException("command type is required");
        if (message == null) message = "";
        if (type == InputCommandType.CHAT) {
            message = sanitizeChat(message);
            int codePoints = message.codePointCount(0, message.length());
            if (message.isBlank() || codePoints > MAX_CHAT_CODE_POINTS) {
                throw new IllegalArgumentException("chat message must contain 1-150 characters");
            }
        } else {
            message = "";
        }
    }

    public static InputEnvelope parse(String json) {
        if (json == null || json.isBlank()) throw new IllegalArgumentException("input envelope is empty");
        Map<String, String> fields = new LinkedHashMap<>();
        Matcher matcher = FIELD.matcher(json);
        while (matcher.find()) {
            String value = matcher.group(2) != null ? unescape(matcher.group(2)) : matcher.group(3);
            fields.put(matcher.group(1), value);
        }
        try {
            return new InputEnvelope(
                Integer.parseInt(required(fields, "protocolVersion")),
                required(fields, "commandId"),
                required(fields, "sectorId"),
                Long.parseLong(required(fields, "expectedTick")),
                Long.parseLong(required(fields, "deadlineTick")),
                InputCommandType.parse(fields.get("type")),
                fields.getOrDefault("message", "")
            );
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid numeric input field", e);
        }
    }

    public String toJson() {
        return "{\"protocolVersion\":" + protocolVersion
            + ",\"commandId\":\"" + escape(commandId)
            + "\",\"sectorId\":\"" + escape(sectorId)
            + "\",\"expectedTick\":" + expectedTick
            + ",\"deadlineTick\":" + deadlineTick
            + ",\"type\":\"" + type.name().toLowerCase(Locale.ROOT)
            + "\",\"message\":\"" + escape(message) + "\"}";
    }

    private static String required(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("missing input field: " + key);
        return value;
    }

    private static String sanitizeChat(String value) {
        return value
            .replaceAll("[\\p{Cntrl}\\p{Z}\\s]+", " ")
            .trim();
    }

    private static String unescape(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!escaped) {
                if (c == '\\') escaped = true;
                else result.append(c);
            } else {
                result.append(switch (c) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case '"' -> '"';
                    case '\\' -> '\\';
                    default -> c;
                });
                escaped = false;
            }
        }
        if (escaped) result.append('\\');
        return result.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t");
    }
}
