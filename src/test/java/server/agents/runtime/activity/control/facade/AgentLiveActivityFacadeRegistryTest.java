package server.agents.runtime.activity.control.facade;

import org.junit.jupiter.api.Test;
import server.agents.runtime.activity.session.AgentActivityKind;
import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLiveActivityFacadeRegistryTest {
    @Test
    void standardRegistryCoversEveryPrimarySystemAndPublishesHonestRollbackReadiness() {
        AgentLiveActivityFacadeRegistry registry = AgentStandardLiveActivityFacades.registry();
        assertTrue(registry.coversAllPrimaryActivities());
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(27);
        assertTrue(registry.bind(AgentActivityKind.QUESTING, entry, agent).rollbackSupported());
        java.util.EnumSet<AgentActivityKind> incomplete = java.util.EnumSet.of(
                AgentActivityKind.HUNTING, AgentActivityKind.TOWN_LIFE,
                AgentActivityKind.COMMERCE, AgentActivityKind.PARTY_QUEST);
        incomplete.forEach(kind -> assertFalse(
                registry.bind(kind, entry, agent).rollbackSupported(), kind.name()));
    }
}
