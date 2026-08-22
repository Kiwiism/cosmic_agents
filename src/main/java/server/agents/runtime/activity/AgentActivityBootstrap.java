package server.agents.runtime.activity;

import client.Character;
import server.agents.capabilities.runtime.AgentCapabilityRuntime;
import server.agents.capabilities.partyquest.kpq.AgentKpqRuntime;
import server.agents.capabilities.townlife.AgentTownLifeRuntime;
import server.agents.plans.AgentUniversalPlanRuntime;
import server.agents.plans.mapleisland.AgentMapleIslandLithHandoffRuntime;
import server.agents.runtime.AgentCommerceControlRuntime;
import server.agents.runtime.commerce.AgentCommerceSessionRegistryRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.session.AgentFileActivityHandoffStore;
import server.agents.runtime.activity.session.AgentRestoredHandoffOwnerResolver;
import server.agents.runtime.field.AgentFieldActivityRuntime;
import server.agents.runtime.field.AgentFieldVisitLeaseRuntime;
import server.agents.runtime.townlife.AgentTownLifeTestScenarioRuntime;
import server.agents.runtime.townlife.AgentTownLifeVisitLeaseRuntime;
import server.agents.socials.AgentSocialsSystem;

import java.util.List;

/** Single bootstrap location for primary, support, compatibility, and capability controllers. */
public final class AgentActivityBootstrap {
    public static final String COMMERCE_CONTROLLER_ID = "commerce";
    public static final String TOWN_LIFE_CONTROLLER_ID = "town-life";
    public static final String HUNTING_CONTROLLER_ID = "hunting";
    public static final String QUESTING_CONTROLLER_ID = "questing";
    public static final String PARTY_QUEST_CONTROLLER_ID = "party-quest";

    private static final AgentActivityHost HOST = new AgentActivityHost(registry());
    private static final AgentActivityAdmissionCoordinator ADMISSION =
            new AgentActivityAdmissionCoordinator(registry());
    private static final AgentActivityOwnershipReconciler OWNERSHIP =
            new AgentActivityOwnershipReconciler(registry());
    private static final AgentRestoredHandoffOwnerResolver RESTORED_HANDOFF_OWNER =
            new AgentRestoredHandoffOwnerResolver(AgentFileActivityHandoffStore.runtimeDefault());

    private AgentActivityBootstrap() {
    }

    public static AgentActivityHost host() {
        return HOST;
    }

    public static AgentActivityAdmissionCoordinator admission() {
        return ADMISSION;
    }

    public static AgentActivityOwnershipReconciliation reconcileRestoredOwnership(
            AgentRuntimeEntry entry, Character agent, long nowMs) {
        AgentActivityKind expected = RESTORED_HANDOFF_OWNER.expectedOwner(agent.getId())
                .orElse(null);
        AgentActivityOwnershipReconciliation result =
                OWNERSHIP.reconcile(entry, agent, expected, nowMs);
        entry.capabilityStates().require(AgentActivityOwnershipState.STATE_KEY)
                .record(result, nowMs);
        return result;
    }

    private static AgentActivityControllerRegistry registry() {
        return Holder.REGISTRY;
    }

    private static final class Holder {
        private static final AgentActivityControllerRegistry REGISTRY =
                new AgentActivityControllerRegistry(List.of(
                        commerce(),
                        partyQuest(),
                        mapleIslandLithTransfer(),
                        townLifeTestScenario(),
                        interactionLease(),
                        townLifeVisitLease(),
                        townLife(),
                        fieldVisitLease(),
                        hunting(),
                        questing(),
                        capability()));
    }

    private static AgentActivityController partyQuest() {
        return new AgentActivityController() {
            @Override public String id() { return PARTY_QUEST_CONTROLLER_ID; }
            @Override public int precedence() { return 900; }
            @Override public AgentActivityRole role() { return AgentActivityRole.PRIMARY; }
            @Override public AgentActivityKind activityKind() { return AgentActivityKind.PARTY_QUEST; }
            @Override public boolean active(AgentRuntimeEntry entry, Character agent) {
                return AgentKpqRuntime.active(agent.getId());
            }
            @Override public AgentActivityTick tick(
                    AgentRuntimeEntry entry, Character agent, long nowMs) {
                AgentKpqRuntime.tick(entry, agent, nowMs);
                // KPQ installs ordinary MOVE_TO/GRIND modes. IDLE retains activity
                // ownership while allowing the existing movement/combat phase to advance.
                return AgentActivityTick.IDLE;
            }
            @Override public boolean requestStop(
                    AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
                return AgentKpqRuntime.requestStop(agent.getId(), reason, nowMs);
            }
            @Override public void forceStop(
                    AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
                AgentKpqRuntime.forceStop(agent.getId(), reason, nowMs);
            }
        };
    }

