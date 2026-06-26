package server.agents.capabilities.dialogue;

import client.Job;

public final class AgentChatOrchestrator {
    private AgentChatOrchestrator() {
    }

    public static boolean handle(String message, Context context) {
        context.markActive();
        if (!context.hasPendingAction()
                && AgentChatSessionRequestFlow.handle(message, context.sessionRequestCallbacks())) {
            return true;
        }
        if (context.hasPendingAction()) {
            AgentPendingChatActionFlow.handle(
                    context.pendingActionState(),
                    message,
                    context.pendingActionCallbacks());
            return true;
        }

        if (AgentChatSupplyRequestFlow.handle(message, context.supplyRequestCallbacks())) {
            return true;
        }
        if (AgentChatSocialFlow.handle(message, context.socialCallbacks())) {
            return true;
        }
        if (AgentChatToggleFlow.handle(message, context.toggleCallbacks())) {
            return true;
        }
        if (AgentChatBuffQueryFlow.handle(message, context.buffQueryCallbacks())) {
            return true;
        }
        if (AgentChatRespecFlow.handle(message, context.respecCallbacks())) {
            return true;
        }
        if (AgentChatEquipmentFlow.handle(message, context.equipmentCallbacks())) {
            return true;
        }

        AgentChatMovementFlow.handle(message, context.movementCallbacks());

        AgentChatBuildFlow.handleSpVariantSelection(
                message,
                context.isWaitingForSpVariant(),
                context.spVariantCallbacks());

        AgentChatBuildFlow.handleApBuildSelection(
                message,
                context.isWaitingForApBuild(),
                context.apBuildCallbacks());

        if (AgentChatUtilityFlow.handle(message, context.utilityCallbacks())) {
            return true;
        }

        AgentChatTransferFlow.TransferCommand transferCommand = AgentChatTransferFlow.matchTransferCommand(message);
        if (transferCommand != null) {
            context.handleTransferCommand(transferCommand, message);
            return true;
        }

        if (AgentChatTransferFlow.handleItemQuery(message, context.itemQueryCallbacks())) {
            return true;
        }

        if (AgentChatReportFlow.handle(message, context.reportCallbacks())) {
            return true;
        }

        AgentChatJobAdvancementFlow.handle(
                message,
                context.currentJob(),
                context.level(),
                context.jobAdvancementCallbacks());
        return false;
    }

    public interface Context {
        void markActive();

        boolean hasPendingAction();

        AgentPendingChatActionFlow.PendingActionState pendingActionState();

        AgentPendingChatActionFlow.PendingActionCallbacks pendingActionCallbacks();

        AgentChatSessionRequestFlow.SessionRequestCallbacks sessionRequestCallbacks();

        AgentChatSupplyRequestFlow.SupplyRequestCallbacks supplyRequestCallbacks();

        AgentChatSocialFlow.SocialCallbacks socialCallbacks();

        AgentChatToggleFlow.ToggleCallbacks toggleCallbacks();

        AgentChatBuffQueryFlow.BuffQueryCallbacks buffQueryCallbacks();

        AgentChatRespecFlow.RespecCallbacks respecCallbacks();

        AgentChatEquipmentFlow.EquipmentCallbacks equipmentCallbacks();

        AgentChatMovementFlow.MovementCallbacks movementCallbacks();

        boolean isWaitingForSpVariant();

        AgentChatBuildFlow.SpVariantCallbacks spVariantCallbacks();

        boolean isWaitingForApBuild();

        AgentChatBuildFlow.ApBuildCallbacks apBuildCallbacks();

        AgentChatUtilityFlow.UtilityCallbacks utilityCallbacks();

        void handleTransferCommand(AgentChatTransferFlow.TransferCommand transferCommand, String message);

        AgentChatTransferFlow.ItemQueryCallbacks itemQueryCallbacks();

        AgentChatReportFlow.ReportCallbacks reportCallbacks();

        Job currentJob();

        int level();

        AgentChatJobAdvancementFlow.JobAdvancementCallbacks jobAdvancementCallbacks();
    }
}
