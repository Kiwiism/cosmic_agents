package server.agents.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentCareerProgressionCheckpointTest {
    @Test
    void restoredStateContinuesAtTheSavedQuestCursorWithoutAnotherWrite() {
        AgentCareerBuildBundle bundle = AgentCareerBuildBundleRepository.defaultRepository()
                .find("thief-claw-standard-v1").orElseThrow();
        AgentCareerProgressionState original = new AgentCareerProgressionState();
        original.reset(bundle, AgentCareerProgressionState.RunMode.LEVEL15_WITH_INITIAL_SHOP,
                "lv9-grind", AgentCareerProgressionState.Stage.INSTRUCTOR_TRAINING, 3_000L);
        original.trainingQuestIndex(2);
        AgentCareerProgressionCheckpoint checkpoint = original.pendingCheckpoint(44, 2_000L);
        assertNotNull(checkpoint);

        AgentCareerProgressionState restored = new AgentCareerProgressionState();
        restored.restore(bundle, checkpoint);

        assertEquals(AgentCareerProgressionState.Stage.INSTRUCTOR_TRAINING, restored.stage());
        assertEquals(2, restored.trainingQuestIndex());
        assertEquals("lv9-grind", restored.startVariantId());
        assertNull(restored.pendingCheckpoint(44, 2_100L));
    }
}
