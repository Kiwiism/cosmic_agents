package server.agents.runtime;

import client.Job;
import server.agents.capabilities.dialogue.AgentChatBuildFlow;
import server.agents.capabilities.dialogue.AgentChatBuffQueryFlow;
import server.agents.capabilities.dialogue.AgentChatEquipmentFlow;
import server.agents.capabilities.dialogue.AgentChatJobAdvancementFlow;
import server.agents.capabilities.dialogue.AgentChatMovementFlow;
import server.agents.capabilities.dialogue.AgentChatOrchestrator;
import server.agents.capabilities.dialogue.AgentChatReportFlow;
import server.agents.capabilities.dialogue.AgentChatRespecFlow;
import server.agents.capabilities.dialogue.AgentChatSessionRequestFlow;
import server.agents.capabilities.dialogue.AgentChatSocialFlow;
import server.agents.capabilities.dialogue.AgentChatSupplyRequestFlow;
import server.agents.capabilities.dialogue.AgentChatToggleFlow;
import server.agents.capabilities.dialogue.AgentChatTransferFlow;
import server.agents.capabilities.dialogue.AgentChatUtilityFlow;
import server.agents.capabilities.dialogue.AgentPendingChatActionFlow;
import server.agents.capabilities.build.AgentBuildRuntime;
import server.agents.capabilities.build.AgentBuildStateRuntime;
import server.agents.integration.AgentChatReportRuntime;
import server.agents.capabilities.dialogue.AgentChatStatusOrchestrator;
import server.agents.capabilities.dialogue.AgentControlRuntime;
import server.agents.capabilities.equipment.AgentEquipmentRuntime;
import server.agents.capabilities.movement.AgentMovementRuntime;
import server.agents.capabilities.dialogue.AgentPendingActionRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.AgentSessionRuntime;
import server.agents.integration.AgentSocialRuntime;
import server.agents.capabilities.supplies.AgentSupplyRuntime;
import server.agents.capabilities.trade.AgentTransferRuntime;
import server.agents.capabilities.dialogue.AgentUtilityRuntime;

/**
 * Agent-owned adapter from the live Agent runtime entry to the generic
 * dialogue orchestrator context.
 */
public final class AgentChatOrchestratorContext implements AgentChatOrchestrator.Context {
    private final AgentRuntimeEntry entry;

    public AgentChatOrchestratorContext(AgentRuntimeEntry entry) {
        this.entry = entry;
    }

    @Override
    public void markActive() {
        AgentChatStatusOrchestrator.markOwnerActive(entry);
    }

    @Override
    public boolean hasPendingAction() {
        return AgentPendingActionStateRuntime.hasPendingAction(entry);
    }

    @Override
    public AgentPendingChatActionFlow.PendingActionState pendingActionState() {
        return AgentPendingActionRuntime.pendingActionState(entry);
    }

    @Override
    public AgentPendingChatActionFlow.PendingActionCallbacks pendingActionCallbacks() {
        return AgentPendingActionRuntime.pendingActionCallbacks(entry);
    }

    @Override
    public AgentChatSessionRequestFlow.SessionRequestCallbacks sessionRequestCallbacks() {
        return AgentSessionRuntime.sessionRequestCallbacks(entry);
    }

    @Override
    public AgentChatSupplyRequestFlow.SupplyRequestCallbacks supplyRequestCallbacks() {
        return AgentSupplyRuntime.supplyRequestCallbacks(entry);
    }

    @Override
    public AgentChatSocialFlow.SocialCallbacks socialCallbacks() {
        return AgentSocialRuntime.socialCallbacks(entry);
    }

    @Override
    public AgentChatToggleFlow.ToggleCallbacks toggleCallbacks() {
        return AgentControlRuntime.toggleCallbacks(entry);
    }

    @Override
    public AgentChatBuffQueryFlow.BuffQueryCallbacks buffQueryCallbacks() {
        return AgentControlRuntime.buffQueryCallbacks(entry);
    }

    @Override
    public AgentChatRespecFlow.RespecCallbacks respecCallbacks() {
        return AgentControlRuntime.respecCallbacks(entry);
    }

    @Override
    public AgentChatEquipmentFlow.EquipmentCallbacks equipmentCallbacks() {
        return AgentEquipmentRuntime.equipmentCallbacks(entry);
    }

    @Override
    public AgentChatMovementFlow.MovementCallbacks movementCallbacks() {
        return AgentMovementRuntime.movementCallbacks(entry);
    }

    @Override
    public boolean isWaitingForSpVariant() {
        return AgentBuildStateRuntime.spVariantPromptSent(entry)
                && !AgentBuildStateRuntime.hasSpVariant(entry);
    }

    @Override
    public AgentChatBuildFlow.SpVariantCallbacks spVariantCallbacks() {
        return AgentBuildRuntime.spVariantCallbacks(entry);
    }

    @Override
    public boolean isWaitingForApBuild() {
        return AgentBuildStateRuntime.apPromptSent(entry);
    }

    @Override
    public AgentChatBuildFlow.ApBuildCallbacks apBuildCallbacks() {
        return AgentBuildRuntime.apBuildCallbacks(entry);
    }

    @Override
    public AgentChatUtilityFlow.UtilityCallbacks utilityCallbacks() {
        return AgentUtilityRuntime.utilityCallbacks(entry);
    }

    @Override
    public void handleTransferCommand(AgentChatTransferFlow.TransferCommand transferCommand, String message) {
        AgentTransferRuntime.handleTransferCommand(entry, transferCommand, message);
    }

    @Override
    public AgentChatTransferFlow.ItemQueryCallbacks itemQueryCallbacks() {
        return AgentTransferRuntime.itemQueryCallbacks(entry);
    }

    @Override
    public AgentChatReportFlow.ReportCallbacks reportCallbacks() {
        return AgentChatReportRuntime.reportCallbacks(entry);
    }

    @Override
    public Job currentJob() {
        return AgentRuntimeIdentityRuntime.bot(entry).getJob();
    }

    @Override
    public int level() {
        return AgentRuntimeIdentityRuntime.bot(entry).getLevel();
    }

    @Override
    public AgentChatJobAdvancementFlow.JobAdvancementCallbacks jobAdvancementCallbacks() {
        return AgentBuildRuntime.jobAdvancementCallbacks(entry);
    }
}
