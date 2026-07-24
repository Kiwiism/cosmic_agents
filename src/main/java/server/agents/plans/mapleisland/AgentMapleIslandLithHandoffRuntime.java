package server.agents.plans.mapleisland;

import client.Character;
import client.QuestStatus;
import server.agents.auth.AgentAuthorityService;
import server.agents.capabilities.townlife.AgentTownLifeRuntime;
import server.agents.capabilities.townlife.LithHarborTownLifeCatalog;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.plans.AgentPlanExecutionStatus;
import server.agents.plans.AgentPlanSessionState;
import server.agents.plans.AgentPlanStartRequest;
import server.agents.plans.AgentUniversalPlanRuntime;
import server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

/**
 * Defers the Southperry transfer until an Agent's current Maple Island plan has
 * finished, then hands the arrived Agent to the generic TownLife capability.
 */
public final class AgentMapleIslandLithHandoffRuntime {
    public static final String TRANSFER_PLAN_ID = "southperry-to-lith-harbor";

    private AgentMapleIslandLithHandoffRuntime() {
    }

    public static AssignmentResult requestAll(Character issuer, long nowMs) {
        if (!AgentAuthorityService.mayOperate(issuer)) {
            return AssignmentResult.unauthorized();
        }
        int assigned = 0;
        int startedNow = 0;
        int alreadyQueued = 0;
        int alreadyInTownLife = 0;
        int outsideScope = 0;
        for (AgentRuntimeEntry entry : AgentRuntimeRegistry.activeEntriesSnapshot()) {
            Character agent = AgentRuntimeIdentityRuntime.bot(entry);
            if (!sameChannel(issuer, agent)) {
                continue;
            }
            if (!mapleIslandParticipant(entry, agent)) {
                outsideScope++;
                continue;
            }
            if (AgentTownLifeRuntime.active(entry)) {
                alreadyInTownLife++;
                continue;
            }
            AgentMapleIslandLithHandoffState state =
                    entry.capabilityStates().require(AgentMapleIslandLithHandoffState.STATE_KEY);
            AgentPlanSessionState plan =
                    entry.capabilityStates().require(AgentPlanSessionState.STATE_KEY);
            reconcileTerminalTransfer(entry, state, plan, nowMs);
            if (state.requested()
                    || TRANSFER_PLAN_ID.equals(plan.deferredSuccessorPlanId())) {
                alreadyQueued++;
                continue;
            }
            state.request(nowMs);
            AgentUniversalPlanRuntime.deferSuccessor(entry, TRANSFER_PLAN_ID);
            assigned++;
            if (tick(entry, agent, nowMs)) {
                startedNow++;
            }
        }
        return new AssignmentResult(
                true, assigned, startedNow, alreadyQueued, alreadyInTownLife, outsideScope);
    }

