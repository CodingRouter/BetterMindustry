package io.bettermindustry.orchestrator;

public enum ViewMode {
    FULL, SUMMARY, OFF;

    public static ViewMode parse(String value) {
        if (value == null) throw new IllegalArgumentException("view mode is required");
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown view mode: " + value);
        }
    }
}
