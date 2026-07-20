package server.agents.capabilities.quest;

import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;
import server.quest.Quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentQuestRewardChoicePolicyTest {
    @Test
    void loadsExplicitPolicyContract() {
        assertTrue(AgentQuestRewardChoicePolicy.catalog().priorityOrder()
                .contains("authored-fixed-choice"));
    }

    @Test
    void selectsAJobEligibleRewardInsteadOfPassingNull() {
        Character warrior = mock(Character.class);
        when(warrior.getId()).thenReturn(500);
        when(warrior.getGender()).thenReturn(0);
        when(warrior.getJob()).thenReturn(Job.WARRIOR);

        var decision = AgentQuestRewardChoicePolicy.choose(warrior, Quest.getInstance(10005));

        assertTrue(decision.isPresent());
        assertFalse(decision.orElseThrow().strategy().isBlank());
    }
}
