package server.agents.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentProvisioningPolicyTest {
    @Test
    void requiresConfiguredGmLevel() {
        AgentProvisioningPolicy policy = new AgentProvisioningPolicy(
                Clock.systemUTC(), 6, 3, 60_000, 25);

        assertTrue(policy.validate(10, 5, 0).contains("GM level 6"));
        assertNull(policy.validate(10, 6, 0));
    }

    @Test
    void enforcesRegisteredAgentQuota() {
        AgentProvisioningPolicy policy = new AgentProvisioningPolicy(
                Clock.systemUTC(), 6, 3, 60_000, 2);

        assertTrue(policy.validate(10, 6, 2).contains("quota"));
    }

    @Test
    void rateLimitExpiresAfterWindow() {
        MutableClock clock = new MutableClock();
        AgentProvisioningPolicy policy = new AgentProvisioningPolicy(clock, 6, 2, 1_000, 25);

        assertNull(policy.validate(10, 6, 0));
        policy.recordProvisioned(10);
        assertNull(policy.validate(10, 6, 1));
        policy.recordProvisioned(10);
        assertTrue(policy.validate(10, 6, 2).contains("rate limited"));

        clock.advanceMillis(1_000);
        assertNull(policy.validate(10, 6, 2));
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
