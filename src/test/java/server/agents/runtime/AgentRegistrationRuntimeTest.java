package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.bots.BotEntry;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentRegistrationRuntimeTest {
    @Test
    void manualRegistrationPreservesNonSpawnNormalizationFlag() {
        Character leader = mock(Character.class);
        Character agent = mock(Character.class);
        BotEntry entry = mock(BotEntry.class);
        AgentLifecycleService.AgentTickCallback tickCallback = (activeEntry, leaderCharId, agentCharId) -> {
        };

        try (MockedStatic<AgentLifecycleService> lifecycle = mockStatic(AgentLifecycleService.class)) {
            lifecycle.when(() -> AgentLifecycleService.registerAgent(
                            eq(100),
                            eq(leader),
                            eq(agent),
                            eq(false),
                            any(AgentLifecycleService.RegisterHooks.class)))
                    .thenReturn(entry);

            assertSame(entry, AgentRegistrationRuntime.registerManualAgent(100, leader, agent, tickCallback));
        }
    }

    @Test
    void spawnedRegistrationPreservesSpawnNormalizationFlag() {
        Character leader = mock(Character.class);
        Character agent = mock(Character.class);
        BotEntry entry = mock(BotEntry.class);
        AgentLifecycleService.AgentTickCallback tickCallback = (activeEntry, leaderCharId, agentCharId) -> {
        };

        try (MockedStatic<AgentLifecycleService> lifecycle = mockStatic(AgentLifecycleService.class)) {
            lifecycle.when(() -> AgentLifecycleService.registerAgent(
                            eq(100),
                            eq(leader),
                            eq(agent),
                            eq(true),
                            any(AgentLifecycleService.RegisterHooks.class)))
                    .thenReturn(entry);

            assertSame(entry, AgentRegistrationRuntime.registerSpawnedAgent(100, leader, agent, tickCallback));
        }
    }
}
