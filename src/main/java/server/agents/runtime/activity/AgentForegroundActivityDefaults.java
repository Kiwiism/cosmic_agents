package server.agents.runtime.activity;

import client.Character;
import server.agents.capabilities.runtime.AgentCapabilityRuntime;
import server.agents.capabilities.townlife.AgentTownLifeRuntime;
import server.agents.plans.AgentUniversalPlanRuntime;
import server.agents.plans.mapleisland.AgentMapleIslandLithHandoffRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.townlife.AgentTownLifeVisitLeaseRuntime;
import server.agents.runtime.interaction.AgentInteractionLeaseRuntime;
import server.agents.runtime.townlife.AgentTownLifeTestScenarioRuntime;

import java.util.List;

/**
 * Default adapters for existing foreground modes. This is the only bootstrap
 * location that knows which concrete modes participate in arbitration.
 */
public final class AgentForegroundActivityDefaults {
    private static final AgentForegroundActivityArbiter ARBITER =
            new AgentForegroundActivityArbiter(registry());
    private static final AgentForegroundActivityCoordinator COORDINATOR =
            new AgentForegroundActivityCoordinator(registry());

    private static AgentForegroundActivityRegistry registry() {
        return Holder.REGISTRY;
    }

    private static final class Holder {
        private static final AgentForegroundActivityRegistry REGISTRY =
                new AgentForegroundActivityRegistry(List.of(
                    handoff(),
                    townLifeTestScenario(),
                    interactionLease(),
                    townLifeVisitLease(),
                    townLife(),
                    universalPlan(),
                    capability()));
    }

    private AgentForegroundActivityDefaults() {
    }

    public static AgentForegroundActivityArbiter arbiter() {
        return ARBITER;
    }

    public static AgentForegroundActivityCoordinator coordinator() {
        return COORDINATOR;
    }

    private static AgentForegroundActivity handoff() {
        return activity("maple-island-lith-handoff", 600,
                (entry, agent) -> AgentMapleIslandLithHandoffRuntime.active(entry),
                (entry, agent, nowMs) -> AgentMapleIslandLithHandoffRuntime.tick(
                        entry, agent, nowMs)
                        ? AgentForegroundActivityTick.CONSUMED
                        : AgentForegroundActivityTick.PASS,
                false, ActivityDeactivator.NONE);
    }

    private static AgentForegroundActivity townLife() {
        return new AgentForegroundActivity() {
            @Override
            public String id() {
                return "town-life";
            }

            @Override
            public int priority() {
                return 500;
            }

            @Override
            public boolean active(AgentRuntimeEntry entry, Character agent) {
                return AgentTownLifeRuntime.active(entry);
            }

            @Override
            public AgentForegroundActivityTick tick(
                    AgentRuntimeEntry entry, Character agent, long nowMs) {
                return AgentTownLifeRuntime.tick(entry, agent, nowMs)
                        ? AgentForegroundActivityTick.CONSUMED
                        : AgentForegroundActivityTick.IDLE;
            }

            @Override
            public boolean requestDeactivate(
                    AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
                AgentTownLifeRuntime.requestGracefulStop(entry, agent, reason, nowMs);
                return !AgentTownLifeRuntime.active(entry);
            }

            @Override
            public void deactivate(
                    AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
                AgentTownLifeRuntime.forceStop(entry, agent, reason);
            }
        };
    }

    private static AgentForegroundActivity interactionLease() {
        return activity("interaction-lease", 575,
                (entry, agent) -> AgentInteractionLeaseRuntime.active(entry),
                AgentInteractionLeaseRuntime::tick,
                false,
                (entry, agent, reason, nowMs) ->
                        AgentInteractionLeaseRuntime.cancel(entry, agent, reason, nowMs));
    }

    private static AgentForegroundActivity townLifeTestScenario() {
        return activity("town-life-test-scenario", 590,
                (entry, agent) -> AgentTownLifeTestScenarioRuntime.active(entry),
                AgentTownLifeTestScenarioRuntime::tick,
                false,
                (entry, agent, reason, nowMs) ->
                        AgentTownLifeTestScenarioRuntime.requestStop(
                                entry, agent, reason, nowMs));
    }

    private static AgentForegroundActivity townLifeVisitLease() {
        return activity("town-life-visit-lease", 550,
                (entry, agent) -> AgentTownLifeVisitLeaseRuntime.active(entry),
                (entry, agent, nowMs) -> {
                    AgentTownLifeVisitLeaseRuntime.tick(entry, agent, nowMs);
                    return AgentForegroundActivityTick.PASS;
                },
                false, ActivityDeactivator.NONE);
    }

    private static AgentForegroundActivity universalPlan() {
        return blockingBooleanActivity("universal-plan", 400,
                (entry, agent) -> AgentUniversalPlanRuntime.foregroundActive(entry),
                AgentUniversalPlanRuntime::foregroundTick,
                (entry, agent, reason, nowMs) ->
                        AgentUniversalPlanRuntime.foregroundCancel(
                                entry, agent, reason, nowMs));
    }

    private static AgentForegroundActivity capability() {
        return blockingBooleanActivity("capability", 100,
                (entry, agent) -> entry.capabilityRuntimeState().hasActiveCapability(),
                AgentCapabilityRuntime::tick,
                (entry, agent, reason, nowMs) ->
                        AgentCapabilityRuntime.cancelNow(entry, agent, nowMs));
    }

    private static AgentForegroundActivity blockingBooleanActivity(
            String id,
            int priority,
            ActivityPredicate predicate,
            ActivityTick tick,
            ActivityDeactivator deactivator) {
        return activity(id, priority, predicate,
                (entry, agent, nowMs) -> tick.tick(entry, agent, nowMs)
                        ? AgentForegroundActivityTick.CONSUMED
                        : AgentForegroundActivityTick.IDLE,
                true, deactivator);
    }

    private static AgentForegroundActivity activity(
            String id,
            int priority,
            ActivityPredicate predicate,
            ActivityOutcomeTick tick,
            boolean exclusive,
            ActivityDeactivator deactivator) {
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

            @Override
            public boolean exclusive() {
                return exclusive;
            }

            @Override
            public void deactivate(
                    AgentRuntimeEntry entry,
                    Character agent,
                    String reason,
                    long nowMs) {
                deactivator.deactivate(entry, agent, reason, nowMs);
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

    @FunctionalInterface
    private interface ActivityDeactivator {
        ActivityDeactivator NONE = (entry, agent, reason, nowMs) -> { };

        void deactivate(
                AgentRuntimeEntry entry, Character agent, String reason, long nowMs);
    }
}
