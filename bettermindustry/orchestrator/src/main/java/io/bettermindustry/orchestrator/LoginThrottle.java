package io.bettermindustry.orchestrator;

import java.util.LinkedHashMap;

final class LoginThrottle {
    static final int MAX_FAILURES = 5;
    static final long WINDOW_MILLIS = 5L * 60L * 1_000L;
    static final int MAX_KEYS = 2_048;

    private final LinkedHashMap<String, Attempt> attempts = new LinkedHashMap<>();

    long blockedForMillis(String key, long now) {
        prune(now);
        Attempt attempt = attempts.get(key);
        if (attempt == null || attempt.failures() < MAX_FAILURES) return 0;
        long remaining = attempt.windowStartedMillis() + WINDOW_MILLIS - now;
        return Math.max(0, remaining);
    }

    long recordFailure(String key, long now) {
        prune(now);
        Attempt current = attempts.get(key);
        Attempt updated;
        if (current == null || now - current.windowStartedMillis() >= WINDOW_MILLIS) {
            updated = new Attempt(1, now);
        } else {
            updated = new Attempt(current.failures() + 1, current.windowStartedMillis());
        }
        attempts.put(key, updated);
        prune(now);
        return blockedForMillis(key, now);
    }

    void recordSuccess(String key) {
        attempts.remove(key);
    }

    void prune(long now) {
        attempts.entrySet().removeIf(entry ->
            now - entry.getValue().windowStartedMillis() >= WINDOW_MILLIS);
        while (attempts.size() > MAX_KEYS) {
            attempts.remove(attempts.keySet().iterator().next());
        }
    }

    private record Attempt(int failures, long windowStartedMillis) {
    }
}
