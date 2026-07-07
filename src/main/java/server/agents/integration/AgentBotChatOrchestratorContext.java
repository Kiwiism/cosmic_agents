package server.agents.integration;

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
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned adapter from the live Agent runtime entry to the generic
 * dialogue orchestrator context.
 */
public final class AgentBotChatOrchestratorContext implements AgentChatOrchestrator.Context {
    private final AgentRuntimeEntry entry;

    public AgentBotChatOrchestratorContext(AgentRuntimeEntry entry) {
        this.entry = entry;
    }

    @Override
    public void markActive() {
        AgentBotChatStatusRuntime.markOwnerActive(entry);
    }

    @Override
    public boolean hasPendingAction() {
        return AgentBotPendingActionStateRuntime.hasPendingAction(entry);
    }

    @Override
    public AgentPendingChatActionFlow.PendingActionState pendingActionState() {
        return AgentBotPendingActionRuntime.pendingActionState(entry);
    }

    @Override
    public AgentPendingChatActionFlow.PendingActionCallbacks pendingActionCallbacks() {
        return AgentBotPendingActionRuntime.pendingActionCallbacks(entry);
    }

    @Override
    public AgentChatSessionRequestFlow.SessionRequestCallbacks sessionRequestCallbacks() {
        return AgentBotSessionRuntime.sessionRequestCallbacks(entry);
    }

    @Override
    public AgentChatSupplyRequestFlow.SupplyRequestCallbacks supplyRequestCallbacks() {
        return AgentBotSupplyRuntime.supplyRequestCallbacks(entry);
    }

    @Override
    public AgentChatSocialFlow.SocialCallbacks socialCallbacks() {
        return AgentBotSocialRuntime.socialCallbacks(entry);
    }

    @Override
    public AgentChatToggleFlow.ToggleCallbacks toggleCallbacks() {
        return AgentBotControlRuntime.toggleCallbacks(entry);
    }

    @Override
    public AgentChatBuffQueryFlow.BuffQueryCallbacks buffQueryCallbacks() {
        return AgentBotControlRuntime.buffQueryCallbacks(entry);
    }

    @Override
    public AgentChatRespecFlow.RespecCallbacks respecCallbacks() {
        return AgentBotControlRuntime.respecCallbacks(entry);
    }

    @Override
    public AgentChatEquipmentFlow.EquipmentCallbacks equipmentCallbacks() {
        return AgentBotEquipmentRuntime.equipmentCallbacks(entry);
    }

    @Override
    public AgentChatMovementFlow.MovementCallbacks movementCallbacks() {
        return AgentBotMovementRuntime.movementCallbacks(entry);
    }

    @Override
    public boolean isWaitingForSpVariant() {
        return AgentBotBuildStateRuntime.spVariantPromptSent(entry)
                && !AgentBotBuildStateRuntime.hasSpVariant(entry);
    }

    @Override
    public AgentChatBuildFlow.SpVariantCallbacks spVariantCallbacks() {
        return AgentBotBuildRuntime.spVariantCallbacks(entry);
    }

    @Override
    public boolean isWaitingForApBuild() {
        return AgentBotBuildStateRuntime.apPromptSent(entry);
    }

    @Override
    public AgentChatBuildFlow.ApBuildCallbacks apBuildCallbacks() {
        return AgentBotBuildRuntime.apBuildCallbacks(entry);
    }

    @Override
    public AgentChatUtilityFlow.UtilityCallbacks utilityCallbacks() {
        return AgentBotUtilityRuntime.utilityCallbacks(entry);
    }

    @Override
    public void handleTransferCommand(AgentChatTransferFlow.TransferCommand transferCommand, String message) {
        AgentBotTransferRuntime.handleTransferCommand(entry, transferCommand, message);
    }

    @Override
    public AgentChatTransferFlow.ItemQueryCallbacks itemQueryCallbacks() {
        return AgentBotTransferRuntime.itemQueryCallbacks(entry);
    }

    @Override
    public AgentChatReportFlow.ReportCallbacks reportCallbacks() {
        return AgentBotChatReportRuntime.reportCallbacks(entry);
    }

    @Override
    public Job currentJob() {
        return AgentBotRuntimeIdentityRuntime.bot(entry).getJob();
    }

    @Override
    public int level() {
        return AgentBotRuntimeIdentityRuntime.bot(entry).getLevel();
    }

    @Override
    public AgentChatJobAdvancementFlow.JobAdvancementCallbacks jobAdvancementCallbacks() {
        return AgentBotBuildRuntime.jobAdvancementCallbacks(entry);
    }
}
