package server.agents.capabilities.supplies;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.contracts.AgentProcurementMethod;
import server.agents.capabilities.contracts.AgentProcurementRequest;
import server.agents.capabilities.contracts.AgentResourceCategory;
import server.agents.capabilities.contracts.AgentSupplyNeed;
import server.agents.capabilities.contracts.AgentSupplyUrgency;
import server.agents.objectives.AgentObjectiveDefinition;
import server.agents.objectives.AgentObjectiveKernel;
import server.agents.objectives.AgentObjectiveSource;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSupplyProcurementRuntimeTest {
    @Test
    void returningFromSupplierResumesExactSuspendedObjective() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(91);
        when(agent.getMapId()).thenReturn(101010101);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentObjectiveDefinition training = new AgentObjectiveDefinition(
                "victoria:training:91:30", "progression.victoria-training", 80,
                Long.MAX_VALUE, 5, AgentObjectiveSource.PROGRESSION_POLICY,
                "victoria-v1", "91:level-30");
        AgentObjectiveDefinition maintenance = new AgentObjectiveDefinition(
                "maintenance:resupply:supply:HP_POTION:100", "maintenance.resupply", 1_000,
                10_000L, 2, AgentObjectiveSource.RECOVERY_POLICY,
                "supply-procurement-v2", training.objectiveId());
        AgentObjectiveKernel.start(entry, training, 10L);
        AgentObjectiveKernel.suspendFor(entry, maintenance, "HP potions critical", 20L);

        AgentResourcePlanningState planning = entry.capabilityStates().require(
                AgentResourcePlanningState.STATE_KEY);
        AgentSupplyNeed need = new AgentSupplyNeed(AgentResourceCategory.HP_POTION,
                0, 20, AgentSupplyUrgency.EMPTY, training.objectiveId(), 100L);
        AgentProcurementRequest request = new AgentProcurementRequest(
                "supply:HP_POTION:100", AgentResourceCategory.HP_POTION, 20, 10_000L,
                List.of(AgentProcurementMethod.NPC_SHOP), AgentSupplyUrgency.EMPTY,
                training.objectiveId(), 10_000L);
        planning.update(need, request);
        entry.capabilityStates().require(AgentSupplyProcurementState.STATE_KEY).start(
                request.requestId(), maintenance.objectiveId(), request.category(),
                101000002, 1032102, 101010101, AgentSupplyProcurementState.Phase.RETURNING);

        assertFalse(AgentSupplyProcurementRuntime.tick(entry, agent, 200L));

        assertEquals(training, AgentObjectiveKernel.active(entry));
        assertNull(planning.procurement(AgentResourceCategory.HP_POTION));
    }
}
