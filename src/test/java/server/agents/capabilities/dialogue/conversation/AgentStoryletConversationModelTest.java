package server.agents.capabilities.dialogue.conversation;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.dialogue.semantic.AgentDialogueTopicRegistry;
import server.agents.capabilities.dialogue.semantic.AgentSemanticDialogueAct;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentStoryletConversationModelTest {
    private final AgentStoryletConversationModel model = new AgentStoryletConversationModel();

    @Test
    void producesBoundedOpeningContentReplyAndClosingActs() {
        AgentRuntimeEntry first = entry(10, "Aeri");
        AgentRuntimeEntry second = entry(20, "Beni");

        AgentSemanticDialogueAct opening = model.produce(context(first, second, 0));
        AgentSemanticDialogueAct question = model.produce(context(first, second, 1));
        AgentSemanticDialogueAct reply = model.produce(context(first, second, 2));
        AgentSemanticDialogueAct closing = model.produce(context(first, second, 3));

        assertEquals(AgentDialogueTopicRegistry.GREETING, opening.topicId());
        assertEquals("open", opening.actKey());
        assertEquals(AgentDialogueTopicRegistry.TRAVEL, question.topicId());
        assertEquals("ask", question.actKey());
        assertEquals(AgentDialogueTopicRegistry.TRAVEL, reply.topicId());
        assertEquals("reply", reply.actKey());
        assertEquals(AgentDialogueTopicRegistry.FAREWELL, closing.topicId());
        assertEquals("close", closing.actKey());
    }

    @Test
    void disabledFramingTopicsFallBackToTheSelectedStorylet() {
        AgentRuntimeEntry first = entry(10, "Aeri");
        AgentRuntimeEntry second = entry(20, "Beni");
        AgentDialogueTopicRegistry.setOverride(AgentDialogueTopicRegistry.GREETING, false);
        AgentDialogueTopicRegistry.setOverride(AgentDialogueTopicRegistry.FAREWELL, false);
        try {
            AgentSemanticDialogueAct opening = model.produce(context(first, second, 0));
            AgentSemanticDialogueAct closing = model.produce(context(first, second, 3));

            assertEquals(AgentDialogueTopicRegistry.TRAVEL, opening.topicId());
            assertEquals("ask", opening.actKey());
            assertEquals(AgentDialogueTopicRegistry.TRAVEL, closing.topicId());
            assertEquals("reply", closing.actKey());
        } finally {
            AgentDialogueTopicRegistry.setOverride(AgentDialogueTopicRegistry.GREETING, null);
            AgentDialogueTopicRegistry.setOverride(AgentDialogueTopicRegistry.FAREWELL, null);
        }
    }

    private static AgentConversationModelContext context(AgentRuntimeEntry first,
                                                         AgentRuntimeEntry second,
                                                         int turn) {
        return new AgentConversationModelContext(first, second, 7L,
                AgentDialogueTopicRegistry.TRAVEL, turn, 4, 1_000L + turn, 100L + turn);
    }

    private static AgentRuntimeEntry entry(int id, String name) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getName()).thenReturn(name);
        when(character.getMapId()).thenReturn(1000000);
        return new AgentRuntimeEntry(character, null, null);
    }
}
