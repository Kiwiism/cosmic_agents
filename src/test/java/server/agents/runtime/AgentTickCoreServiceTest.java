package server.agents.runtime;

import server.agents.capabilities.movement.AgentTargetSnapshot;
import server.agents.capabilities.movement.AgentFormationService;
import client.Character;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTickCoreServiceTest {
    @Test
    void ownerLogoutPreservesSeatedPoseWithoutRunningOwnerlessPhysics() {
        Character agent = mock(Character.class);
        when(agent.getChair()).thenReturn(3010000);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        List<String> calls = new ArrayList<>();

        AgentTickCoreService.tickCore(entry, 1, 2, new AgentTickCoreService.Hooks(
                () -> 123L,
                (tickEntry, agentCharId, nowMs) -> {
                    calls.add("preflight");
                    return new AgentTickPreflightService.Result(false, agent, true);
                },
                (tickEntry, leaderCharId) -> {
                    calls.add("leader");
                    return null;
                },
                (tickEntry, tickAgent, leader, nowMs, leaderCharId) -> {
                    calls.add("inactive");
                    return false;
                },
                (tickEntry, tickAgent, runAiTick) -> calls.add("ownerless"),
                (tickEntry, tickAgent) -> false,
                (tickEntry, tickAgent, leader) -> liveContext(),
                () -> false,
                (tickEntry, tickAgent, leader, anchor, context, runAiTick, perf) -> false,
                (tickEntry, tickAgent, anchor, context, runAiTick, nowMs, perf) -> calls.add("mode")));

        assertEquals(List.of("preflight", "leader"), calls);
    }

    @Test
    void stopsWhenPreflightConsumesTick() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        List<String> calls = new ArrayList<>();

        AgentTickCoreService.tickCore(entry, 1, 2, new AgentTickCoreService.Hooks(
                () -> 123L,
                (tickEntry, agentCharId, nowMs) -> {
                    calls.add("preflight");
                    return new AgentTickPreflightService.Result(true, null, false);
                },
                (tickEntry, leaderCharId) -> {
                    calls.add("leader");
                    return null;
                },
                (tickEntry, agent, leader, nowMs, leaderCharId) -> {
                    calls.add("inactive");
                    return false;
                },
                (tickEntry, agent, runAiTick) -> calls.add("ownerless"),
                (tickEntry, agent) -> {
                    calls.add("dead");
                    return false;
                },
                (tickEntry, agent, leader) -> {
                    calls.add("context");
                    return liveContext();
                },
                () -> false,
                (tickEntry, agent, leader, followAnchor, liveContext, runAiTick, perf) -> {
                    calls.add("gate");
                    return false;
                },
                (tickEntry, agent, followAnchor, liveContext, runAiTick, nowMs, perf) -> calls.add("mode")));

        assertEquals(List.of("preflight"), calls);
    }

    @Test
    void deadOwnerlessAgentRecoversBeforeLeaderResolution() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        List<String> calls = new ArrayList<>();

        AgentTickCoreService.tickCore(entry, 1, 2, new AgentTickCoreService.Hooks(
                () -> 123L,
                (tickEntry, agentCharId, nowMs) -> {
                    calls.add("preflight");
                    return new AgentTickPreflightService.Result(false, agent, true);
                },
                (tickEntry, leaderCharId) -> {
                    calls.add("leader");
                    return null;
                },
                (tickEntry, tickAgent, leader, nowMs, leaderCharId) -> {
                    calls.add("inactive");
                    return false;
                },
                (tickEntry, tickAgent, runAiTick) -> calls.add("ownerless"),
                (tickEntry, tickAgent) -> {
                    calls.add("dead");
                    return true;
                },
                (tickEntry, tickAgent, leader) -> {
                    calls.add("context");
                    return liveContext();
                },
                () -> false,
                (tickEntry, tickAgent, leader, anchor, context, runAiTick, perf) -> false,
                (tickEntry, tickAgent, anchor, context, runAiTick, nowMs, perf) -> calls.add("mode")));

        assertEquals(List.of("preflight", "dead"), calls);
    }

    @Test
    void runsLivePathInLegacyOrder() {
        Character agent = mock(Character.class);
        Character leader = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, leader, null);
        AgentLiveTickContextService.Context liveContext = liveContext();
        List<String> calls = new ArrayList<>();

        AgentTickCoreService.tickCore(entry, 1, 2, new AgentTickCoreService.Hooks(
                () -> 123L,
                (tickEntry, agentCharId, nowMs) -> {
                    calls.add("preflight:" + nowMs);
                    return new AgentTickPreflightService.Result(false, agent, true);
                },
                (tickEntry, leaderCharId) -> {
                    calls.add("leader");
                    return leader;
                },
                (tickEntry, tickAgent, tickLeader, nowMs, leaderCharId) -> {
                    calls.add("inactive");
                    return false;
                },
                (tickEntry, tickAgent, runAiTick) -> calls.add("ownerless"),
                (tickEntry, tickAgent) -> {
                    calls.add("dead");
                    return false;
                },
                (tickEntry, tickAgent, tickLeader) -> {
                    calls.add("context");
                    return liveContext;
                },
                () -> {
                    calls.add("perf");
                    return true;
                },
                (tickEntry, tickAgent, tickLeader, followAnchor, context, runAiTick, perf) -> {
                    calls.add("gate:" + runAiTick + ":" + perf);
                    return false;
                },
                (tickEntry, tickAgent, followAnchor, context, runAiTick, nowMs, perf) ->
                        calls.add("mode:" + nowMs + ":" + perf)));

        assertEquals(List.of(
                "preflight:123",
                "dead",
                "leader",
                "inactive",
                "context",
                "perf",
                "gate:true:true",
                "mode:123:true"), calls);
    }

    @Test
    void exposesBoundedSlicesWithoutChangingLiveOrder() {
        Character agent = mock(Character.class);
        Character leader = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, leader, null);
        List<String> calls = new ArrayList<>();
        AgentTickCoreService.Frame frame = AgentTickCoreService.beginFrame(
                entry,
                1,
                2,
                liveHooks(agent, leader, calls));

        AgentTickSliceResult preflight = frame.runNextSlice();
        assertEquals(AgentTickSliceKind.PREFLIGHT, preflight.completedSlice());
        assertEquals(AgentTickNextRunHint.IMMEDIATE_CONTINUATION, preflight.nextRunHint());
        assertFalse(frame.isComplete());

        assertEquals(AgentTickSliceKind.LIFECYCLE, frame.runNextSlice().completedSlice());
        assertEquals(AgentTickSliceKind.PLAN_AND_GATES, frame.runNextSlice().completedSlice());
        AgentTickSliceResult movement = frame.runNextSlice();

        assertEquals(AgentTickSliceKind.CAPABILITY_AND_MOVEMENT, movement.completedSlice());
        assertEquals(AgentTickNextRunHint.NORMAL_CADENCE, movement.nextRunHint());
        assertTrue(frame.isComplete());
        assertEquals(List.of(
                "preflight:123",
                "dead",
                "leader",
                "inactive",
                "context",
                "perf",
                "gate:true:true",
                "mode:123:true"), calls);
    }

    private static AgentTickCoreService.Hooks liveHooks(Character agent,
                                                        Character leader,
                                                        List<String> calls) {
        AgentLiveTickContextService.Context liveContext = liveContext();
        return new AgentTickCoreService.Hooks(
                () -> 123L,
                (tickEntry, agentCharId, nowMs) -> {
                    calls.add("preflight:" + nowMs);
                    return new AgentTickPreflightService.Result(false, agent, true);
                },
                (tickEntry, leaderCharId) -> {
                    calls.add("leader");
                    return leader;
                },
                (tickEntry, tickAgent, tickLeader, nowMs, leaderCharId) -> {
                    calls.add("inactive");
                    return false;
                },
                (tickEntry, tickAgent, runAiTick) -> calls.add("ownerless"),
                (tickEntry, tickAgent) -> {
                    calls.add("dead");
                    return false;
                },
                (tickEntry, tickAgent, tickLeader) -> {
                    calls.add("context");
                    return liveContext;
                },
                () -> {
                    calls.add("perf");
                    return true;
                },
                (tickEntry, tickAgent, tickLeader, followAnchor, context, runAiTick, perf) -> {
                    calls.add("gate:" + runAiTick + ":" + perf);
                    return false;
                },
                (tickEntry, tickAgent, followAnchor, context, runAiTick, nowMs, perf) ->
                        calls.add("mode:" + nowMs + ":" + perf));
    }

    private static AgentLiveTickContextService.Context liveContext() {
        Character followAnchor = mock(Character.class);
        AgentTargetSnapshot snapshot = new AgentTargetSnapshot(
                new AgentFormationService.FormationState(AgentFormationService.FormationType.STAGGER, 0, 0),
                new Point(1, 1),
                new Point(1, 1),
                "leader",
                new Point(1, 1),
                new Point(2, 2),
                null,
                null,
                null,
                new Point(2, 2),
                "follow");
        return new AgentLiveTickContextService.Context(new Point(0, 0), followAnchor, snapshot, new Point(2, 2));
    }
}
