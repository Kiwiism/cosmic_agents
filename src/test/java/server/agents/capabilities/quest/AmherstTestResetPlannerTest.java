package server.agents.capabilities.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmherstTestResetPlannerTest {
    private final AmherstTestResetPlanner planner = new AmherstTestResetPlanner();

    @Test
    void cleanLevelOneAndMvpCleanResetAllQuestsAtStartMap() {
        for (AmherstTestResetMode mode : new AmherstTestResetMode[]{
                AmherstTestResetMode.CLEAN_LV1_START,
                AmherstTestResetMode.AMHERST_MVP_CLEAN}) {
            AmherstTestResetPlan plan = planner.plan(new AmherstTestResetRequest(1, "Agent", mode, 0));
            assertEquals(10000, plan.targetMapId());
            assertTrue(plan.resetCharacterBaseline());
            assertTrue(plan.resetAllAmherstQuests());
            assertFalse(plan.seedAmherstPrerequisites());
        }
    }

    @Test
    void questScenarioResetsOnlySelectedQuestAtItsRepresentativeMap() {
        AmherstTestResetPlan plan = planner.plan(new AmherstTestResetRequest(
                1, "Agent", AmherstTestResetMode.QUEST_SCENARIO, 1037));

        assertEquals(50000, plan.targetMapId());
        assertEquals(1037, plan.selectedQuestId());
        assertFalse(plan.resetCharacterBaseline());
        assertFalse(plan.resetAllAmherstQuests());
    }

    @Test
    void amherstReadySeedsPrerequisitesAtAmherst() {
        AmherstTestResetPlan plan = planner.plan(new AmherstTestResetRequest(
                1, "Agent", AmherstTestResetMode.AMHERST_READY, 0));

        assertEquals(1000000, plan.targetMapId());
        assertTrue(plan.resetCharacterBaseline());
        assertTrue(plan.resetAllAmherstQuests());
        assertTrue(plan.seedAmherstPrerequisites());
    }

    @Test
    void malformedAllowlistEntriesFailClosed() {
        assertEquals(java.util.Set.of(7, 9), AmherstTestResetConfig.parseIds("7, bad, -1, 0, 9"));
        assertEquals(java.util.Set.of("One", "Two"), AmherstTestResetConfig.parseNames(" One, ,Two "));
    }
}
