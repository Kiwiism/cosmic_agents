package server.bots;

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

/**
 * Temporary compatibility adapter while BotChatManager side effects are migrated
 * into Agent-owned runtime services.
 */
final class BotChatOrchestratorContext implements AgentChatOrchestrator.Context {
    private final BotEntry entry;

    BotChatOrchestratorContext(BotEntry entry) {
        this.entry = entry;
    }

    @Override
    public void markActive() {
        BotChatManager.markOwnerActive(entry);
    }

    @Override
    public boolean hasPendingAction() {
        return entry.pendingAction != null;
    }

    @Override
    public AgentPendingChatActionFlow.PendingActionState pendingActionState() {
        return BotChatManager.pendingActionState(entry);
    }

    @Override
    public AgentPendingChatActionFlow.PendingActionCallbacks pendingActionCallbacks() {
        return BotChatManager.pendingActionCallbacks(entry);
    }

    @Override
    public AgentChatSessionRequestFlow.SessionRequestCallbacks sessionRequestCallbacks() {
        return BotChatManager.sessionRequestCallbacks(entry);
    }

    @Override
    public AgentChatSupplyRequestFlow.SupplyRequestCallbacks supplyRequestCallbacks() {
        return BotChatManager.supplyRequestCallbacks(entry);
    }

    @Override
    public AgentChatSocialFlow.SocialCallbacks socialCallbacks() {
        return BotChatManager.socialCallbacks(entry);
    }

    @Override
    public AgentChatToggleFlow.ToggleCallbacks toggleCallbacks() {
        return BotChatManager.toggleCallbacks(entry);
    }

    @Override
    public AgentChatBuffQueryFlow.BuffQueryCallbacks buffQueryCallbacks() {
        return BotChatManager.buffQueryCallbacks(entry);
    }

    @Override
    public AgentChatRespecFlow.RespecCallbacks respecCallbacks() {
        return BotChatManager.respecCallbacks(entry);
    }

    @Override
    public AgentChatEquipmentFlow.EquipmentCallbacks equipmentCallbacks() {
        return BotChatManager.equipmentCallbacks(entry);
    }

    @Override
    public AgentChatMovementFlow.MovementCallbacks movementCallbacks() {
        return BotChatManager.movementCallbacks(entry);
    }

    @Override
    public boolean isWaitingForSpVariant() {
        return entry.spVariantPromptSent && entry.spVariant == null;
    }

    @Override
    public AgentChatBuildFlow.SpVariantCallbacks spVariantCallbacks() {
        return BotChatManager.spVariantCallbacks(entry);
    }

    @Override
    public boolean isWaitingForApBuild() {
        return entry.apPromptSent;
    }

    @Override
    public AgentChatBuildFlow.ApBuildCallbacks apBuildCallbacks() {
        return BotChatManager.apBuildCallbacks(entry);
    }

    @Override
    public AgentChatUtilityFlow.UtilityCallbacks utilityCallbacks() {
        return BotChatManager.utilityCallbacks(entry);
    }

    @Override
    public void handleTransferCommand(AgentChatTransferFlow.TransferCommand transferCommand, String message) {
        BotChatManager.handleTransferCommand(entry, transferCommand, message);
    }

    @Override
    public AgentChatTransferFlow.ItemQueryCallbacks itemQueryCallbacks() {
        return BotChatManager.itemQueryCallbacks(entry);
    }

    @Override
    public AgentChatReportFlow.ReportCallbacks reportCallbacks() {
        return BotChatManager.reportCallbacks(entry);
    }

    @Override
    public Job currentJob() {
        return entry.bot.getJob();
    }

    @Override
    public int level() {
        return entry.bot.getLevel();
    }

    @Override
    public AgentChatJobAdvancementFlow.JobAdvancementCallbacks jobAdvancementCallbacks() {
        return BotChatManager.jobAdvancementCallbacks(entry);
    }
}
