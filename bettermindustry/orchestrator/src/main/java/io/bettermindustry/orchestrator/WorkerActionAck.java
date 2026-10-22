package io.bettermindustry.orchestrator;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record WorkerActionAck(
    int protocolVersion,
    String commandId,
    Status status,
    double tick,
    boolean paused,
    String detail,
    Instant receivedAt
) {
    static final int VERSION = 1;
    private static final Pattern VERSION_FIELD = field("protocolVersion", "(-?\\d+)");
    private static final Pattern COMMAND_ID = field("commandId", "\"([A-Za-z0-9_-]{1,96})\"");
    private static final Pattern STATUS = field("status", "\"(APPLIED|FAILED)\"");
    private static final Pattern TICK = field("tick", "([-0-9.eE]+)");
    private static final Pattern PAUSED = field("paused", "(true|false)");
    private static final Pattern DETAIL = field("detail", "\"((?:\\\\.|[^\"\\\\])*)\"");

    static WorkerActionAck parse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("action acknowledgement is empty");
        }
        int version = Integer.parseInt(required(VERSION_FIELD, json, "protocolVersion"));
        if (version != VERSION) {
            throw new IllegalArgumentException("unsupported action acknowledgement version: " + version);
        }
        return new WorkerActionAck(
            version,
            required(COMMAND_ID, json, "commandId"),
            Status.valueOf(required(STATUS, json, "status")),
            Double.parseDouble(required(TICK, json, "tick")),
            Boolean.parseBoolean(required(PAUSED, json, "paused")),
            unescape(required(DETAIL, json, "detail")),
            Instant.now()
        );
    }

    enum Status {
        APPLIED, FAILED
    }

    private static Pattern field(String name, String valuePattern) {
        return Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*" + valuePattern);
    }

    private static String required(Pattern pattern, String json, String field) {
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("missing action acknowledgement field: " + field);
        }
        return matcher.group(1);
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
}
