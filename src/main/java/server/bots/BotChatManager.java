package server.bots;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatBuildFlow;
import server.agents.capabilities.dialogue.AgentChatBuffQueryFlow;
import server.agents.capabilities.dialogue.AgentChatCommandClassifier;
import server.agents.capabilities.dialogue.AgentChatPendingAction;
import server.agents.capabilities.dialogue.AgentChatRespecFlow;
import server.agents.capabilities.dialogue.AgentChatReportFlow;
import server.agents.capabilities.dialogue.AgentChatSocialFlow;
import server.agents.capabilities.dialogue.AgentChatSupplyRequestFlow;
import server.agents.capabilities.dialogue.AgentChatUtilityFlow;
import server.agents.capabilities.dialogue.AgentChatTransferFlow;
import server.agents.capabilities.dialogue.AgentChatEquipmentFlow;
import server.agents.capabilities.dialogue.AgentChatJobAdvancementFlow;
import server.agents.capabilities.dialogue.AgentChatMovementFlow;
import server.agents.capabilities.dialogue.AgentChatOrchestrator;
import server.agents.capabilities.dialogue.AgentChatSessionRequestFlow;
import server.agents.capabilities.dialogue.AgentChatToggleFlow;
import server.agents.capabilities.dialogue.AgentPendingChatActionFlow;
import server.agents.capabilities.dialogue.AgentSkillDialogueReporter;
import server.agents.capabilities.dialogue.AgentSkillReportFlow;

import java.awt.*;
import java.util.List;
import java.util.Map;

public class BotChatManager {
    // %s = current map name (bot is in town since the offline-return warp put it there).
    // Sent via party chat so the owner sees it across maps when they reconnect.
    static void markOwnerActive(BotEntry entry) {
        Character owner = entry.owner;
        entry.ownerWasAfk = false;
        entry.ownerAfkSinceMs = System.currentTimeMillis();
        entry.ownerAfkPos = owner != null ? new Point(owner.getPosition()) : null;
    }

    // Set true on entry; cleared to false only if we fall off the natural end of handleChat
    // (no command pattern matched). Every match path returns early, leaving this true. Caller
    // (BotManager) reads via wasLastChatHandled() to gate the LLM fallback.
    private static final ThreadLocal<Boolean> LAST_CHAT_HANDLED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static boolean wasLastChatHandled() {
        return LAST_CHAT_HANDLED.get();
    }

    static void handleChat(BotEntry entry, String message) {
        LAST_CHAT_HANDLED.set(AgentChatOrchestrator.handle(message, new BotChatOrchestratorContext(entry)));
    }

    static AgentPendingChatActionFlow.PendingActionState pendingActionState(BotEntry entry) {
        return new AgentPendingChatActionFlow.PendingActionState() {
            @Override
            public String pendingAction() {
                return entry.pendingAction;
            }

            @Override
            public String pendingDropCategory() {
                return entry.pendingDropCategory;
            }

            @Override
            public void clearPendingAction() {
                entry.pendingAction = null;
            }

            @Override
            public void clearPendingDropCategory() {
                entry.pendingDropCategory = null;
            }
        };
    }

    static AgentChatSessionRequestFlow.SessionRequestCallbacks sessionRequestCallbacks(BotEntry entry) {
        return BotChatSessionRuntime.sessionRequestCallbacks(entry);
    }

    static AgentChatToggleFlow.ToggleCallbacks toggleCallbacks(BotEntry entry) {
        return BotChatControlRuntime.toggleCallbacks(entry);
    }

    static AgentChatBuffQueryFlow.BuffQueryCallbacks buffQueryCallbacks(BotEntry entry) {
        return BotChatControlRuntime.buffQueryCallbacks(entry);
    }

    static AgentChatRespecFlow.RespecCallbacks respecCallbacks(BotEntry entry) {
        return BotChatControlRuntime.respecCallbacks(entry);
    }

    static AgentChatEquipmentFlow.EquipmentCallbacks equipmentCallbacks(BotEntry entry) {
        return BotChatEquipmentRuntime.equipmentCallbacks(entry);
    }

    static AgentChatSupplyRequestFlow.SupplyRequestCallbacks supplyRequestCallbacks(BotEntry entry) {
        return BotChatSupplyRuntime.supplyRequestCallbacks(entry);
    }

    static AgentChatSocialFlow.SocialCallbacks socialCallbacks(BotEntry entry) {
        return BotChatSocialRuntime.socialCallbacks(entry);
    }

    static AgentChatMovementFlow.MovementCallbacks movementCallbacks(BotEntry entry) {
        return BotChatMovementRuntime.movementCallbacks(entry);
    }

