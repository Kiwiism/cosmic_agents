package server.agents.progression;

import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.objectives.AgentObjectiveDefinition;
import server.agents.objectives.AgentObjectiveKernel;
import server.agents.objectives.AgentObjectiveSource;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentVictoriaTrainingObjectiveRuntimeTest {
    @Test
    void grindsOnlyNonHazardMobsOnSelectedMap() {
        Character agent = agent(77, 17, 104010001);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        assertTrue(AgentVictoriaTrainingObjectiveRuntime.start(entry, agent, 30, 100L));
        entry.capabilityStates().require(AgentVictoriaTrainingState.STATE_KEY).selected(
                104010001, 17, "test", 100L);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);

        assertTrue(AgentVictoriaTrainingObjectiveRuntime.tick(entry, agent, 200L, gateway));

        verify(gateway).grind(entry, Set.of(1210100, 1210101));
    }

    @Test
    void restoresTargetFromDurableObjectiveAndCompletesFromLiveLevel() {
        Character agent = agent(78, 30, 100000000);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentObjectiveKernel.start(entry, new AgentObjectiveDefinition(
                "victoria:training:78:30", AgentVictoriaTrainingObjectiveRuntime.OBJECTIVE_TYPE,
                80, Long.MAX_VALUE, 5, AgentObjectiveSource.OPERATOR_COMMAND,
                "victoria-level15-30-training-v1", "78:level-30"), 100L);

        assertFalse(AgentVictoriaTrainingObjectiveRuntime.tick(
                entry, agent, 200L, mock(PrimitiveCapabilityGateway.class)));

        assertNull(AgentObjectiveKernel.active(entry));
        assertFalse(entry.capabilityStates().require(AgentVictoriaTrainingState.STATE_KEY).active());
    }

    private static Character agent(int id, int level, int mapId) {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(id);
        when(agent.getName()).thenReturn("Trainer" + id);
        when(agent.getJob()).thenReturn(Job.THIEF);
        when(agent.getLevel()).thenReturn(level);
        when(agent.getMapId()).thenReturn(mapId);
        when(agent.getPosition()).thenReturn(new Point(0, 0));
        return agent;
    }
}
