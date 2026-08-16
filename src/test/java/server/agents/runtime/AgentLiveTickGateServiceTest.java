package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLiveTickGateServiceTest {
    @AfterEach
    void releaseExclusiveControl() {
        AgentExclusiveControlRuntime.clearForTests();
    }

    @Test
    void exclusiveControlTicksOnlyItsForegroundOwner() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(42);
        when(agent.getChair()).thenReturn(-1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        List<String> calls = new ArrayList<>();
        AgentExclusiveControlRuntime.claim(42, "economy:test");

        boolean consumed = AgentLiveTickGateService.tickLiveGates(
                new AgentLiveTickGateService.Context(entry, agent, null, null, new Point(), true),
                new AgentLiveTickGateService.Hooks(
                        (commonEntry, commonAgent, leader, runAi) -> { calls.add("common"); return false; },
                        (gateEntry, gateAgent, runAi) -> { calls.add("interlude"); return true; },
                        (supervisionEntry, supervisionAgent) -> { calls.add("supervision"); return false; },
                        (capabilityEntry, capabilityAgent) -> { calls.add("exclusive"); return true; },
                        (tradeEntry, tradeAgent) -> { calls.add("trade"); return false; },
                        (idleEntry, idleAgent) -> { calls.add("idle"); return false; },
                        (recoveryEntry, recoveryAgent, anchor, target) -> { calls.add("recovery"); return false; },
                        (mapEntry, mapAgent) -> { calls.add("mapChange"); return false; }));

        assertTrue(consumed);
        assertEquals(List.of("exclusive"), calls);
    }

    @Test
    void exclusiveControlYieldsOnlyToTheMovementPhaseWhenCapabilityDoesNotConsume() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(42);
        when(agent.getChair()).thenReturn(-1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        List<String> calls = new ArrayList<>();
        AgentExclusiveControlRuntime.claim(42, "economy:test");

        boolean consumed = AgentLiveTickGateService.tickLiveGates(
                new AgentLiveTickGateService.Context(entry, agent, null, null, new Point(), true),
                new AgentLiveTickGateService.Hooks(
                        (commonEntry, commonAgent, leader, runAi) -> { calls.add("common"); return false; },
                        (gateEntry, gateAgent, runAi) -> { calls.add("interlude"); return true; },
                        (supervisionEntry, supervisionAgent) -> { calls.add("supervision"); return false; },
                        (capabilityEntry, capabilityAgent) -> { calls.add("exclusive"); return false; },
                        (tradeEntry, tradeAgent) -> { calls.add("trade"); return false; },
                        (idleEntry, idleAgent) -> { calls.add("idle"); return false; },
                        (recoveryEntry, recoveryAgent, anchor, target) -> { calls.add("recovery"); return false; },
                        (mapEntry, mapAgent) -> { calls.add("mapChange"); return false; }));

        assertFalse(consumed);
        assertEquals(List.of("exclusive"), calls);
    }

    @Test
    void planExecutionGateCanResumeASeatedAgentBeforeChairShortCircuit() {
        Character agent = mock(Character.class);
        when(agent.getChair()).thenReturn(3010000);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        List<String> calls = new ArrayList<>();

        boolean consumed = AgentLiveTickGateService.tickLiveGates(
                new AgentLiveTickGateService.Context(entry, agent, null, null, new Point(), true),
                new AgentLiveTickGateService.Hooks(
                        (commonEntry, commonAgent, commonLeader, runAiTick) -> false,
                        (gateEntry, gateAgent, runAiTick) -> { calls.add("planGate"); return true; },
                        (supervisionEntry, supervisionAgent) -> false,
                        (capabilityEntry, capabilityAgent) -> { calls.add("capability"); return false; },
                        (tradeEntry, tradeAgent) -> false,
                        (idleEntry, idleAgent) -> false,
                        (recoveryEntry, recoveryAgent, anchor, target) -> false,
                        (mapEntry, mapAgent) -> { calls.add("mapChange"); return false; }));

        assertTrue(consumed);
        assertEquals(List.of("mapChange", "planGate"), calls);
    }

    @Test
    void seatedAgentRunsOnlyMapChangeAndCapabilityBeforeConsumingTick() {
        Character agent = mock(Character.class);
        when(agent.getChair()).thenReturn(3010000);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        List<String> calls = new ArrayList<>();

        boolean consumed = AgentLiveTickGateService.tickLiveGates(
                new AgentLiveTickGateService.Context(entry, agent, null, null, new Point(), true),
                new AgentLiveTickGateService.Hooks(
                        (commonEntry, commonAgent, commonLeader, runAiTick) -> {
                            calls.add("common");
                            return false;
                        },
                        (supervisionEntry, supervisionAgent) -> false,
                        (capabilityEntry, capabilityAgent) -> {
                            calls.add("capability");
                            return false;
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
        assertEquals(List.of("mapChange", "capability"), calls);
    }

    @Test
    void runsLiveGatesInLegacyOrderWhenNoneConsumesTick() {
        Character agent = mock(Character.class);
        when(agent.getChair()).thenReturn(-1);
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
                        (supervisionEntry, supervisionAgent) -> {
                            calls.add("supervision");
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
        assertEquals(List.of("mapChange", "common", "supervision", "capability", "trade", "idle", "recovery"), calls);
    }

    @Test
    void stopsWhenTradeWindowGateConsumesTick() {
        Character agent = mock(Character.class);
        when(agent.getChair()).thenReturn(-1);
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
                        (supervisionEntry, supervisionAgent) -> {
                            calls.add("supervision");
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
        assertEquals(List.of("mapChange", "common", "supervision", "capability", "trade"), calls);
    }

    @Test
    void activeCapabilityRunsBeforeLegacyLiveGatesAndConsumesTick() {
        Character agent = mock(Character.class);
        when(agent.getChair()).thenReturn(-1);
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
                        (supervisionEntry, supervisionAgent) -> {
                            calls.add("supervision");
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
        assertEquals(List.of("mapChange", "common", "supervision", "capability"), calls);
    }

    @Test
    void maintenanceSuppressesForegroundPlanButAllowsNormalMovementPhase() {
        Character agent = mock(Character.class);
        when(agent.getChair()).thenReturn(-1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        List<String> calls = new ArrayList<>();

        boolean consumed = AgentLiveTickGateService.tickLiveGates(
                new AgentLiveTickGateService.Context(entry, agent, null, null, new Point(), true),
                new AgentLiveTickGateService.Hooks(
                        (commonEntry, commonAgent, commonLeader, runAiTick) -> {
                            calls.add("common");
                            return false;
                        },
                        (supervisionEntry, supervisionAgent) -> {
                            calls.add("maintenance");
                            return true;
                        },
                        (capabilityEntry, capabilityAgent) -> {
                            calls.add("capability");
                            return true;
                        },
                        (tradeEntry, tradeAgent) -> { calls.add("trade"); return false; },
                        (idleEntry, idleAgent) -> { calls.add("idle"); return false; },
                        (recoveryEntry, recoveryAgent, anchor, target) -> false,
                        (mapEntry, mapAgent) -> { calls.add("mapChange"); return false; }));

        assertFalse(consumed);
        assertEquals(List.of("mapChange", "common", "maintenance"), calls);
    }

    @Test
    void foregroundRouteCombatCanConsumeBeforeUniversalPlanMovement() {
        Character agent = mock(Character.class);
        when(agent.getChair()).thenReturn(-1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        List<String> calls = new ArrayList<>();

        boolean consumed = AgentLiveTickGateService.tickLiveGates(
                new AgentLiveTickGateService.Context(
                        entry, agent, null, null, new Point(250, 10), true),
                new AgentLiveTickGateService.Hooks(
                        (commonEntry, commonAgent, commonLeader, runAiTick) -> {
                            calls.add("common");
                            return false;
                        },
                        (gateEntry, gateAgent, runAiTick) -> {
                            calls.add("planGate");
                            return false;
                        },
                        (supervisionEntry, supervisionAgent) -> {
                            calls.add("supervision");
                            return false;
                        },
                        (travelEntry, travelAgent, targetPosition, runAiTick) -> {
                            calls.add("routeCombat");
                            return true;
                        },
                        (capabilityEntry, capabilityAgent) -> false,
                        (tradeEntry, tradeAgent) -> false,
                        (idleEntry, idleAgent) -> false,
                        (recoveryEntry, recoveryAgent, anchor, target) -> false,
                        (mapEntry, mapAgent) -> {
                            calls.add("mapChange");
                            return false;
                        }));

        assertTrue(consumed);
        assertEquals(List.of("mapChange", "common", "supervision", "planGate", "routeCombat"), calls);
    }

    @Test
    void mapChangeIsGroundedBeforeCapabilityOrPhysicsWork() {
        Character agent = mock(Character.class);
        when(agent.getChair()).thenReturn(-1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        List<String> calls = new ArrayList<>();

        boolean consumed = AgentLiveTickGateService.tickLiveGates(
                new AgentLiveTickGateService.Context(entry, agent, null, null, new Point(), true),
                new AgentLiveTickGateService.Hooks(
                        (commonEntry, commonAgent, commonLeader, runAiTick) -> {
                            calls.add("common");
                            return false;
                        },
                        (supervisionEntry, supervisionAgent) -> false,
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
