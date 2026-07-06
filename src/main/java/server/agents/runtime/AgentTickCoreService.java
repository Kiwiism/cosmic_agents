package server.agents.runtime;

import client.Character;

import java.util.function.BooleanSupplier;

public final class AgentTickCoreService {
    private AgentTickCoreService() {
    }

    public record Hooks(NowMillis nowMillis,
                        PreflightRunner preflightRunner,
                        LeaderResolver leaderResolver,
                        InactiveLeaderTick inactiveLeaderTick,
                        OwnerlessTick ownerlessTick,
                        DeadTick deadTick,
                        LiveContextPreparer liveContextPreparer,
                        BooleanSupplier performanceEnabled,
                        LiveGateRunner liveGateRunner,
                        LiveModeRunner liveModeRunner) {
    }

    @FunctionalInterface
    public interface NowMillis {
        long get();
    }

    @FunctionalInterface
    public interface PreflightRunner {
        AgentTickPreflightService.Result run(AgentRuntimeEntry entry, int agentCharId, long nowMs);
    }

    @FunctionalInterface
    public interface LeaderResolver {
        Character resolve(AgentRuntimeEntry entry, int leaderCharId);
    }

    @FunctionalInterface
    public interface InactiveLeaderTick {
        boolean tick(AgentRuntimeEntry entry, Character agent, Character leader, long nowMs, int leaderCharId);
    }

    @FunctionalInterface
    public interface OwnerlessTick {
        void tick(AgentRuntimeEntry entry, Character agent, boolean runAiTick);
    }

    @FunctionalInterface
    public interface DeadTick {
        boolean tick(AgentRuntimeEntry entry, Character agent, Character leader);
    }

    @FunctionalInterface
    public interface LiveContextPreparer {
        AgentLiveTickContextService.Context prepare(AgentRuntimeEntry entry, Character agent, Character leader);
    }

    @FunctionalInterface
    public interface LiveGateRunner {
        boolean tick(AgentRuntimeEntry entry,
                     Character agent,
                     Character leader,
                     Character followAnchor,
                     AgentLiveTickContextService.Context liveContext,
                     boolean runAiTick,
                     boolean performanceEnabled);
    }

    @FunctionalInterface
    public interface LiveModeRunner {
        void tick(AgentRuntimeEntry entry,
                  Character agent,
                  Character followAnchor,
                  AgentLiveTickContextService.Context liveContext,
                  boolean runAiTick,
                  long nowMs,
                  boolean performanceEnabled);
    }

    public static void tickCore(AgentRuntimeEntry entry, int leaderCharId, int agentCharId, Hooks hooks) {
        long nowMs = hooks.nowMillis().get();
        AgentTickPreflightService.Result preflight = hooks.preflightRunner().run(entry, agentCharId, nowMs);
        if (preflight.consumedTick()) {
            return;
        }
        Character agent = preflight.agent();
        boolean runAiTick = preflight.runAiTick();

        Character leader = hooks.leaderResolver().resolve(entry, leaderCharId);
        if (hooks.inactiveLeaderTick().tick(entry, agent, leader, nowMs, leaderCharId)) {
            return;
        }
        if (leader == null) {
            hooks.ownerlessTick().tick(entry, agent, runAiTick);
            return;
        }

        if (hooks.deadTick().tick(entry, agent, leader)) {
            return;
        }

        AgentLiveTickContextService.Context liveContext = hooks.liveContextPreparer().prepare(entry, agent, leader);
        Character followAnchor = liveContext.followAnchor();
        boolean perf = hooks.performanceEnabled().getAsBoolean();

        if (hooks.liveGateRunner().tick(entry, agent, leader, followAnchor, liveContext, runAiTick, perf)) {
            return;
        }
        hooks.liveModeRunner().tick(entry, agent, followAnchor, liveContext, runAiTick, nowMs, perf);
    }
}
