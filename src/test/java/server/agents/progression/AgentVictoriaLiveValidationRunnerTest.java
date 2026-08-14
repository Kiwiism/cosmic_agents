package server.agents.progression;

import client.QuestStatus;
import org.junit.jupiter.api.Test;
import server.agents.plans.AgentPlanExecutionStatus;

import java.awt.Point;
import java.util.List;

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

    @Test
    void henesysPackRequiresBruceRinaCamilaAndJayToBeTurnedIn() {
        int completed = QuestStatus.Status.COMPLETED.getId();
        var incomplete = sample(0, 0, 0, 0, 1, 1, 1, 1,
                40, 10, 20, completed, completed, completed, 1);
        var complete = sample(0, 0, 0, 0, 1, 1, 1, 1,
                0, 0, 0, completed, completed, completed, completed);

        assertFalse(incomplete.henesysPackComplete());
        assertTrue(complete.henesysPackComplete());
    }

    @Test
    void warriorValidationRequiresBothPerionAndElliniaPacks() {
        int completed = QuestStatus.Status.COMPLETED.getId();
        var incomplete = sampleWithWarriorStatuses(
                List.of(completed, completed, completed, completed, completed, 1));
        var complete = sampleWithWarriorStatuses(
                List.of(completed, completed, completed, completed, completed, completed));

        assertFalse(incomplete.warriorPacksComplete());
        assertTrue(complete.warriorPacksComplete());
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
        return sample(orangeKills, pigKills, ribbons, slimeKills,
                orangeStatus, pigStatus, ribbonStatus, slimeStatus,
                0, 0, 0, 0, 0, 0, 0);
    }

    private static AgentVictoriaLiveValidationRunner.Sample sample(
            int orangeKills,
            int pigKills,
            int ribbons,
            int slimeKills,
            int orangeStatus,
            int pigStatus,
            int ribbonStatus,
            int slimeStatus,
            int orangeCaps,
            int spores,
            int greenCaps,
            int bruceStatus,
            int rinaStatus,
            int camilaStatus,
            int jayStatus) {
        return new AgentVictoriaLiveValidationRunner.Sample(
                1_000L,
                100030000,
                new Point(),
                AgentCareerProgressionState.Stage.ROTATION_QUEST_PACK,
                8,
                AgentPlanExecutionStatus.ACTIVE,
                12,
                1_234,
                orangeKills,
                pigKills,
                ribbons,
                slimeKills,
                orangeStatus,
                pigStatus,
                ribbonStatus,
                slimeStatus,
                orangeCaps,
                spores,
                greenCaps,
                bruceStatus,
                rinaStatus,
                camilaStatus,
                jayStatus,
                List.of(),
                0,
                0,
                null,
                null,
                "test");
    }

    private static AgentVictoriaLiveValidationRunner.Sample sampleWithWarriorStatuses(
            List<Integer> statuses) {
        return new AgentVictoriaLiveValidationRunner.Sample(
                1_000L, 102000000, new Point(),
                AgentCareerProgressionState.Stage.ROTATION_QUEST_PACK, 0,
                AgentPlanExecutionStatus.ACTIVE,
                12, 1_234,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0,
                0, 0, 0, 0,
                statuses,
                0, 0, null, null, "test");
    }
}
