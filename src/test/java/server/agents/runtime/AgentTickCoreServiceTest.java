package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.bots.BotEntry;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AgentTickCoreServiceTest {
    @Test
    void stopsWhenPreflightConsumesTick() {
        BotEntry entry = new BotEntry(mock(Character.class), mock(Character.class), null);
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
                (tickEntry, agent, leader) -> {
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
    void runsLivePathInLegacyOrder() {
        Character agent = mock(Character.class);
        Character leader = mock(Character.class);
        BotEntry entry = new BotEntry(agent, leader, null);
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
                (tickEntry, tickAgent, tickLeader) -> {
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
                "leader",
                "inactive",
                "dead",
                "context",
                "perf",
                "gate:true:true",
                "mode:123:true"), calls);
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
