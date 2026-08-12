package server.agents.progression;

import client.QuestStatus;
import org.junit.jupiter.api.Test;
import server.agents.plans.AgentPlanExecutionStatus;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentVictoriaLiveValidationRunnerTest {
    @Test
    void forestHuntRequiresAllThreeEqualObjectiveFamilies() {
        var incomplete = sample(30, 30, 19, 0, 1, 1, 1, 1);
        var complete = sample(30, 30, 20, 0, 1, 1, 1, 1);

        assertFalse(incomplete.forestHuntComplete());
        assertTrue(complete.forestHuntComplete());
    }

    @Test
    void completedQuestCountsAsSatisfiedAfterProgressAndItemsAreConsumed() {
        int completed = QuestStatus.Status.COMPLETED.getId();
        var sample = sample(0, 0, 0, 0, completed, completed, completed, completed);

        assertTrue(sample.forestHuntComplete());
        assertTrue(sample.nautilusPackComplete());
    }

    @Test
    void nautilusPackAlsoRequiresTheSlimeQuestToBeTurnedIn() {
        int completed = QuestStatus.Status.COMPLETED.getId();
        var sample = sample(30, 30, 20, 30, completed, completed, completed, 1);

        assertTrue(sample.forestHuntComplete());
        assertFalse(sample.nautilusPackComplete());
    }

    private static AgentVictoriaLiveValidationRunner.Sample sample(
            int orangeKills,
            int pigKills,
            int ribbons,
            int slimeKills,
            int orangeStatus,
            int pigStatus,
            int ribbonStatus,
            int slimeStatus) {
        return new AgentVictoriaLiveValidationRunner.Sample(
                1_000L,
                100030000,
                new Point(),
                AgentCareerProgressionState.Stage.ROTATION_QUEST_PACK,
                8,
                AgentPlanExecutionStatus.ACTIVE,
                orangeKills,
                pigKills,
                ribbons,
                slimeKills,
                orangeStatus,
                pigStatus,
                ribbonStatus,
                slimeStatus,
                null,
                null);
    }
}
