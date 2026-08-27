package server.agents.progression;

import client.Character;
import client.QuestStatus;
import org.junit.jupiter.api.Test;
import server.agents.integration.PrimitiveCapabilityGateway;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMushroomKingdomInvariantRecoveryTest {
    @Test
    void grantsRareHelmetPepeItemAfterFiftyVerifiedKills() {
        Character agent = mock(Character.class);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        AgentMushroomKingdomState state = new AgentMushroomKingdomState();
        state.begin(1L);
        state.observe(2326, 0, 106021100, new java.awt.Point(), 2L);
        for (int kill = 0; kill < 50; kill++) state.recordHelmetPepeKill();
        Map<Integer, Integer> items = new HashMap<>();
        when(gateway.itemCount(eq(agent), anyInt())).thenAnswer(call ->
                items.getOrDefault(call.getArgument(1), 0));
        when(gateway.grantItem(eq(agent), anyInt(), anyInt())).thenAnswer(call -> {
            items.merge(call.getArgument(1), call.getArgument(2), Integer::sum);
            return true;
        });

        AgentMushroomKingdomInvariantRecovery.Result result =
                AgentMushroomKingdomInvariantRecovery.recover(agent, state, gateway);

        assertTrue(result.recovered());
        assertEquals(1, items.get(4001317));
    }

    @Test
    void synthesizesWeddingKeyOnlyAfterAllThreeYetiCredits() {
        Character agent = mock(Character.class);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        Map<Integer, Integer> items = new HashMap<>();
        when(gateway.questProgress(eq(agent), eq(2330), anyInt())).thenReturn(1);
        when(gateway.itemCount(eq(agent), anyInt())).thenAnswer(call ->
                items.getOrDefault(call.getArgument(1), 0));
        when(gateway.grantItem(eq(agent), anyInt(), anyInt())).thenAnswer(call -> {
            items.merge(call.getArgument(1), call.getArgument(2), Integer::sum);
            return true;
        });

        AgentMushroomKingdomInvariantRecovery.Result result =
                AgentMushroomKingdomInvariantRecovery.recover(
                        agent, new AgentMushroomKingdomState(), gateway);

        assertTrue(result.recovered());
        assertEquals(1, items.get(4032388));
    }

    @Test
    void repairsTruthQuestOrderingAndBothStartItems() {
        Character agent = mock(Character.class);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        Map<Integer, Integer> statuses = new HashMap<>();
        Map<Integer, Integer> items = new HashMap<>();
        statuses.put(2334, QuestStatus.Status.COMPLETED.getId());
        statuses.put(2331, QuestStatus.Status.STARTED.getId());
        when(gateway.questStatus(eq(agent), anyInt())).thenAnswer(call ->
                statuses.getOrDefault(call.getArgument(1), QuestStatus.Status.NOT_STARTED.getId()));
        when(gateway.itemCount(eq(agent), anyInt())).thenAnswer(call ->
                items.getOrDefault(call.getArgument(1), 0));
        doAnswer(call -> {
            statuses.put(call.getArgument(1), QuestStatus.Status.STARTED.getId());
            return true;
        }).when(gateway).forceStartQuest(eq(agent), eq(2336), eq(1300002));
        when(gateway.grantItem(eq(agent), anyInt(), anyInt())).thenAnswer(call -> {
            items.merge(call.getArgument(1), call.getArgument(2), Integer::sum);
            return true;
        });

        AgentMushroomKingdomInvariantRecovery.Result result =
                AgentMushroomKingdomInvariantRecovery.recover(
                        agent, new AgentMushroomKingdomState(), gateway);

        assertTrue(result.recovered());
        assertEquals(QuestStatus.Status.STARTED.getId(), statuses.get(2336));
        assertEquals(1, items.get(4032387));
        assertEquals(1, items.get(4032386));
    }
}
