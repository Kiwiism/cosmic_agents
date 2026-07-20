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
        boolean tick(AgentRuntimeEntry entry, Character agent);
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
        beginFrame(entry, leaderCharId, agentCharId, hooks).runToCompletion();
    }

    public static Frame beginFrame(AgentRuntimeEntry entry,
                                   int leaderCharId,
                                   int agentCharId,
                                   Hooks hooks) {
        if (entry == null || hooks == null) {
            throw new IllegalArgumentException("Agent tick entry and hooks are required");
        }
        return new Frame(entry, leaderCharId, agentCharId, hooks);
    }

    public static final class Frame implements AgentTickFrame {
        private final AgentRuntimeEntry entry;
        private final int leaderCharId;
        private final int agentCharId;
        private final Hooks hooks;
        private AgentTickSliceKind nextSlice = AgentTickSliceKind.PREFLIGHT;
        private long nowMs;
        private Character agent;
        private Character leader;
        private boolean runAiTick;
        private AgentLiveTickContextService.Context liveContext;
        private Character followAnchor;
        private boolean performanceEnabled;
        private boolean complete;

        private Frame(AgentRuntimeEntry entry, int leaderCharId, int agentCharId, Hooks hooks) {
            this.entry = entry;
            this.leaderCharId = leaderCharId;
            this.agentCharId = agentCharId;
            this.hooks = hooks;
        }

        @Override
        public AgentTickSliceResult runNextSlice() {
            if (complete) {
                throw new IllegalStateException("Agent tick frame is already complete");
            }
            AgentTickSliceKind completedSlice = nextSlice;
            switch (nextSlice) {
                case PREFLIGHT -> runPreflight();
                case LIFECYCLE -> runLifecycle();
                case PLAN_AND_GATES -> runPlanAndGates();
                case CAPABILITY_AND_MOVEMENT -> runCapabilityAndMovement();
            }
            return new AgentTickSliceResult(
                    completedSlice,
                    complete ? AgentTickNextRunHint.NORMAL_CADENCE : AgentTickNextRunHint.IMMEDIATE_CONTINUATION,
                    complete);
        }

        public void runToCompletion() {
            while (!complete) {
                runNextSlice();
            }
        }

        @Override
        public boolean isComplete() {
            return complete;
        }

        private void runPreflight() {
            nowMs = hooks.nowMillis().get();
            AgentTickPreflightService.Result preflight = hooks.preflightRunner().run(entry, agentCharId, nowMs);
            if (preflight.consumedTick()) {
                complete = true;
                return;
            }
            agent = preflight.agent();
            runAiTick = preflight.runAiTick();
            nextSlice = AgentTickSliceKind.LIFECYCLE;
        }

        private void runLifecycle() {
            if (hooks.deadTick().tick(entry, agent)) {
                complete = true;
                return;
            }
            leader = hooks.leaderResolver().resolve(entry, leaderCharId);
            // Logging out removes the leader before this agent's next tick. Preserve a seated
            // agent as-is instead of routing it through ownerless idle physics, which changes
            // the stored stance to standing and is then exposed to the client on relog.
            if (leader == null && agent.getChair() > 0) {
                complete = true;
                return;
            }
            if (hooks.inactiveLeaderTick().tick(entry, agent, leader, nowMs, leaderCharId)) {
                complete = true;
                return;
            }
            if (leader == null) {
                hooks.ownerlessTick().tick(entry, agent, runAiTick);
                complete = true;
                return;
            }
            nextSlice = AgentTickSliceKind.PLAN_AND_GATES;
        }

        private void runPlanAndGates() {
            liveContext = hooks.liveContextPreparer().prepare(entry, agent, leader);
            followAnchor = liveContext.followAnchor();
            performanceEnabled = hooks.performanceEnabled().getAsBoolean();
            if (hooks.liveGateRunner().tick(
                    entry,
                    agent,
                    leader,
                    followAnchor,
                    liveContext,
                    runAiTick,
                    performanceEnabled)) {
                complete = true;
                return;
            }
            nextSlice = AgentTickSliceKind.CAPABILITY_AND_MOVEMENT;
        }

        private void runCapabilityAndMovement() {
            hooks.liveModeRunner().tick(
                    entry,
                    agent,
                    followAnchor,
                    liveContext,
                    runAiTick,
                    nowMs,
                    performanceEnabled);
            complete = true;
        }
    }
}
