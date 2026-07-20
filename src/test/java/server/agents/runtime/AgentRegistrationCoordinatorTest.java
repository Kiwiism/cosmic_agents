package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.plans.AgentPlanReattachmentRuntime;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentRegistrationCoordinatorTest {
    @Test
    void manualRegistrationPreservesNonSpawnNormalizationFlag() {
        Character leader = mock(Character.class);
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        AgentLifecycleService.AgentTickCallback tickCallback = (activeEntry, leaderCharId, agentCharId) -> {
        };

        try (MockedStatic<AgentLifecycleService> lifecycle = mockStatic(AgentLifecycleService.class);
             MockedStatic<AgentPersonalityRuntime> personality = mockStatic(AgentPersonalityRuntime.class);
             MockedStatic<AgentPlanReattachmentRuntime> reattachment =
                     mockStatic(AgentPlanReattachmentRuntime.class)) {
            lifecycle.when(() -> AgentLifecycleService.registerAgent(
                            eq(100),
                            eq(leader),
                            eq(agent),
                            eq(false),
                            any(AgentLifecycleService.RegisterHooks.class)))
                    .thenReturn(entry);

            assertSame(entry, AgentRegistrationCoordinator.registerManualAgent(100, leader, agent, tickCallback));
            personality.verify(() -> AgentPersonalityRuntime.restoreOrAssign(
                    eq(entry), eq(false), anyLong()));
        }
    }

    @Test
    void spawnedRegistrationPreservesSpawnNormalizationFlag() {
        Character leader = mock(Character.class);
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        AgentLifecycleService.AgentTickCallback tickCallback = (activeEntry, leaderCharId, agentCharId) -> {
        };

        try (MockedStatic<AgentLifecycleService> lifecycle = mockStatic(AgentLifecycleService.class);
             MockedStatic<AgentPlanReattachmentRuntime> reattachment =
                     mockStatic(AgentPlanReattachmentRuntime.class)) {
            lifecycle.when(() -> AgentLifecycleService.registerAgent(
                            eq(100),
                            eq(leader),
                            eq(agent),
                            eq(true),
                            any(AgentLifecycleService.RegisterHooks.class)))
                    .thenReturn(entry);

            assertSame(entry, AgentRegistrationCoordinator.registerSpawnedAgent(100, leader, agent, tickCallback));
        }
    }
}
