package com.hireflow.web.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BruteForceThrottler - Login Abuse & Rate Limiting Tests")
class BruteForceThrottlerTest {

    private BruteForceThrottler throttler;

    @BeforeEach
    void setUp() {
        // 3 max attempts, 2-second lockout for fast testing
        throttler = new BruteForceThrottler(3, 2L);
    }

    @Test
    @DisplayName("throttling: blocks IP and username after reaching max consecutive failures")
    void testBlocksAfterMaxFailures() {
        String ip = "192.168.1.100";
        String user = "target.user";

        assertFalse(throttler.isBlocked(ip, user));

        throttler.recordFailure(ip, user);
        assertFalse(throttler.isBlocked(ip, user));

        throttler.recordFailure(ip, user);
        assertFalse(throttler.isBlocked(ip, user));

        throttler.recordFailure(ip, user); // 3rd failure triggers lockout
        assertTrue(throttler.isBlocked(ip, user));
        assertTrue(throttler.isBlocked(ip, "another.user"), "IP should be blocked");
        assertTrue(throttler.isBlocked("10.0.0.1", user), "Targeted username should be blocked");
    }

    @Test
    @DisplayName("recovery: clearing on success resets failure counters")
    void testSuccessResetsFailureCounter() {
        String ip = "10.0.0.2";
        String user = "alice";

        throttler.recordFailure(ip, user);
        throttler.recordFailure(ip, user);
        assertFalse(throttler.isBlocked(ip, user));

        throttler.recordSuccess(ip, user);

        // Next failure starts at count 1 again
        throttler.recordFailure(ip, user);
        assertFalse(throttler.isBlocked(ip, user));
    }

    @Test
    @DisplayName("temporary lockout: lockout expires automatically after duration")
    void testLockoutExpiresAfterWindow() throws InterruptedException {
        String ip = "172.16.0.5";
        String user = "bob";

        throttler.recordFailure(ip, user);
        throttler.recordFailure(ip, user);
        throttler.recordFailure(ip, user);
        assertTrue(throttler.isBlocked(ip, user));

        // Wait for lockout duration (2 seconds + margin)
        Thread.sleep(2200);

        assertFalse(throttler.isBlocked(ip, user), "Lockout must expire after configured window");
    }
}
