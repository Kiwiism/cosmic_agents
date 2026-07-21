package server.agents.capabilities.dialogue.conversation;

import client.Character;
import config.YamlConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.semantic.AgentDialogueRuntimeSnapshot;
import server.agents.coordination.AgentCoordinationRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.MapGateway;
import server.agents.personality.AgentPersonalityAssignment;
import server.agents.personality.AgentPersonalityProfile;
import server.agents.personality.AgentPersonalityState;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.maps.MapleMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentConversationRuntimeTest {
    @AfterEach
    void clearRuntime() {
        AgentConversationRuntime.resetForTests();
        AgentRuntimeRegistry.clear();
    }

    @Test
    void startsAdvancesAndCleansUpABoundedPairSession() {
        boolean previousEnabled = YamlConfig.config.server.AGENT_DIALOGUE_SYSTEM_ENABLED;
        boolean previousConversations = YamlConfig.config.server.AGENT_CONVERSATION_ENABLED;
        boolean previousUnobserved = YamlConfig.config.server.AGENT_CONVERSATION_SIMULATE_UNOBSERVED;
        int previousMaxSessions = YamlConfig.config.server.AGENT_CONVERSATION_MAX_VISIBLE_SESSIONS_PER_MAP;
        MapleMap map = mock(MapleMap.class);
        AgentRuntimeEntry first = entry(7101, "Aeri", map);
        AgentRuntimeEntry second = entry(7102, "Beni", map);
        MapGateway gateway = mock(MapGateway.class);
        when(gateway.isObservedByPlayer(map)).thenReturn(true);
        AgentRuntimeRegistry.registerEntry(first);
        AgentRuntimeRegistry.registerEntry(second);
        AgentDialogueRuntimeSnapshot baseline = AgentConversationRuntime.snapshot();
        int baselineRoutes = AgentCoordinationRuntime.snapshot().routes();

        YamlConfig.config.server.AGENT_DIALOGUE_SYSTEM_ENABLED = true;
        YamlConfig.config.server.AGENT_CONVERSATION_ENABLED = true;
        YamlConfig.config.server.AGENT_CONVERSATION_SIMULATE_UNOBSERVED = false;
        YamlConfig.config.server.AGENT_CONVERSATION_MAX_VISIBLE_SESSIONS_PER_MAP = 1;
        try (MockedStatic<AgentMapGatewayRuntime> mapRuntime = mockStatic(AgentMapGatewayRuntime.class)) {
            mapRuntime.when(AgentMapGatewayRuntime::map).thenReturn(gateway);

            AgentConversationRuntime.tick(first, first.bot(), 1_000L);
            AgentConversationRuntime.tick(second, second.bot(), 1_000L);
            AgentConversationRuntime.tick(first, first.bot(), 10_000L);

            AgentConversationSessionView session = AgentConversationRuntime.sessionsSnapshot().getFirst();
            assertEquals(1, AgentConversationRuntime.sessionsSnapshot().size());
            AgentRuntimeEntry openingSpeaker = session.firstAgentId() == first.bot().getId() ? first : second;
            AgentRuntimeEntry listener = openingSpeaker == first ? second : first;
            AgentConversationRuntime.tick(openingSpeaker, openingSpeaker.bot(), 12_000L);
            AgentConversationRuntime.tick(listener, listener.bot(), 12_001L);

            AgentDialogueRuntimeSnapshot advanced = AgentConversationRuntime.snapshot();
            assertTrue(advanced.sessionsStarted() > baseline.sessionsStarted());
            assertTrue(advanced.coordinationPublished() > baseline.coordinationPublished());
            assertTrue(advanced.coordinationDelivered() > baseline.coordinationDelivered());

            AgentConversationRuntime.leave(first);
            assertTrue(AgentConversationRuntime.sessionsSnapshot().isEmpty());
            assertEquals(baselineRoutes, AgentCoordinationRuntime.snapshot().routes());
        } finally {
            YamlConfig.config.server.AGENT_DIALOGUE_SYSTEM_ENABLED = previousEnabled;
            YamlConfig.config.server.AGENT_CONVERSATION_ENABLED = previousConversations;
            YamlConfig.config.server.AGENT_CONVERSATION_SIMULATE_UNOBSERVED = previousUnobserved;
            YamlConfig.config.server.AGENT_CONVERSATION_MAX_VISIBLE_SESSIONS_PER_MAP = previousMaxSessions;
        }
    }

    @Test
    void doesNotStartAmbientSessionsWithoutAnObserverByDefault() {
        boolean previousEnabled = YamlConfig.config.server.AGENT_DIALOGUE_SYSTEM_ENABLED;
        boolean previousConversations = YamlConfig.config.server.AGENT_CONVERSATION_ENABLED;
        boolean previousUnobserved = YamlConfig.config.server.AGENT_CONVERSATION_SIMULATE_UNOBSERVED;
        MapleMap map = mock(MapleMap.class);
        AgentRuntimeEntry first = entry(7201, "Aeri", map);
        AgentRuntimeEntry second = entry(7202, "Beni", map);
        MapGateway gateway = mock(MapGateway.class);
        when(gateway.isObservedByPlayer(map)).thenReturn(false);
        AgentRuntimeRegistry.registerEntry(first);
        AgentRuntimeRegistry.registerEntry(second);

        YamlConfig.config.server.AGENT_DIALOGUE_SYSTEM_ENABLED = true;
        YamlConfig.config.server.AGENT_CONVERSATION_ENABLED = true;
        YamlConfig.config.server.AGENT_CONVERSATION_SIMULATE_UNOBSERVED = false;
        try (MockedStatic<AgentMapGatewayRuntime> mapRuntime = mockStatic(AgentMapGatewayRuntime.class)) {
            mapRuntime.when(AgentMapGatewayRuntime::map).thenReturn(gateway);

            AgentConversationRuntime.tick(first, first.bot(), 1_000L);
            AgentConversationRuntime.tick(second, second.bot(), 1_000L);
            AgentConversationRuntime.tick(first, first.bot(), 10_000L);

            assertTrue(AgentConversationRuntime.sessionsSnapshot().isEmpty());
        } finally {
            YamlConfig.config.server.AGENT_DIALOGUE_SYSTEM_ENABLED = previousEnabled;
            YamlConfig.config.server.AGENT_CONVERSATION_ENABLED = previousConversations;
            YamlConfig.config.server.AGENT_CONVERSATION_SIMULATE_UNOBSERVED = previousUnobserved;
        }
    }

    private static AgentRuntimeEntry entry(int id, String name, MapleMap map) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getName()).thenReturn(name);
        when(character.getMap()).thenReturn(map);
        when(character.getMapId()).thenReturn(1000000);
        when(character.getWorld()).thenReturn(0);
        when(character.getHp()).thenReturn(50);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(character, null, null);
        AgentPersonalityProfile profile = new AgentPersonalityProfile(
                "test-social", 1,
                new AgentPersonalityProfile.Traits(50, 50, 50, 50, 100, 50, 50));
        entry.capabilityStates().require(AgentPersonalityState.STATE_KEY).assign(
                new AgentPersonalityAssignment(
                        1, id, name, profile.profileId(), profile.profileVersion(), id, 0L),
                profile, true);
        return entry;
    }
}
