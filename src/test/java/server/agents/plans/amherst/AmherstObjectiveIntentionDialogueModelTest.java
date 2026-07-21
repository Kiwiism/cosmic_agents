package server.agents.plans.amherst;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.dialogue.semantic.AgentDialogueTopicRegistry;
import server.agents.capabilities.dialogue.semantic.AgentSemanticDialogueAct;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AmherstObjectiveIntentionDialogueModelTest {
    @Test
    void adaptsPlanContentIntoGenericObjectiveAnnouncement() {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(17);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(character, null, null);
        AmherstPlanObjective objective = new AmherstPlanObjective(
                "pio-start", AmherstPlanObjectiveKind.QUEST_START, 0, 0, 1000000,
                1008, List.of(), 10000, List.of(), null,
                List.of(), List.of(), List.of(), null, null);

        AgentSemanticDialogueAct act = new AmherstObjectiveIntentionDialogueModel().produce(
                new AmherstObjectiveIntentionDialogueModel.Context(entry, character, objective, 500L));

        assertEquals(AgentDialogueTopicRegistry.OBJECTIVE_INTENTION, act.topicId());
        assertEquals("announce", act.actKey());
        assertEquals("I'm going to Amherst to talk to Pio and start Pio's Collecting Recycled Goods.",
                act.parameters().get("message"));
    }
}
