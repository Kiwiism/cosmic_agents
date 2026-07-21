package server.agents.capabilities.dialogue.conversation;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCoreConversationTopicProviderTest {
    @Test
    void slowSpawnUsesOnlyRegionNeutralActivityFacts() {
        AgentRuntimeEntry first = entry(1, "Aeri");
        AgentRuntimeEntry second = entry(2, "Beni");
        AgentConversationSelectionContext context = new AgentConversationSelectionContext(
                first, second, 20_000L, 11L,
                new AgentConversationActivity(true, true, 12_000L, false),
                AgentConversationActivity.NONE);
        AgentConversationTopicModel slowSpawn = new AgentCoreConversationTopicProvider().topicModels()
                .stream()
                .filter(model -> model.definition().topicId()
                        .equals(AgentCoreConversationTopicProvider.SLOW_SPAWN))
                .findFirst().orElseThrow();

        assertTrue(slowSpawn.utility(context) > AgentConversationTopicModel.UNAVAILABLE);
    }

    @Test
    void activityIdentityIsGenericAndSurvivesMerging() {
        AgentConversationActivity objective = new AgentConversationActivity(
                true, true, 12_000L, false, "region-plan-objective-17");

        AgentConversationActivity merged = AgentConversationActivity.NONE.merge(objective);

        assertEquals("region-plan-objective-17", merged.objectiveKey());
        assertTrue(merged.objectiveActive());
        assertTrue(merged.hunting());
    }

    private static AgentRuntimeEntry entry(int id, String name) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getName()).thenReturn(name);
        return new AgentRuntimeEntry(character, null, null);
    }
}
