package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLpqDefinitionTest {
    @Test
    void declaresEveryLocalStageAndSubmissionCount() {
        assertEquals(List.of(922_010_100, 922_010_200, 922_010_300, 922_010_400,
                        922_010_500, 922_010_600, 922_010_700, 922_010_800, 922_010_900),
                AgentLpqDefinition.stages().stream().map(AgentLpqDefinition.Stage::mapId).toList());
        assertEquals(List.of(25, 15, 32, 6, 24, 0, 3, 0, 1),
                AgentLpqDefinition.stages().stream()
                        .map(AgentLpqDefinition.Stage::submissionCount).toList());
        assertEquals(AgentLpqDefinition.BOSS_KEY, 4_001_023);
        assertEquals(6, AgentLpqDefinition.RECOMMENDED_PARTY_SIZE);
        assertEquals(2, AgentLpqDefinition.stageNumber(922_010_201));
        assertEquals(List.of(922_010_501, 922_010_502, 922_010_503,
                        922_010_504, 922_010_505, 922_010_506),
                AgentLpqDefinition.roomMaps(5));
        assertTrue(AgentLpqDefinition.isEventMap(AgentLpqDefinition.BONUS_MAP));
        assertThrows(IllegalArgumentException.class, () -> AgentLpqDefinition.stage(10));
    }
}
