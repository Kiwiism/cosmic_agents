package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.Trade;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCommonTickServiceTest {
    @Test
    void entersDeadStateAndConsumesTickBeforeLaterSystems() {
        Scenario scenario = new Scenario();
        when(scenario.agent.getHp()).thenReturn(0);

        boolean consumed = AgentCommonTickService.runCommonTickSystems(
                scenario.entry, scenario.agent, scenario.leader, true, scenario.hooks());

        assertTrue(consumed);
        assertEquals(List.of("mobDamage", "isDead", "enterDead"), scenario.calls);
    }

    @Test
    void skipsPassiveLootWhileTradeWindowIsOpen() {
        Scenario scenario = new Scenario();
        when(scenario.agent.getTrade()).thenReturn(mock(Trade.class));

        boolean consumed = AgentCommonTickService.runCommonTickSystems(
                scenario.entry, scenario.agent, scenario.leader, false, scenario.hooks());

        assertFalse(consumed);
        assertFalse(scenario.calls.contains("passiveLoot"));
        assertTrue(scenario.calls.contains("criticalSurvivalBuff"));
        assertEquals("actionLocked", scenario.calls.get(scenario.calls.size() - 1));
    }

    @Test
    void npcLockConsumesTickBeforeActionLockAndAiSystems() {
        Scenario scenario = new Scenario();
        scenario.npcLocked = true;

        boolean consumed = AgentCommonTickService.runCommonTickSystems(
                scenario.entry, scenario.agent, scenario.leader, true, scenario.hooks());

        assertTrue(consumed);
        assertFalse(scenario.calls.contains("actionLock"));
        assertFalse(scenario.calls.contains("skillCache"));
        assertEquals("scriptTasks", scenario.calls.get(scenario.calls.size() - 1));
    }

    @Test
    void runsAiSystemsOnlyWhenDueAndReturnsActionLockedResult() {
        Scenario scenario = new Scenario();
        scenario.actionLocked = true;

        boolean consumed = AgentCommonTickService.runCommonTickSystems(
                scenario.entry, scenario.agent, scenario.leader, true, scenario.hooks());

        assertTrue(consumed);
        assertTrue(scenario.calls.contains("skillCache"));
        assertTrue(scenario.calls.contains("supportHeal"));
        assertTrue(scenario.calls.contains("combatBuffs"));
        assertTrue(scenario.calls.contains("buffPots"));
        assertEquals("actionLocked", scenario.calls.get(scenario.calls.size() - 1));
    }

    private static final class Scenario {
        private final Character agent = mock(Character.class);
        private final Character leader = mock(Character.class);
        private final AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, leader, null);
        private final List<String> calls = new ArrayList<>();
        private boolean dead;
        private boolean npcLocked;
        private boolean actionLocked;

        private Scenario() {
            when(agent.getHp()).thenReturn(1);
            when(agent.getTrade()).thenReturn(null);
        }

        private AgentCommonTickService.CommonTickHooks hooks() {
            return new AgentCommonTickService.CommonTickHooks(
                    (entry, agent) -> calls.add("mobDamage"),
                    (entry, agent) -> {
                        calls.add("isDead");
                        return dead;
                    },
                    (entry, agent) -> calls.add("enterDead"),
                    agent -> calls.add("releaseMonsters"),
                    (entry, agent) -> calls.add("passiveLoot"),
                    (entry, agent) -> calls.add("potionCheck"),
                    (entry, agent) -> calls.add("passiveRecovery"),
                    (entry, agent) -> calls.add("criticalSurvivalBuff"),
                    (entry, agent) -> calls.add("levelUp"),
                    (entry, agent, leader) -> calls.add("afk"),
                    (entry, agent) -> calls.add("trade"),
                    (entry, agent) -> calls.add("manualTrade"),
                    (entry, agent, leader) -> calls.add("pq"),
                    entry -> calls.add("scriptTasks"),
                    entry -> npcLocked,
                    entry -> calls.add("actionLock"),
                    (entry, agent) -> calls.add("skillCache"),
                    (entry, agent) -> calls.add("supportHeal"),
                    (entry, agent) -> calls.add("combatBuffs"),
                    (entry, agent) -> calls.add("buffPots"),
                    entry -> {
                        calls.add("actionLocked");
                        return actionLocked;
                    });
        }
    }
}