    private static AgentActivityController commerce() {
        return new AgentActivityController() {
            @Override public String id() { return COMMERCE_CONTROLLER_ID; }
            @Override public int precedence() { return 1_000; }
            @Override public AgentActivityRole role() { return AgentActivityRole.PRIMARY; }
            @Override public AgentActivityKind activityKind() { return AgentActivityKind.COMMERCE; }
            @Override public boolean active(AgentRuntimeEntry entry, Character agent) {
                return AgentCommerceSessionRegistryRuntime.active(agent.getId())
                        || AgentCommerceControlRuntime.claimed(agent.getId());
            }
            @Override public AgentActivityTick tick(
                    AgentRuntimeEntry entry, Character agent, long nowMs) {
                    if (AgentCommerceSessionRegistryRuntime.active(agent.getId())) {
                        if (entry.capabilityRuntimeState().hasActiveCapability()) {
                            return AgentCommerceControlRuntime.withAttribution(agent.getId(),
                                    () -> AgentCapabilityRuntime.tick(entry, agent, nowMs))
                                    ? AgentActivityTick.CONSUMED : AgentActivityTick.IDLE;
                        }
                        return AgentCommerceSessionRegistryRuntime.tick(agent.getId(), nowMs)
                                ? AgentActivityTick.CONSUMED : AgentActivityTick.IDLE;
                    }
                    if (entry.capabilityRuntimeState().hasActiveCapability()) {
                        return AgentCommerceControlRuntime.withAttribution(agent.getId(),
                                () -> AgentCapabilityRuntime.tick(entry, agent, nowMs))
                                ? AgentActivityTick.CONSUMED : AgentActivityTick.IDLE;
                    }
                    return AgentActivityTick.CONSUMED;
            }
            @Override public boolean requestStop(
                    AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
                if (AgentCommerceSessionRegistryRuntime.active(agent.getId())) {
                    return AgentCommerceSessionRegistryRuntime.requestStop(
                            agent.getId(), reason, nowMs, nowMs + 30_000L)
                            .status() == server.agents.runtime.activity.session.AgentActivityExitResult.Status.RELEASED;
                }
                return false;
            }
            @Override public void forceStop(
                    AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
                if (AgentCommerceSessionRegistryRuntime.active(agent.getId())) {
                    throw new IllegalStateException(
                            "Per-Agent Commerce must drain protected shop and trade state");
                }
                throw new IllegalStateException(
                        "Commerce control must be released by the Commerce run lifecycle");
            }
        };
    }

    private static AgentActivityController mapleIslandLithTransfer() {
        return controller("maple-island-lith-transfer", 600,
                AgentActivityRole.COMPATIBILITY, null,
                (entry, agent) -> AgentMapleIslandLithHandoffRuntime.active(entry),
                (entry, agent, nowMs) -> AgentMapleIslandLithHandoffRuntime.tick(
                        entry, agent, nowMs) ? AgentActivityTick.CONSUMED : AgentActivityTick.PASS,
                false, ActivityStopper.NONE);
    }

    private static AgentActivityController townLifeTestScenario() {
        return controller("town-life-test-scenario", 590,
                AgentActivityRole.COMPATIBILITY, null,
                (entry, agent) -> AgentTownLifeTestScenarioRuntime.active(entry),
                AgentTownLifeTestScenarioRuntime::tick, false,
                (entry, agent, reason, nowMs) ->
                        AgentTownLifeTestScenarioRuntime.requestStop(entry, agent, reason, nowMs));
    }

    private static AgentActivityController interactionLease() {
        return controller("interaction-lease", 575, AgentActivityRole.SUPPORT, null,
                (entry, agent) -> AgentSocialsSystem.interactionActive(entry),
                AgentSocialsSystem::tickInteraction, false,
                AgentSocialsSystem::cancelInteraction);
    }

    private static AgentActivityController townLifeVisitLease() {
        return controller("town-life-visit-lease", 550, AgentActivityRole.SUPPORT, null,
                (entry, agent) -> AgentTownLifeVisitLeaseRuntime.active(entry),
                (entry, agent, nowMs) -> {
                    AgentTownLifeVisitLeaseRuntime.tick(entry, agent, nowMs);
                    return AgentActivityTick.PASS;
                }, false, ActivityStopper.NONE);
    }

    private static AgentActivityController townLife() {
        return new AgentActivityController() {
            @Override public String id() { return TOWN_LIFE_CONTROLLER_ID; }
            @Override public int precedence() { return 500; }
            @Override public AgentActivityRole role() { return AgentActivityRole.PRIMARY; }
            @Override public AgentActivityKind activityKind() { return AgentActivityKind.TOWN_LIFE; }
            @Override public boolean active(AgentRuntimeEntry entry, Character agent) {
                return AgentTownLifeRuntime.active(entry);
            }
            @Override public AgentActivityTick tick(
                    AgentRuntimeEntry entry, Character agent, long nowMs) {
                return AgentTownLifeRuntime.tick(entry, agent, nowMs)
                        ? AgentActivityTick.CONSUMED : AgentActivityTick.IDLE;
            }
            @Override public boolean requestStop(
                    AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
                AgentTownLifeRuntime.requestGracefulStop(entry, agent, reason, nowMs);
                return !AgentTownLifeRuntime.active(entry);
            }
            @Override public void forceStop(
                    AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
                AgentTownLifeRuntime.forceStop(entry, agent, reason);
            }
        };
    }

