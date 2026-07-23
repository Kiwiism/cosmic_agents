package server.agents.progression;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentVictoriaPlanSessionStateTest {
    @Test
    void restoredCareerStateDoesNotActivateVictoriaExecution() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        entry.capabilityStates().require(AgentCareerProgressionState.STATE_KEY)
                .assign(AgentCareerBuildBundleRepository.defaultRepository()
                        .find("thief-dagger-standard-v1").orElseThrow());

        assertFalse(AgentVictoriaPlanSessionRuntime.active(entry));
        assertEquals(AgentVictoriaPlanSessionState.Plan.NONE,
                AgentVictoriaPlanSessionRuntime.plan(entry));
    }

    @Test
    void explicitSessionOwnsOnlyTheSelectedVictoriaPlan() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);

        AgentVictoriaPlanSessionRuntime.startFirstJob(entry, agent);
        assertTrue(AgentVictoriaPlanSessionRuntime.active(entry));
        assertEquals(AgentVictoriaPlanSessionState.Plan.FIRST_JOB,
                AgentVictoriaPlanSessionRuntime.plan(entry));

        AgentVictoriaPlanSessionRuntime.startTraining(entry, agent);
        assertEquals(AgentVictoriaPlanSessionState.Plan.TRAINING,
                AgentVictoriaPlanSessionRuntime.plan(entry));

        AgentVictoriaPlanSessionRuntime.stop(entry);
        assertFalse(AgentVictoriaPlanSessionRuntime.active(entry));
    }
}
