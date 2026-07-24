package server.agents.stabilization;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.runtime.AgentCapabilityResource;
import server.agents.capabilities.runtime.AgentCapabilityResourceLockState;
import server.agents.coordination.session.AgentInteractionSessionRegistry;
import server.agents.coordination.session.AgentInteractionSessionType;
import server.agents.memory.AgentMemoryEntry;
import server.agents.memory.AgentMemoryKind;
import server.agents.memory.AgentMemoryRepository;
import server.agents.memory.AgentSessionMemoryRepository;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.decision.AgentBehaviorRouteMode;
import server.agents.runtime.decision.AgentBehaviorVersionRouter;
import server.agents.runtime.decision.AgentDecisionProvenanceState;
import server.agents.runtime.decision.AgentDecisionReplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lightweight 500-Agent gate for bounded control-plane state. Live movement,
 * map, and mob soak testing remains an operational server test.
 */
class AgentStabilizationScaleGateTest {
    @AfterEach
    void clearSessions() {
        resetInteractionSessions();
    }

    @Test
    void fiveHundredAgentControlPlaneRemainsBoundedAndReplayable() throws Exception {
        List<AgentRuntimeEntry> entries = new ArrayList<>();
        for (int id = 1; id <= 500; id++) {
            Character agent = mock(Character.class);
            when(agent.getId()).thenReturn(id);
            AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
            entries.add(entry);

            AgentBehaviorVersionRouter.route(entry, "combat-targeting", "v1", "v2",
                    AgentBehaviorRouteMode.CANARY, 10, id, "soak:" + id);
            AgentMemoryRepository memories = new AgentSessionMemoryRepository(entry);
            memories.remember(new AgentMemoryEntry(
                    AgentMemoryKind.WORKING, "objective", "maple-island",
                    1.0, id, id + 10_000L, "scale-gate"), id);
            AgentCapabilityResourceLockState locks = entry.capabilityStates()
                    .require(AgentCapabilityResourceLockState.STATE_KEY);
            assertTrue(locks.acquire("soak:" + id,
                    Set.of(AgentCapabilityResource.MOVEMENT), id, id + 1_000L));
            locks.releaseOwner("soak:" + id);
        }
        for (int id = 1; id <= 500; id += 2) {
            AgentInteractionSessionRegistry.propose(
                    AgentInteractionSessionType.TOWN_ENCOUNTER,
                    id, Set.of(id + 1), 1_000L, 10_000L,
                    "soak-session:" + id, Map.of());
        }

        assertEquals(500, entries.size());
        assertEquals(250, AgentInteractionSessionRegistry.size(1_000L));
        for (AgentRuntimeEntry entry : entries) {
            assertEquals(1, new AgentSessionMemoryRepository(entry)
                    .recallAll(AgentMemoryKind.WORKING, 1_000L).size());
            assertEquals(0, entry.capabilityStates()
                    .require(AgentCapabilityResourceLockState.STATE_KEY).size(1_000L));
            assertTrue(AgentDecisionReplay.verify(entry.capabilityStates()
                    .require(AgentDecisionProvenanceState.STATE_KEY).snapshot()).valid());
        }
    }

    private static void resetInteractionSessions() {
        try {
            var method = AgentInteractionSessionRegistry.class
                    .getDeclaredMethod("resetForTests");
            method.setAccessible(true);
            method.invoke(null);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }
}
