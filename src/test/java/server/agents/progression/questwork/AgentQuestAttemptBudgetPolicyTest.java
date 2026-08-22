package server.agents.progression.questwork;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentQuestAttemptBudgetPolicyTest {
    @Test
    void budgetIsPositiveBoundedAndNonDecreasing() {
        int levelOne = AgentQuestAttemptBudgetPolicy.budgetForLevel(1);
        int levelTwentyOne = AgentQuestAttemptBudgetPolicy.budgetForLevel(21);
        int extreme = AgentQuestAttemptBudgetPolicy.budgetForLevel(Integer.MAX_VALUE);

        assertTrue(levelOne > 0);
        assertTrue(levelTwentyOne >= levelOne);
        assertTrue(extreme >= levelTwentyOne);
        assertTrue(extreme <= 80);
    }
}
