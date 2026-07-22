package server.agents.behavior;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBehaviorCalibrationStateTest {
    @Test
    void durableSeedReplaysBaselineAndDecisionSequence() {
        AgentBehaviorPolicyProfile policy = AgentBehaviorPolicyRepository.defaultRepository().resolve("restless-v1");
        AgentBehaviorCalibrationState first = new AgentBehaviorCalibrationState();
        AgentBehaviorCalibrationState replay = new AgentBehaviorCalibrationState();
        first.configure(policy, 91234L, true);
        replay.configure(policy, 91234L, true);

        assertEquals(first.responseBaselineMs(), replay.responseBaselineMs());
        assertTrue(first.responseBaselineMs() >= policy.response().minMs());
        assertTrue(first.responseBaselineMs() <= policy.response().maxMs());
        for (int index = 0; index < 10; index++) {
            assertEquals(first.nextPercent("target"), replay.nextPercent("target"));
        }
        assertEquals(first.stablePercent("anchor", 1L), replay.stablePercent("anchor", 1L));
    }
}
