package server.agents.capabilities.townlife;

import client.Character;
import client.QuestStatus;
import org.junit.jupiter.api.Test;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTownLifeRuntimeTest {
    @Test
    void walksToShanksBeforeRunningTheRealNpcScript() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(12);
        when(agent.getMapId()).thenReturn(MapleIslandSouthperryQuestCatalog.FINAL_MAP_ID);
        when(agent.getPosition()).thenReturn(new Point(0, 0));
        when(agent.getChair()).thenReturn(-1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, agent, null);
        entry.capabilityStates().require(AgentTownLifeState.STATE_KEY).start(0L, 0);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        Point shanks = new Point(200, 0);
        when(gateway.npcPosition(agent, MapleIslandSouthperryQuestCatalog.SHANKS_NPC_ID))
                .thenReturn(shanks);
        when(gateway.grounded(agent)).thenReturn(true);

        long responseAtMs = entry.capabilityStates().require(AgentTownLifeState.STATE_KEY).nextActionAtMs();
        assertFalse(AgentTownLifeRuntime.tick(entry, agent, responseAtMs, gateway));

        verify(gateway).navigate(entry, shanks, true);
    }

    @Test
    void successfulShanksInteractionSettlesInLithBeforeChoosingActivities() {
        AtomicInteger mapId = new AtomicInteger(MapleIslandSouthperryQuestCatalog.FINAL_MAP_ID);
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(18);
        when(agent.getMapId()).thenAnswer(ignored -> mapId.get());
        when(agent.getPosition()).thenReturn(new Point(0, 0));
        when(agent.getChair()).thenReturn(-1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, agent, null);
        AgentTownLifeState state = entry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
        state.start(0L, 0);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        Point shanks = new Point(10, 0);
        when(gateway.npcPosition(agent, MapleIslandSouthperryQuestCatalog.SHANKS_NPC_ID))
                .thenReturn(shanks);
        when(gateway.grounded(agent)).thenReturn(true);
        doAnswer(ignored -> {
            mapId.set(LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID);
            return true;
        }).when(gateway).runNpcScript(agent, MapleIslandSouthperryQuestCatalog.SHANKS_NPC_ID);

        assertTrue(AgentTownLifeRuntime.tick(entry, agent, state.nextActionAtMs(), gateway));

        verify(gateway).runNpcScript(agent, MapleIslandSouthperryQuestCatalog.SHANKS_NPC_ID);
        assertEquals(AgentTownLifeState.Stage.COMPLETE_ISLAND_HANDOFF, state.stage());

        assertTrue(AgentTownLifeRuntime.tick(entry, agent, state.nextActionAtMs(), gateway));

        assertEquals(AgentTownLifeState.Stage.SETTLING, state.stage());
        assertTrue(state.nextActionAtMs() > 1L);
    }

    @Test
    void completesTheOutstandingBiggsHandoffBeforeTownLife() {
        AtomicInteger questStatus = new AtomicInteger(QuestStatus.Status.STARTED.getId());
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(22);
        when(agent.getMapId()).thenReturn(LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID);
        when(agent.getPosition()).thenReturn(new Point(0, 0));
        when(agent.getQuestStatus(MapleIslandSouthperryQuestCatalog.START_ONLY_BIGGS_STORY_QUEST_ID))
                .thenAnswer(ignored -> (byte) questStatus.get());
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, agent, null);
        AgentTownLifeState state = entry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
        state.start(0L, 0);
        state.transition(AgentTownLifeState.Stage.COMPLETE_ISLAND_HANDOFF, 0L);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        Point biggs = new Point(10, 0);
        when(gateway.npcPosition(agent, LithHarborTownLifeCatalog.BIGGS_NPC_ID)).thenReturn(biggs);
        when(gateway.grounded(agent)).thenReturn(true);
        when(gateway.canCompleteQuest(
                agent,
                MapleIslandSouthperryQuestCatalog.START_ONLY_BIGGS_STORY_QUEST_ID,
                LithHarborTownLifeCatalog.BIGGS_NPC_ID)).thenReturn(true);
        doAnswer(ignored -> {
            questStatus.set(QuestStatus.Status.COMPLETED.getId());
            return true;
        }).when(gateway).completeQuest(
                agent,
                MapleIslandSouthperryQuestCatalog.START_ONLY_BIGGS_STORY_QUEST_ID,
                LithHarborTownLifeCatalog.BIGGS_NPC_ID);

        assertTrue(AgentTownLifeRuntime.tick(entry, agent, 1L, gateway));

        verify(gateway).completeQuest(
                agent,
                MapleIslandSouthperryQuestCatalog.START_ONLY_BIGGS_STORY_QUEST_ID,
                LithHarborTownLifeCatalog.BIGGS_NPC_ID);
        assertEquals(AgentTownLifeState.Stage.SETTLING, state.stage());
    }
}
