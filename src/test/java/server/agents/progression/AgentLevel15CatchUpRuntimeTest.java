package server.agents.progression;

import constants.game.ExpTable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentLevel15CatchUpRuntimeTest {
    @Test
    void homePackWithinThirtyPercentOfMilestoneGrindsNearTheHomeTown() {
        int needed = ExpTable.getExpNeededForLevel(14);
        int thresholdExp = needed - needed * 30 / 100;

        assertEquals(AgentCareerProgressionState.Stage.HOME_GRIND_TO_MILESTONE,
                AgentLevel15CatchUpRuntime.stageAfterHome(
                        14, thresholdExp, 15,
                        AgentVictoriaLevel15Catalog.AfterHomeStrategy.ROTATION_PACK));
    }

    @Test
    void homePackOutsideThirtyPercentStillRunsTheConfiguredRotationPack() {
        int needed = ExpTable.getExpNeededForLevel(14);
        int thresholdExp = needed - needed * 30 / 100;

        assertEquals(AgentCareerProgressionState.Stage.ROTATION_QUEST_PACK,
                AgentLevel15CatchUpRuntime.stageAfterHome(
                        14, thresholdExp - 1, 15,
                        AgentVictoriaLevel15Catalog.AfterHomeStrategy.ROTATION_PACK));
    }

    @Test
    void lowerLevelUsesTheConfiguredPostHomeStrategy() {
        assertEquals(AgentCareerProgressionState.Stage.ROTATION_QUEST_PACK,
                AgentLevel15CatchUpRuntime.stageAfterHome(
                        13, 0, 15, AgentVictoriaLevel15Catalog.AfterHomeStrategy.ROTATION_PACK));
        assertEquals(AgentCareerProgressionState.Stage.HOME_GRIND_TO_MILESTONE,
                AgentLevel15CatchUpRuntime.stageAfterHome(
                        13, 0, 15, AgentVictoriaLevel15Catalog.AfterHomeStrategy.LOCAL_GRIND));
    }

    @Test
    void completedHomePackFinishesWithoutStartingTheRotationPack() {
        assertEquals(AgentCareerProgressionState.Stage.FINALIZE_AT_NEAREST_TOWN,
                AgentLevel15CatchUpRuntime.stageAfterHome(
                        15, 0, 15, AgentVictoriaLevel15Catalog.AfterHomeStrategy.ROTATION_PACK));
    }

    @Test
    void completedRotationPackFinishesAtNearestTownInsteadOfReturningToInstructor() {
        assertEquals(AgentCareerProgressionState.Stage.FINALIZE_AT_NEAREST_TOWN,
                AgentLevel15CatchUpRuntime.stageAfterRotation(15, 15));
        assertEquals(AgentCareerProgressionState.Stage.GRIND_TO_MILESTONE,
                AgentLevel15CatchUpRuntime.stageAfterRotation(14, 15));
    }
}