    /**
     * Advances only the handoff boundary. Returning true means a transfer or
     * TownLife session was started and the current tick is consumed.
     */
    public static boolean active(AgentRuntimeEntry entry) {
        if (entry == null) {
            return false;
        }
        AgentMapleIslandLithHandoffState state = entry.capabilityStates()
                .find(AgentMapleIslandLithHandoffState.STATE_KEY).orElse(null);
        if (state != null && state.requested()) {
            return true;
        }
        AgentPlanSessionState plan = entry.capabilityStates()
                .find(AgentPlanSessionState.STATE_KEY).orElse(null);
        return plan != null && TRANSFER_PLAN_ID.equals(plan.deferredSuccessorPlanId());
    }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null || agent == null) {
            return false;
        }
        AgentPlanSessionState plan =
                entry.capabilityStates().require(AgentPlanSessionState.STATE_KEY);
        AgentMapleIslandLithHandoffState state = entry.capabilityStates()
                .find(AgentMapleIslandLithHandoffState.STATE_KEY).orElse(null);
        if ((state == null || !state.requested())
                && TRANSFER_PLAN_ID.equals(plan.deferredSuccessorPlanId())) {
            state = entry.capabilityStates().require(AgentMapleIslandLithHandoffState.STATE_KEY);
            state.request(nowMs);
        }
        if (state == null || !state.requested()) {
            return false;
        }
        if (AgentTownLifeRuntime.active(entry)) {
            state.complete();
            AgentUniversalPlanRuntime.clearDeferredSuccessor(
                    entry, TRANSFER_PLAN_ID, nowMs);
            return false;
        }

        if (TRANSFER_PLAN_ID.equals(plan.planId())) {
            if (plan.status() == AgentPlanExecutionStatus.SUCCEEDED) {
                AgentTownLifeRuntime.start(entry, LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID,
                        nowMs, agent.getId());
                state.complete();
                AgentUniversalPlanRuntime.clearDeferredSuccessor(
                        entry, TRANSFER_PLAN_ID, nowMs);
                return true;
            }
            if (plan.status() == AgentPlanExecutionStatus.BLOCKED
                    || plan.status() == AgentPlanExecutionStatus.CANCELLED
                    || plan.status() == AgentPlanExecutionStatus.FAILED) {
                if (state.stage()
                        == AgentMapleIslandLithHandoffState.Stage.WAITING_FOR_SOUTHPERRY
                        && readyAtSouthperry(agent)) {
                    boolean restarted = AgentUniversalPlanRuntime.start(
                            entry, agent, TRANSFER_PLAN_ID, AgentPlanStartRequest.EMPTY, nowMs);
                    if (restarted) {
                        state.transferring();
                    }
                    return restarted;
                }
                state.fail(plan.reason());
                AgentUniversalPlanRuntime.clearDeferredSuccessor(
                        entry, TRANSFER_PLAN_ID, nowMs);
                return false;
            }
            if (plan.active()) {
                state.transferring();
                return false;
            }
        }
        if (plan.active() || !readyAtSouthperry(agent)) {
            return false;
        }

        boolean started = plan.availableSuccessorPlanIds().contains(TRANSFER_PLAN_ID)
                ? AgentUniversalPlanRuntime.startAvailableSuccessor(
                        entry, agent, TRANSFER_PLAN_ID, AgentPlanStartRequest.EMPTY, nowMs)
                : AgentUniversalPlanRuntime.start(
                        entry, agent, TRANSFER_PLAN_ID, AgentPlanStartRequest.EMPTY, nowMs);
        if (started) {
            state.transferring();
        }
        return started;
    }

    private static void reconcileTerminalTransfer(AgentRuntimeEntry entry,
                                                  AgentMapleIslandLithHandoffState state,
                                                  AgentPlanSessionState plan,
                                                  long nowMs) {
        if (!state.requested() || !TRANSFER_PLAN_ID.equals(plan.planId())) {
            return;
        }
        if (plan.status() == AgentPlanExecutionStatus.BLOCKED
                || plan.status() == AgentPlanExecutionStatus.CANCELLED
                || plan.status() == AgentPlanExecutionStatus.FAILED) {
            state.fail(plan.reason());
            AgentUniversalPlanRuntime.clearDeferredSuccessor(
                    entry, TRANSFER_PLAN_ID, nowMs);
        }
    }

    static boolean mapleIslandParticipant(AgentRuntimeEntry entry, Character agent) {
        if (entry == null || agent == null) {
            return false;
        }
        int mapId = agent.getMapId();
        if (mapId >= 0 && mapId <= MapleIslandSouthperryQuestCatalog.FINAL_MAP_ID) {
            return true;
        }
        AgentPlanSessionState plan = entry.capabilityStates()
                .find(AgentPlanSessionState.STATE_KEY).orElse(null);
        return plan != null && plan.planId().startsWith("maple-island-");
    }

    static boolean readyAtSouthperry(Character agent) {
        return agent != null
                && agent.getMapId() == MapleIslandSouthperryQuestCatalog.FINAL_MAP_ID
                && agent.getQuestStatus(MapleIslandSouthperryQuestCatalog.START_ONLY_BIGGS_STORY_QUEST_ID)
                == QuestStatus.Status.STARTED.getId();
    }

    private static boolean sameChannel(Character issuer, Character agent) {
        return issuer != null
                && agent != null
                && issuer.getWorld() == agent.getWorld()
                && AgentClientGatewayRuntime.clients().hasClient(issuer)
                && AgentClientGatewayRuntime.clients().hasClient(agent)
                && AgentClientGatewayRuntime.clients().channel(issuer)
                == AgentClientGatewayRuntime.clients().channel(agent);
    }

    public record AssignmentResult(
            boolean authorized,
            int assigned,
            int startedNow,
            int alreadyQueued,
            int alreadyInTownLife,
            int outsideScope) {
        private static AssignmentResult unauthorized() {
            return new AssignmentResult(false, 0, 0, 0, 0, 0);
        }

        public int waitingForCurrentPlan() {
            return Math.max(0, assigned - startedNow);
        }
    }
}
