package io.bettermindustry.protocol;

public enum InputCommandType {
    STATUS, PAUSE, RESUME, CHAT;

    public static InputCommandType parse(String value) {
        if (value == null) throw new IllegalArgumentException("command type is required");
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown input command: " + value);
        }
    }
}
