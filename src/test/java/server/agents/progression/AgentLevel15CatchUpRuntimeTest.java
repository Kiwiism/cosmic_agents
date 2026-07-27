package server.agents.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentLevel15CatchUpRuntimeTest {
    @Test
    void oneLevelBelowMilestoneGrindsLocallyInsteadOfStartingTownRotation() {
        assertEquals(AgentCareerProgressionState.Stage.GRIND_TO_MILESTONE,
                AgentLevel15CatchUpRuntime.stageAfterHome(
                        14, 15, AgentVictoriaLevel15Catalog.AfterHomeStrategy.ROTATION_PACK));
    }

    @Test
    void lowerLevelUsesTheConfiguredPostHomeStrategy() {
        assertEquals(AgentCareerProgressionState.Stage.ROTATION_QUEST_PACK,
                AgentLevel15CatchUpRuntime.stageAfterHome(
                        13, 15, AgentVictoriaLevel15Catalog.AfterHomeStrategy.ROTATION_PACK));
        assertEquals(AgentCareerProgressionState.Stage.GRIND_TO_MILESTONE,
                AgentLevel15CatchUpRuntime.stageAfterHome(
                        13, 15, AgentVictoriaLevel15Catalog.AfterHomeStrategy.LOCAL_GRIND));
    }
}
