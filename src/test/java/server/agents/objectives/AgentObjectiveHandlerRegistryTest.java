package server.agents.objectives;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class AgentObjectiveHandlerRegistryTest {
    @Test
    void dispatchesByExactObjectiveType() throws Exception {
        AgentObjectiveHandlerRegistry registry = new AgentObjectiveHandlerRegistry(Map.of(
                "quest", (entry, agent, objective, nowMs) -> AgentObjectiveAttachment.ATTACHED));
        AgentObjectiveDefinition objective = new AgentObjectiveDefinition(
                "q:1", "quest", 10, 1_000L, 1,
                AgentObjectiveSource.QUEST_PLAN, "v1", "run");

        assertEquals(AgentObjectiveAttachment.ATTACHED,
                registry.reconcileAndAttach(new AgentRuntimeEntry(null, null, null),
                        mock(Character.class), objective, 10L));
    }

    @Test
    void rejectsObjectivesWithoutARegisteredHandler() {
        AgentObjectiveHandlerRegistry registry = new AgentObjectiveHandlerRegistry(Map.of(
                "quest", (entry, agent, objective, nowMs) -> AgentObjectiveAttachment.ATTACHED));
        AgentObjectiveDefinition objective = new AgentObjectiveDefinition(
                "training:1", "training", 10, 1_000L, 1,
                AgentObjectiveSource.PROGRESSION_POLICY, "v1", "run");

        assertThrows(UnsupportedOperationException.class,
                () -> registry.reconcileAndAttach(new AgentRuntimeEntry(null, null, null),
                        mock(Character.class), objective, 10L));
    }
}
