package server.agents.economy.ambient;

import org.junit.jupiter.api.Test;
import server.agents.economy.scenario.EconomyEngineConfig;
import server.agents.economy.scenario.NamedRandomStreams;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConstrainedAmbientBehaviorPolicyTest {
    @Test
    void sitModuleRequiresAnOwnedChairAndBecomesStandWhenAlreadySeated() {
        ConstrainedAmbientBehaviorPolicy policy = new ConstrainedAmbientBehaviorPolicy(4,
                new NamedRandomStreams(4), Map.of("sit", module(1)));
        Instant at = Instant.parse("2026-01-01T00:00:00Z");

        assertTrue(policy.choose(new AmbientBehaviorPolicy.Context(
                "agent", at, 910000001, true, false, false, false, 0)).isEmpty());
        assertEquals(AmbientBehaviorPolicy.AmbientAction.Type.SIT, policy.choose(
                new AmbientBehaviorPolicy.Context("agent", at, 910000001,
                        true, false, true, false, 0)).orElseThrow().type());
        assertEquals(AmbientBehaviorPolicy.AmbientAction.Type.STAND, policy.choose(
                new AmbientBehaviorPolicy.Context("agent", at, 910000001,
                        true, false, true, true, 0)).orElseThrow().type());
    }

    @Test
    void neverWalksAwayWhileOwningAStallAndStopsAtConfiguredLimit() {
        ConstrainedAmbientBehaviorPolicy policy = new ConstrainedAmbientBehaviorPolicy(2,
                new NamedRandomStreams(8), Map.of("walk", module(1)));
        Instant at = Instant.parse("2026-01-01T00:00:00Z");
        assertTrue(policy.choose(new AmbientBehaviorPolicy.Context(
                "agent", at, 910000001, true, false, false, false, 0)).isEmpty());
        assertTrue(policy.choose(new AmbientBehaviorPolicy.Context(
                "agent", at, 910000001, false, false, false, false, 2)).isEmpty());
    }

    private static EconomyEngineConfig.AmbientModule module(int weight) {
        EconomyEngineConfig.AmbientModule module = new EconomyEngineConfig.AmbientModule();
        module.enabled = true; module.weight = weight; return module;
    }
}
