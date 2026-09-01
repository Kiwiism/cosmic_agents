package server.agents.field;

import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentEpqTestFixtureServiceTest {
    @Test
    void fullPartyUsesEveryExplorerFamilyExactlyOnce() {
        assertEquals(List.of(Job.WARRIOR, Job.MAGICIAN, Job.BOWMAN, Job.THIEF, Job.PIRATE),
                AgentEpqTestFixtureService.agentBranchesFor(null));
    }

    @Test
    void mixedPartyLeavesTheHumansFamilyToTheHuman() {
        Character human = mock(Character.class);
        when(human.getJob()).thenReturn(Job.CLERIC);

        List<Job> agents = AgentEpqTestFixtureService.agentBranchesFor(human);

        assertEquals(4, agents.size());
        assertFalse(agents.contains(Job.MAGICIAN));
    }

    @Test
    void requestedScrollIdsMatchTheRealV83Effects() {
        assertScroll(AgentEpqTestFixtureService.GLOVE_DEX_10, 10, "incDEX", 3);
        assertScroll(AgentEpqTestFixtureService.GLOVE_DEX_60, 60, "incDEX", 1);
        assertScroll(AgentEpqTestFixtureService.GLOVE_MAGIC_ATTACK_10, 10, "incMAD", 3);
        assertScroll(AgentEpqTestFixtureService.GLOVE_MAGIC_ATTACK_60, 60, "incMAD", 1);
        assertScroll(AgentEpqTestFixtureService.OVERALL_INT_60, 60, "incINT", 2);
        assertScroll(AgentEpqTestFixtureService.OVERALL_DEX_60, 60, "incDEX", 2);
        assertScroll(AgentEpqTestFixtureService.BOTTOM_DEX_10, 10, "incDEX", 3);
        assertScroll(AgentEpqTestFixtureService.BOTTOM_DEX_60, 60, "incDEX", 2);
        assertTrue(AgentEpqTestFixtureService.MINIMUM_BOSS_HIT_CHANCE >= 0.60d);
    }

    private static void assertScroll(int itemId, int success, String stat, int value) {
        Data item = DataProviderFactory.getDataProvider(WZFiles.ITEM)
                .getData("Consume/0204.img")
                .getChildByPath("%08d/info".formatted(itemId));
        assertEquals(success, DataTool.getInt("success", item));
        assertEquals(value, DataTool.getInt(stat, item));
    }
}
