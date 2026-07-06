package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;

public final class AgentTickPreflightService {
    private AgentTickPreflightService() {
    }

    public record Result(boolean consumedTick, Character agent, boolean runAiTick) {
    }

    public record Hooks(AirshowState airshowState,
                        SkipDelayConsumer skipDelayConsumer,
                        RemovedAgentCleanup removedAgentCleanup,
                        HeartbeatTick heartbeatTick,
                        PendingOfferExpiry pendingOfferExpiry,
                        AiTickPreparation aiTickPreparation,
                        int movementTickMs,
                        int aiTickMs,
                        long heartbeatIntervalMs) {
    }

    @FunctionalInterface
    public interface AirshowState {
        boolean active(AgentRuntimeEntry entry);
    }

    @FunctionalInterface
    public interface SkipDelayConsumer {
        boolean consume(AgentRuntimeEntry entry, int movementTickMs);
    }

    @FunctionalInterface
    public interface RemovedAgentCleanup {
        void remove(int agentCharId);
    }

    @FunctionalInterface
    public interface HeartbeatTick {
        void tick(AgentRuntimeEntry entry, Character agent, long nowMs, long intervalMs);
    }

    @FunctionalInterface
    public interface PendingOfferExpiry {
        void expire(AgentRuntimeEntry entry);
    }

    @FunctionalInterface
    public interface AiTickPreparation {
        boolean prepare(AgentRuntimeEntry entry, int movementTickMs, int aiTickMs, long tickAtMs);
    }

    public static Result runPreflight(AgentRuntimeEntry entry,
                                      int agentCharId,
                                      long nowMs,
                                      Hooks hooks) {
        if (entry == null) {
            return new Result(true, null, false);
        }
        if (hooks.airshowState().active(entry)) {
            return new Result(true, null, false);
        }
        if (hooks.skipDelayConsumer().consume(entry, hooks.movementTickMs())) {
            return new Result(true, null, false);
        }

        Character agent = AgentBotRuntimeIdentityRuntime.bot(entry);
        if (agent.getMap() == null) {
            hooks.removedAgentCleanup().remove(agentCharId);
            return new Result(true, agent, false);
        }

        hooks.heartbeatTick().tick(entry, agent, nowMs, hooks.heartbeatIntervalMs());
        hooks.pendingOfferExpiry().expire(entry);
        boolean runAiTick = hooks.aiTickPreparation().prepare(
                entry, hooks.movementTickMs(), hooks.aiTickMs(), nowMs);
        return new Result(false, agent, runAiTick);
    }
}