    static AgentChatUtilityFlow.UtilityCallbacks utilityCallbacks(BotEntry entry) {
        return BotChatUtilityRuntime.utilityCallbacks(entry);
    }

    static AgentChatBuildFlow.SpVariantCallbacks spVariantCallbacks(BotEntry entry) {
        return BotChatBuildRuntime.spVariantCallbacks(entry);
    }

    static AgentChatBuildFlow.ApBuildCallbacks apBuildCallbacks(BotEntry entry) {
        return BotChatBuildRuntime.apBuildCallbacks(entry);
    }

    static AgentChatReportFlow.ReportCallbacks reportCallbacks(BotEntry entry) {
        return new AgentChatReportFlow.ReportCallbacks() {
            @Override
            public void help() {
                BotManager.after(BotManager.randMs(500, 700), () -> BotChatReportRuntime.reportHelp(entry));
            }

            @Override
            public void requestUpgrade() {
                BotManager.after(BotManager.randMs(500, 700), () ->
                        BotChatSupplyRuntime.handleRequestUpgradeCommand(entry, entry.bot));
            }

            @Override
            public void recommendedGear() {
                BotManager.after(BotManager.randMs(500, 700), () -> BotChatReportRuntime.reportRecommendedGear(entry, entry.bot));
            }

            @Override
            public void skills() {
                BotManager.after(BotManager.randMs(900, 1100), () -> BotChatReportRuntime.reportSkills(entry, entry.bot));
            }

            @Override
            public void stats() {
                BotManager.after(BotManager.randMs(900, 1100), () -> BotChatReportRuntime.reportStats(entry, entry.bot));
            }

            @Override
            public void movementStats() {
                BotManager.after(BotManager.randMs(900, 1100), () -> BotChatReportRuntime.reportMovementStats(entry, entry.bot));
            }

            @Override
            public void range() {
                BotManager.after(BotManager.randMs(900, 1100), () -> BotChatReportRuntime.reportRange(entry, entry.bot));
            }

            @Override
            public void build() {
                BotManager.after(BotManager.randMs(900, 1100), () -> BotChatReportRuntime.reportBuild(entry, entry.bot));
            }

            @Override
            public void inventory() {
                BotManager.after(BotManager.randMs(900, 1100), () -> BotChatReportRuntime.reportInventory(entry, entry.bot));
            }

            @Override
            public void mesos() {
                BotManager.after(BotManager.randMs(900, 1100), () -> BotChatReportRuntime.reportMesos(entry, entry.bot));
            }

            @Override
            public void exp() {
                BotManager.after(BotManager.randMs(900, 1100), () -> BotChatReportRuntime.reportExp(entry, entry.bot));
            }

            @Override
            public void inventorySlots() {
                BotManager.after(BotManager.randMs(900, 1100), () -> BotChatReportRuntime.reportInventorySlots(entry, entry.bot));
            }

            @Override
            public void scrolls() {
                BotManager.after(BotManager.randMs(900, 1100), () -> BotChatReportRuntime.reportScrolls(entry, entry.bot));
            }

            @Override
            public void potions() {
                BotManager.after(BotManager.randMs(900, 1100), () -> BotChatReportRuntime.reportPotions(entry, entry.bot));
            }

            @Override
            public void debugStats() {
                BotManager.after(BotManager.randMs(900, 1100), () -> BotChatReportRuntime.reportDebugStats(entry, entry.bot));
            }

            @Override
            public void critDebug() {
                BotManager.after(BotManager.randMs(900, 1100), () -> BotChatReportRuntime.reportCritDebug(entry, entry.bot));
            }

            @Override
            public void potDebug() {
                BotManager.after(BotManager.randMs(900, 1100), () -> BotChatReportRuntime.reportPotDebug(entry, entry.bot));
            }
        };
    }

    static AgentChatJobAdvancementFlow.JobAdvancementCallbacks jobAdvancementCallbacks(BotEntry entry) {
        return BotChatBuildRuntime.jobAdvancementCallbacks(entry);
    }

    static AgentChatTransferFlow.ItemQueryCallbacks itemQueryCallbacks(BotEntry entry) {
        return BotChatTransferRuntime.itemQueryCallbacks(entry);
    }

