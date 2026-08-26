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
        assertEquals(922_010_201, AgentLpqDefinition.STAGE_2_TRAP_MAP);
        assertEquals(2_200_002, AgentLpqDefinition.STAGE_2_TRAP_REACTOR);
        assertEquals(2, AgentLpqDefinition.STAGE_2_SCOUT_COUNT);
        assertEquals(6, AgentLpqDefinition.RECOMMENDED_PARTY_SIZE);
        assertEquals(2, AgentLpqDefinition.stageNumber(922_010_201));
        assertEquals(List.of(922_010_501, 922_010_502, 922_010_503,
                        922_010_504, 922_010_505, 922_010_506),
                AgentLpqDefinition.roomMaps(5));
        assertEquals(List.of(1, 1, 1, 2, 1),
                AgentLpqDefinition.roomMaps(4).stream()
                        .map(AgentLpqDefinition::roomPassQuota).toList());
        assertEquals(List.of(4, 4, 4, 4, 4, 4),
                AgentLpqDefinition.roomMaps(5).stream()
                        .map(AgentLpqDefinition::roomPassQuota).toList());
        assertEquals(AgentLpqDefinition.stage(4).submissionCount(),
                AgentLpqDefinition.roomMaps(4).stream()
                        .mapToInt(AgentLpqDefinition::roomPassQuota).sum());
        assertEquals(AgentLpqDefinition.stage(5).submissionCount(),
                AgentLpqDefinition.roomMaps(5).stream()
                        .mapToInt(AgentLpqDefinition::roomPassQuota).sum());
        assertEquals(AgentLpqDefinition.stage(7).submissionCount(),
                AgentLpqDefinition.STAGE_7_TRIGGER_MOBS.size());
        assertEquals(10, AgentLpqDefinition.ROOM_MARKER_MESOS);
        assertEquals(922_010_200, AgentLpqDefinition.nextTraversalMap(922_010_100));
        assertEquals(922_010_300, AgentLpqDefinition.nextTraversalMap(922_010_200));
        assertEquals(922_010_200, AgentLpqDefinition.nextTraversalMap(922_010_201));
        assertEquals(922_010_400, AgentLpqDefinition.nextTraversalMap(922_010_404));
        assertEquals(AgentLpqDefinition.CLEAR_MAP,
                AgentLpqDefinition.nextTraversalMap(922_010_900));
        assertEquals(0, AgentLpqDefinition.nextTraversalMap(AgentLpqDefinition.EXIT_MAP));
        assertTrue(AgentLpqDefinition.isEventMap(AgentLpqDefinition.BONUS_MAP));
        assertEquals(0, AgentLpqDefinition.roomPassQuota(922_010_100));
        assertThrows(IllegalArgumentException.class, () -> AgentLpqDefinition.stage(10));
    }
}