    private static AgentActivityController fieldVisitLease() {
        return controller("hunting-visit-lease", 475, AgentActivityRole.SUPPORT, null,
                (entry, agent) -> AgentFieldVisitLeaseRuntime.active(entry),
                (entry, agent, nowMs) -> {
                    AgentFieldVisitLeaseRuntime.tick(entry, agent, nowMs);
                    return AgentActivityTick.PASS;
                }, false, ActivityStopper.NONE);
    }

    private static AgentActivityController hunting() {
        return new AgentActivityController() {
            @Override public String id() { return HUNTING_CONTROLLER_ID; }
            @Override public int precedence() { return 450; }
            @Override public AgentActivityRole role() { return AgentActivityRole.PRIMARY; }
            @Override public AgentActivityKind activityKind() { return AgentActivityKind.HUNTING; }
            @Override public boolean active(AgentRuntimeEntry entry, Character agent) {
                return AgentFieldActivityRuntime.active(entry);
            }
            @Override public AgentActivityTick tick(
                    AgentRuntimeEntry entry, Character agent, long nowMs) {
                return AgentFieldActivityRuntime.tick(entry, agent, nowMs);
            }
            @Override public boolean requestStop(
                    AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
                return AgentFieldActivityRuntime.requestGracefulStop(entry, agent, reason, nowMs);
            }
            @Override public void forceStop(
                    AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
                AgentFieldActivityRuntime.forceStop(entry, agent, reason, nowMs);
            }
        };
    }

    private static AgentActivityController questing() {
        return new AgentActivityController() {
            @Override public String id() { return QUESTING_CONTROLLER_ID; }
            @Override public int precedence() { return 400; }
            @Override public AgentActivityRole role() { return AgentActivityRole.PRIMARY; }
            @Override public AgentActivityKind activityKind() { return AgentActivityKind.QUESTING; }
            @Override public boolean active(AgentRuntimeEntry entry, Character agent) {
                return AgentUniversalPlanRuntime.foregroundActive(entry);
            }
            @Override public AgentActivityTick tick(
                    AgentRuntimeEntry entry, Character agent, long nowMs) {
                return AgentUniversalPlanRuntime.foregroundTick(entry, agent, nowMs)
                        ? AgentActivityTick.CONSUMED : AgentActivityTick.IDLE;
            }
            @Override public boolean requestStop(
                    AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
                return AgentUniversalPlanRuntime.requestGracefulStop(entry, agent, reason, nowMs);
            }
            @Override public void forceStop(
                    AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
                AgentUniversalPlanRuntime.foregroundCancel(entry, agent, reason, nowMs);
            }
        };
    }

    private static AgentActivityController capability() {
        return controller("capability", 100, AgentActivityRole.CAPABILITY, null,
                (entry, agent) -> entry.capabilityRuntimeState().hasActiveCapability(),
                (entry, agent, nowMs) -> AgentCapabilityRuntime.tick(entry, agent, nowMs)
                        ? AgentActivityTick.CONSUMED : AgentActivityTick.IDLE,
                true,
                (entry, agent, reason, nowMs) ->
                        AgentCapabilityRuntime.cancelNow(entry, agent, nowMs));
    }

    private static AgentActivityController controller(
            String id,
            int precedence,
            AgentActivityRole role,
            AgentActivityKind kind,
            ActivityPredicate predicate,
            ActivityTick tick,
            boolean exclusive,
            ActivityStopper stopper) {
        return new AgentActivityController() {
            @Override public String id() { return id; }
            @Override public int precedence() { return precedence; }
            @Override public AgentActivityRole role() { return role; }
            @Override public AgentActivityKind activityKind() { return kind; }
            @Override public boolean active(AgentRuntimeEntry entry, Character agent) {
                return predicate.active(entry, agent);
            }
            @Override public AgentActivityTick tick(
                    AgentRuntimeEntry entry, Character agent, long nowMs) {
                return tick.tick(entry, agent, nowMs);
            }
            @Override public boolean exclusive() { return exclusive; }
            @Override public void forceStop(
                    AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
                stopper.stop(entry, agent, reason, nowMs);
            }
        };
    }

    @FunctionalInterface
    private interface ActivityPredicate {
        boolean active(AgentRuntimeEntry entry, Character agent);
    }

    @FunctionalInterface
    private interface ActivityTick {
        AgentActivityTick tick(AgentRuntimeEntry entry, Character agent, long nowMs);
    }

    @FunctionalInterface
    private interface ActivityStopper {
        ActivityStopper NONE = (entry, agent, reason, nowMs) -> { };

        void stop(AgentRuntimeEntry entry, Character agent, String reason, long nowMs);
    }
}
