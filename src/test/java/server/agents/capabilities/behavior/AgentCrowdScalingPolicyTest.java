package server.agents.capabilities.behavior;

import org.junit.jupiter.api.Test;
import server.agents.model.AgentPosition;
import server.agents.perception.AgentCharacterPerception;
import server.agents.perception.AgentPerceptionSnapshot;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCrowdScalingPolicyTest {
    @Test
    void responseCeilingGrowsFromResponsiveSmallMapToWideCrowdVariation() {
        assertEquals(500, AgentCrowdScalingPolicy.responseCeilingMs(5));
        assertEquals(1_500, AgentCrowdScalingPolicy.responseCeilingMs(10));
        assertEquals(5_000, AgentCrowdScalingPolicy.responseCeilingMs(20));
        assertEquals(10_500, AgentCrowdScalingPolicy.responseCeilingMs(30));

        assertEquals(500, AgentCrowdScalingPolicy.responseDelayMs(2_500, 19, 5));
        assertEquals(10_500, AgentCrowdScalingPolicy.responseDelayMs(2_500, 19, 30));
        assertTrue(AgentCrowdScalingPolicy.responseDelayMs(650, 0, 5) < 150);
    }

    @Test
    void respiteCeilingOnlyBecomesLongWhenTheMapIsCrowded() {
        assertEquals(0, AgentCrowdScalingPolicy.restCeilingMs(5));
        assertEquals(1_875, AgentCrowdScalingPolicy.restCeilingMs(10));
        assertEquals(9_875, AgentCrowdScalingPolicy.restCeilingMs(20));
        assertEquals(24_875, AgentCrowdScalingPolicy.restCeilingMs(30));

        assertEquals(0, AgentCrowdScalingPolicy.restDurationMs(35_000, 5));
        assertEquals(24_875, AgentCrowdScalingPolicy.restDurationMs(35_000, 30));
    }

    @Test
    void targetVariationUsesLocalRatherThanWholeMapCrowd() {
        AgentPerceptionSnapshot snapshot = new AgentPerceptionSnapshot(
                1010100,
                1L,
                List.of(),
                List.of(),
                1,
                List.of(),
                List.of(
                        character(1, 0, 0),
                        character(2, 100, 0),
                        character(3, 200, 0),
                        character(4, 300, 0),
                        character(5, 351, 0),
                        character(6, 1_000, 0)));

        assertEquals(4, AgentCrowdScalingPolicy.localCharacters(
                snapshot, new AgentPosition(0, 0)));
        assertEquals(0, AgentCrowdScalingPolicy.targetVariationPercent(4));
        assertEquals(0, AgentCrowdScalingPolicy.targetVariationPercent(5));
        assertEquals(28, AgentCrowdScalingPolicy.targetVariationPercent(7));
        assertEquals(100, AgentCrowdScalingPolicy.targetVariationPercent(12));
        assertEquals(0, AgentCrowdScalingPolicy.anchorPercent(60, 5));
        assertEquals(60, AgentCrowdScalingPolicy.anchorPercent(60, 12));
    }

    private static AgentCharacterPerception character(int id, int x, int y) {
        return new AgentCharacterPerception(id, new AgentPosition(x, y), id != 1);
    }
}
