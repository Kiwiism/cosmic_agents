package server.agents.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentProvisioningPolicyTest {
    @Test
    void requiresConfiguredGmLevel() {
        AgentProvisioningPolicy policy = new AgentProvisioningPolicy(
                Clock.systemUTC(), 6, 3, 60_000, 25);

        assertTrue(policy.validateAndRecordAttempt(10, 5, 0).contains("GM level 6"));
        assertNull(policy.validateAndRecordAttempt(10, 6, 0));
    }

    @Test
    void enforcesRegisteredAgentQuota() {
        AgentProvisioningPolicy policy = new AgentProvisioningPolicy(
                Clock.systemUTC(), 6, 3, 60_000, 2);

        assertTrue(policy.validateAndRecordAttempt(10, 6, 2).contains("quota"));
    }

    @Test
    void rateLimitExpiresAfterWindow() {
        MutableClock clock = new MutableClock();
        AgentProvisioningPolicy policy = new AgentProvisioningPolicy(clock, 6, 2, 1_000, 25);

        assertNull(policy.validateAndRecordAttempt(10, 6, 0));
        assertNull(policy.validateAndRecordAttempt(10, 6, 1));
        assertTrue(policy.validateAndRecordAttempt(10, 6, 2).contains("rate limited"));

        clock.advanceMillis(1_000);
        assertNull(policy.validateAndRecordAttempt(10, 6, 2));
    }

    @Test
    void concurrentRequestsCannotBypassRateLimit() throws Exception {
        AgentProvisioningPolicy policy = new AgentProvisioningPolicy(
                Clock.systemUTC(), 6, 3, 60_000, 25);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return policy.validateAndRecordAttempt(10, 6, 0);
                }));
            }
            ready.await();
            start.countDown();

            int accepted = 0;
            for (Future<String> result : results) {
                if (result.get() == null) {
                    accepted++;
                }
            }
            assertTrue(accepted == 3, "exactly the configured number of requests should be accepted");
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.EPOCH;

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        private void advanceMillis(long millis) {
            now = now.plusMillis(millis);
        }
    }
}
