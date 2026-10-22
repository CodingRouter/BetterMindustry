package io.bettermindustry.orchestrator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginThrottleTest {
    @Test
    void blocksAfterFiveFailuresAndClearsOnSuccess() {
        LoginThrottle throttle = new LoginThrottle();
        long now = 1_000;

        for (int i = 0; i < LoginThrottle.MAX_FAILURES - 1; i++) {
            assertEquals(0, throttle.recordFailure("key", now));
        }
        assertTrue(throttle.recordFailure("key", now) > 0);
        assertTrue(throttle.blockedForMillis("key", now + 1) > 0);

        throttle.recordSuccess("key");
        assertEquals(0, throttle.blockedForMillis("key", now + 2));
    }

    @Test
    void expiresWindowAndKeepsBoundedKeys() {
        LoginThrottle throttle = new LoginThrottle();
        for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
            throttle.recordFailure("expired", 1_000);
        }
        assertEquals(0, throttle.blockedForMillis(
            "expired",
            1_000 + LoginThrottle.WINDOW_MILLIS
        ));

        for (int i = 0; i < LoginThrottle.MAX_KEYS + 1; i++) {
            throttle.recordFailure("key-" + i, 10_000);
        }
        assertEquals(0, throttle.blockedForMillis("key-0", 10_001));
        assertEquals(0, throttle.blockedForMillis(
            "key-" + LoginThrottle.MAX_KEYS,
            10_001
        ));
    }
}
