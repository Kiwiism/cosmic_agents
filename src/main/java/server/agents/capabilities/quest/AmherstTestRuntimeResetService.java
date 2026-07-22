package server.agents.capabilities.quest;

import client.Character;
import server.agents.capabilities.behavior.AgentPioRelaxerInterludeRuntime;
import server.agents.capabilities.combat.AgentCombatObjectiveTargetStateRuntime;
import server.agents.capabilities.dialogue.AgentPendingActionStateRuntime;
import server.agents.capabilities.looting.AgentGrindLootStateRuntime;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.plans.amherst.MapleIslandRelaxerSpotReservationRuntime;
import server.agents.capabilities.runtime.AgentCapabilityRuntime;
import server.agents.capabilities.trade.AgentTradeStateService;
import server.agents.plans.AgentPlanPauseRuntime;
import server.agents.plans.AgentScriptTaskStateRuntime;
import server.agents.runtime.AgentModeService;
import server.agents.runtime.AgentRuntimeEntry;

/** Clears transient state only for an explicitly guarded Amherst test fixture. */
public final class AmherstTestRuntimeResetService {
    private AmherstTestRuntimeResetService() {
    }

    public static void reset(AgentRuntimeEntry entry, Character agent, long nowMs) {
        AgentPioRelaxerInterludeRuntime.cancel(entry, agent, nowMs);
        AgentPlanPauseRuntime.reset(entry);
        AgentCapabilityRuntime.cancelNow(entry, agent, nowMs);
        MapleIslandRelaxerSpotReservationRuntime.release(agent.getId());
        AgentModeService.startStop(entry);
        AgentMovementStateResetService.resetEntryState(entry);
        AgentCombatObjectiveTargetStateRuntime.clear(entry);
        AgentGrindLootStateRuntime.clearGrindLootTarget(entry);
        AgentGrindLootStateRuntime.clearRetrySuppression(entry);

        entry.combatCooldownState().clearAttackCooldown();
        entry.combatCooldownState().clearMoveWindow();
        entry.combatCooldownState().setMobHitCooldownMs(0);
        entry.combatCooldownState().setAlertedUntilMs(0L);
        entry.combatCooldownState().setAlertResetScheduled(false);
        entry.inventoryCooldownState().setLootInhibitMs(0);
        entry.inventoryCooldownState().setInventoryFullWarnCooldownMs(0);
        entry.portalCooldownState().setUseCooldownUntilMs(0L);

        AgentPendingActionStateRuntime.clearPendingAction(entry);
        AgentPendingActionStateRuntime.clearPendingDropCategory(entry);
        AgentScriptTaskStateRuntime.clearTasksAndBumpEpoch(entry);
        AgentScriptTaskStateRuntime.resetScript(entry, null);
        entry.actionMailbox().discardPending("Amherst test fixture reset");
        entry.messageQueueState().clear();
        entry.pendingLootOfferState().clear();
        entry.tradeRetryState().takeRetry();
        entry.tradeRetryState().setDelayMs(0);
        entry.manualTradeState().clear();
        AgentTradeStateService.clearSequence(entry);
    }
}
