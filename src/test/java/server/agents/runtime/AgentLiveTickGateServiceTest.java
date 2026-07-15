package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;

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
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, leader, null);
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
                        (capabilityEntry, capabilityAgent) -> {
                            calls.add("capability");
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
        assertEquals(List.of("mapChange", "common", "capability", "trade", "idle", "recovery"), calls);
    }

    @Test
    void stopsWhenTradeWindowGateConsumesTick() {
        Character agent = mock(Character.class);
        Character leader = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, leader, null);
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
                        (capabilityEntry, capabilityAgent) -> {
                            calls.add("capability");
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
        assertEquals(List.of("mapChange", "common", "capability", "trade"), calls);
    }

    @Test
    void activeCapabilityRunsBeforeLegacyLiveGatesAndConsumesTick() {
        Character agent = mock(Character.class);
        Character leader = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, leader, null);
        List<String> calls = new ArrayList<>();

        boolean consumed = AgentLiveTickGateService.tickLiveGates(
                new AgentLiveTickGateService.Context(entry, agent, leader, leader, new Point(), true),
                new AgentLiveTickGateService.Hooks(
                        (commonEntry, commonAgent, commonLeader, runAiTick) -> {
                            calls.add("common");
                            return false;
                        },
                        (capabilityEntry, capabilityAgent) -> {
                            calls.add("capability");
                            return true;
                        },
                        (tradeEntry, tradeAgent) -> {
                            calls.add("trade");
                            return false;
                        },
                        (idleEntry, idleAgent) -> false,
                        (recoveryEntry, recoveryAgent, anchor, target) -> false,
                        (mapEntry, mapAgent) -> {
                            calls.add("mapChange");
                            return false;
                        }));

        assertTrue(consumed);
        assertEquals(List.of("mapChange", "common", "capability"), calls);
    }

    @Test
    void mapChangeIsGroundedBeforeCapabilityOrPhysicsWork() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        List<String> calls = new ArrayList<>();

        boolean consumed = AgentLiveTickGateService.tickLiveGates(
                new AgentLiveTickGateService.Context(entry, agent, null, null, new Point(), true),
                new AgentLiveTickGateService.Hooks(
                        (commonEntry, commonAgent, commonLeader, runAiTick) -> {
                            calls.add("common");
                            return false;
                        },
                        (capabilityEntry, capabilityAgent) -> {
                            calls.add("capability");
                            return false;
                        },
                        (tradeEntry, tradeAgent) -> false,
                        (idleEntry, idleAgent) -> false,
                        (recoveryEntry, recoveryAgent, anchor, target) -> false,
                        (mapEntry, mapAgent) -> {
                            calls.add("mapChange");
                            return true;
                        }));

        assertTrue(consumed);
        assertEquals(List.of("mapChange"), calls);
    }
}