    static AgentPendingChatActionFlow.PendingActionCallbacks pendingActionCallbacks(BotEntry entry) {
        return new AgentPendingChatActionFlow.PendingActionCallbacks() {
            @Override
            public void handleOwnerAwayChoice(String message) {
                BotChatSessionRuntime.handleOwnerAwayChoice(entry, message);
            }

            @Override
            public void executeItemChoice(String category, boolean trade) {
                BotManager.after(BotManager.randMs(400, 600),
                        () -> BotInventoryManager.executeChoice(category, trade, entry, entry.bot));
            }

            @Override
            public void cancelItemChoice() {
                BotManager.after(BotManager.randMs(400, 600),
                        () -> BotManager.getInstance().botReply(entry, AgentPendingChatActionFlow.keepDropChoiceReply()));
            }

            @Override
            public void handleSkillTreeChoice(String message) {
                BotChatManager.handleSkillTreeChoice(entry, entry.bot, message);
            }

            @Override
            public void confirmRelog() {
                BotChatSessionRuntime.scheduleRelogConfirm(entry);
            }

            @Override
            public void confirmLogout() {
                BotChatSessionRuntime.scheduleLogoutConfirm(entry);
            }

            @Override
            public void cancelPendingAction(boolean dropAction) {
                String cancelMsg = AgentPendingChatActionFlow.pendingActionCancelReply(dropAction);
                BotManager.after(BotManager.randMs(700, 900), () ->
                        BotManager.getInstance().botReply(entry, cancelMsg));
            }
        };
    }

    // -------------------------------------------------------------------------
    // Message queue — 5-second spacing between consecutive bot messages
    // -------------------------------------------------------------------------

    public static void queueBotSay(BotEntry entry, String message) {
        BotChatReplyRuntime.queueSay(entry, message);
    }

    static void queueBotReply(BotEntry entry, String message) {
        BotChatReplyRuntime.queueReply(entry, message);
    }

    static long queueBotSayWithEstimatedDelay(BotEntry entry, String message) {
        return BotChatReplyRuntime.queueSayWithEstimatedDelay(entry, message);
    }

    static long queueBotReplyWithEstimatedDelay(BotEntry entry, String message) {
        return BotChatReplyRuntime.queueReplyWithEstimatedDelay(entry, message);
    }

    // Status check — called on spawn, grind start, greeting, and level-up
    static void checkBotStatus(BotEntry entry, Character bot) {
        BotChatStatusRuntime.checkBotStatus(entry, bot);
    }

    /**
     * Announces the bot's town location via party chat after the owner reconnects
     * (or revives) following a 5+ min offline-or-dead window during which the bot
     * scrolled to town. Party chat reaches the owner even if they spawn back into
     * a different map.
     */
    static void announceOwnerReturnedFromOffline(BotEntry entry) {
        BotChatStatusRuntime.announceOwnerReturnedFromOffline(entry);
    }

    /** Detects owner AFK (same position ≥5 min) and says "wb" when they return. */
    static void tickAfkCheck(BotEntry entry, Character owner) {
        BotChatStatusRuntime.tickAfkCheck(entry, owner);
    }

    static String buildRangeReport(Character bot) {
        return BotChatReportRuntime.buildRangeReport(bot);
    }

    static String buildRangeReport(Character bot, BotEquipManager.MapDamageProfile mobProfile) {
        return BotChatReportRuntime.buildRangeReport(bot, mobProfile);
    }

    static List<String> buildMovementStatsReport(Character bot) {
        return BotChatReportRuntime.buildMovementStatsReport(bot);
    }

    private static void reportHelp(BotEntry entry) {
        BotChatReportRuntime.reportHelp(entry);
    }

    /** Returns true when the owner hasn't moved in ≥5 min (AFK). Skip chat interactions. */
    static boolean isOwnerIdle(BotEntry entry) {
        return BotChatStatusRuntime.isOwnerIdle(entry);
    }

    static int randomFidgetExpression() {
        return BotChatStatusRuntime.randomFidgetExpression();
    }

    private static void handleSkillTreeChoice(BotEntry entry, Character bot, String message) {
        Map<Integer, List<AgentSkillReportFlow.SkillLine>> skillTrees =
                AgentSkillDialogueReporter.collectLearnedSkillTrees(bot);
        applySkillReportDecision(entry, AgentSkillReportFlow.resolveSkillTreeChoice(skillTrees, message));
    }

    static void applySkillReportDecision(BotEntry entry, AgentSkillReportFlow.SkillReportDecision decision) {
        if (decision.clearPendingAction()) {
            entry.pendingAction = null;
        }
        if (decision.requestSkillTreeChoice()) {
            entry.pendingAction = AgentChatPendingAction.SKILL_TREE_CHOICE;
        }
        for (String line : decision.replies()) {
            queueBotReply(entry, line);
        }
    }

    static void handleTransferCommand(BotEntry entry, AgentChatTransferFlow.TransferCommand transferCommand, String message) {
        BotChatTransferRuntime.handleTransferCommand(entry, transferCommand, message);
    }

}
