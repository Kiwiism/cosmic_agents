package server.agents.runtime.activity;

import client.Character;
import server.agents.capabilities.runtime.AgentCapabilityRuntime;
import server.agents.capabilities.townlife.AgentTownLifeRuntime;
import server.agents.plans.AgentUniversalPlanRuntime;
import server.agents.plans.amherst.AgentAmherstPlanRuntime;
import server.agents.plans.mapleisland.AgentMapleIslandLithHandoffRuntime;
import server.agents.progression.AgentVictoriaPlanSessionRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

/**
 * Default adapters for existing foreground modes. This is the only bootstrap
 * location that knows which concrete modes participate in arbitration.
 */
public final class AgentForegroundActivityDefaults {
    private static final AgentForegroundActivityArbiter ARBITER =
            new AgentForegroundActivityArbiter(new AgentForegroundActivityRegistry(List.of(
                    handoff(),
                    townLife(),
                    universalPlan(),
                    legacyVictoria(),
                    legacyAmherst(),
                    capability())));

    private AgentForegroundActivityDefaults() {
    }

    public static AgentForegroundActivityArbiter arbiter() {
        return ARBITER;
    }

    private static AgentForegroundActivity handoff() {
        return activity("maple-island-lith-handoff", 600,
                (entry, agent) -> AgentMapleIslandLithHandoffRuntime.active(entry),
                (entry, agent, nowMs) -> AgentMapleIslandLithHandoffRuntime.tick(
                        entry, agent, nowMs)
                        ? AgentForegroundActivityTick.CONSUMED
                        : AgentForegroundActivityTick.PASS);
    }

    private static AgentForegroundActivity townLife() {
        return blockingBooleanActivity("town-life", 500,
                (entry, agent) -> AgentTownLifeRuntime.active(entry),
                AgentTownLifeRuntime::tick);
    }

    private static AgentForegroundActivity universalPlan() {
        return blockingBooleanActivity("universal-plan", 400,
                (entry, agent) -> AgentUniversalPlanRuntime.active(entry),
                AgentUniversalPlanRuntime::tick);
    }

    private static AgentForegroundActivity legacyVictoria() {
        return blockingBooleanActivity("legacy-victoria-plan", 300,
                (entry, agent) -> AgentVictoriaPlanSessionRuntime.active(entry),
                AgentVictoriaPlanSessionRuntime::tick);
    }

    private static AgentForegroundActivity legacyAmherst() {
        return blockingBooleanActivity("legacy-amherst-plan", 200,
                (entry, agent) -> AgentAmherstPlanRuntime.active(entry),
                AgentAmherstPlanRuntime::tickGate);
    }

    private static AgentForegroundActivity capability() {
        return blockingBooleanActivity("capability", 100,
                (entry, agent) -> entry.capabilityRuntimeState().hasActiveCapability(),
                AgentCapabilityRuntime::tick);
    }

    private static AgentForegroundActivity blockingBooleanActivity(
            String id,
            int priority,
            ActivityPredicate predicate,
            ActivityTick tick) {
        return activity(id, priority, predicate,
                (entry, agent, nowMs) -> tick.tick(entry, agent, nowMs)
                        ? AgentForegroundActivityTick.CONSUMED
                        : AgentForegroundActivityTick.IDLE);
    }

    private static AgentForegroundActivity activity(
            String id,
            int priority,
            ActivityPredicate predicate,
            ActivityOutcomeTick tick) {
        return new AgentForegroundActivity() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public int priority() {
                return priority;
            }

            @Override
            public boolean active(AgentRuntimeEntry entry, Character agent) {
                return predicate.active(entry, agent);
            }

            @Override
            public AgentForegroundActivityTick tick(
                    AgentRuntimeEntry entry, Character agent, long nowMs) {
                return tick.tick(entry, agent, nowMs);
            }
        };
    }

    @FunctionalInterface
    private interface ActivityPredicate {
        boolean active(AgentRuntimeEntry entry, Character agent);
    }

    @FunctionalInterface
    private interface ActivityTick {
        boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs);
    }

    @FunctionalInterface
    private interface ActivityOutcomeTick {
        AgentForegroundActivityTick tick(AgentRuntimeEntry entry, Character agent, long nowMs);
    }
}
