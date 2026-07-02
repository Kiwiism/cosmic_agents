package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.bots.BotEntry;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentLiveTickGateServiceTest {
    @Test
    void runsLiveGatesInLegacyOrderWhenNoneConsumesTick() {
        Character agent = mock(Character.class);
        Character leader = mock(Character.class);
        Character followAnchor = mock(Character.class);
        BotEntry entry = new BotEntry(agent, leader, null);
        List<String> calls = new ArrayList<>();

        boolean consumed = AgentLiveTickGateService.tickLiveGates(
                new AgentLiveTickGateService.Context(
                        entry,
                        agent,
                        leader,
                        followAnchor,
                        new Point(10, 20),
                        true),
                new AgentLiveTickGateService.Hooks(
                        (commonEntry, commonAgent, commonLeader, runAiTick) -> {
                            calls.add("common");
                            return false;
                        },
                        (tradeEntry, tradeAgent) -> {
                            calls.add("trade");
                            return false;
                        },
                        (idleEntry, idleAgent) -> {
                            calls.add("idle");
                            return false;
                        },
                        (recoveryEntry, recoveryAgent, recoveryFollowAnchor, targetPosition) -> {
                            calls.add("recovery");
                            return false;
                        },
                        (mapEntry, mapAgent) -> {
                            calls.add("mapChange");
                            return false;
                        }));

        assertFalse(consumed);
        assertEquals(List.of("common", "trade", "idle", "recovery", "mapChange"), calls);
    }

    @Test
    void stopsWhenTradeWindowGateConsumesTick() {
        Character agent = mock(Character.class);
        Character leader = mock(Character.class);
        BotEntry entry = new BotEntry(agent, leader, null);
        List<String> calls = new ArrayList<>();

        boolean consumed = AgentLiveTickGateService.tickLiveGates(
                new AgentLiveTickGateService.Context(
                        entry,
                        agent,
                        leader,
                        leader,
                        new Point(10, 20),
                        true),
                new AgentLiveTickGateService.Hooks(
                        (commonEntry, commonAgent, commonLeader, runAiTick) -> {
                            calls.add("common");
                            return false;
                        },
                        (tradeEntry, tradeAgent) -> {
                            calls.add("trade");
                            return true;
                        },
                        (idleEntry, idleAgent) -> {
                            calls.add("idle");
                            return false;
                        },
                        (recoveryEntry, recoveryAgent, recoveryFollowAnchor, targetPosition) -> {
                            calls.add("recovery");
                            return false;
                        },
                        (mapEntry, mapAgent) -> {
                            calls.add("mapChange");
                            return false;
                        }));

        assertTrue(consumed);
        assertEquals(List.of("common", "trade"), calls);
    }
}
