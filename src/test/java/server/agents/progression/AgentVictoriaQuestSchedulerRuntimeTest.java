package server.agents.progression;

import client.Character;
import client.Job;
import client.QuestStatus;
import org.junit.jupiter.api.Test;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentVictoriaQuestSchedulerRuntimeTest {
    @Test
    void resumesAnIncompleteHuntingObjectiveWithoutReplayingQuestStart() {
        AgentVictoriaQuestRuntimeCatalog.Entry quest =
                AgentVictoriaQuestRuntimeCatalogRepository.defaultRepository().find(1115).orElseThrow();
        AgentVictoriaQuestRuntimeCatalog.HuntingObjective objective = quest.huntingObjectives().getFirst();
        AgentVictoriaQuestRuntimeCatalog.HuntMap huntMap = objective.huntMaps().getFirst();
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(92);
        when(agent.getLevel()).thenReturn(20);
        when(agent.getJob()).thenReturn(Job.THIEF);
        when(agent.getMapId()).thenReturn(huntMap.mapId());
        when(agent.getPosition()).thenReturn(new Point(0, 0));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        entry.capabilityStates().require(AgentVictoriaTrainingState.STATE_KEY).start(30, true, 0L);
        AgentVictoriaQuestSchedulerState state = entry.capabilityStates().require(
                AgentVictoriaQuestSchedulerState.STATE_KEY);
        state.begin(quest.questId(), quest.startMapIds().getFirst(),
                quest.completeMapIds().getFirst(), true);
        state.huntMapId(huntMap.mapId());
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.questStatus(agent, quest.questId()))
                .thenReturn(QuestStatus.Status.STARTED.getId());
        when(gateway.canCompleteQuest(agent, quest.questId(), quest.completeNpcId())).thenReturn(false);
        when(gateway.itemCount(agent, objective.targetId())).thenReturn(0);

        assertTrue(AgentVictoriaQuestSchedulerRuntime.tick(entry, agent, 100L, gateway));

        verify(gateway).grind(entry, Set.copyOf(huntMap.targetMobIds()));
    }
}
