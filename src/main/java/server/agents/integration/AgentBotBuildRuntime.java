package server.agents.integration;

import client.Job;
import server.agents.capabilities.dialogue.AgentApBuildDialogueResolver;
import server.agents.capabilities.dialogue.AgentBuildDialogueClassifier;
import server.agents.capabilities.dialogue.AgentChatBuildFlow;
import server.agents.capabilities.dialogue.AgentChatJobAdvancementFlow;
import server.bots.BotBuildManager;
import server.bots.BotEntry;
import server.bots.BotManager;
import server.bots.BotStarterKitManager;

/**
 * Agent-owned build callback facade over temporary bot-side AP/SP/job side
 * effects.
 */
public final class AgentBotBuildRuntime {
    private AgentBotBuildRuntime() {
    }

    public static AgentChatBuildFlow.SpVariantCallbacks spVariantCallbacks(BotEntry entry) {
        return new AgentChatBuildFlow.SpVariantCallbacks() {
            @Override
            public void oneHanded() {
                entry.setSpVariant(AgentBuildDialogueClassifier.ONE_HANDED_SP_VARIANT);
                BotManager.getInstance().botReply(entry, AgentChatBuildFlow.oneHandedSpVariantReply());
                BotBuildManager.autoAssignSp(entry, entry.bot());
            }

            @Override
            public void twoHanded() {
                entry.setSpVariant(AgentBuildDialogueClassifier.TWO_HANDED_SP_VARIANT);
                BotManager.getInstance().botReply(entry, AgentChatBuildFlow.twoHandedSpVariantReply());
                BotBuildManager.autoAssignSp(entry, entry.bot());
            }
        };
    }

    public static AgentChatBuildFlow.ApBuildCallbacks apBuildCallbacks(BotEntry entry) {
        return new AgentChatBuildFlow.ApBuildCallbacks() {
            @Override
            public void requestBuildPrompt() {
                entry.clearApBuildPromptState();
                String prompt = BotBuildManager.requestApBuildPrompt(entry, entry.bot());
                if (prompt != null) {
                    BotManager.getInstance().botReply(entry, prompt);
                }
            }

            @Override
            public void selectBuild(String message) {
                handleApBuildSelection(entry, message);
            }
        };
    }

    public static AgentChatJobAdvancementFlow.JobAdvancementCallbacks jobAdvancementCallbacks(BotEntry entry) {
        return advJob -> {
            String reply = AgentChatJobAdvancementFlow.jobChangeReply(advJob);
            BotManager.getInstance().botReply(entry, reply);
            AgentBotSchedulerRuntime.afterRandomDelay(900, 1100, () -> BotStarterKitManager.advanceJob(entry, advJob));
        };
    }

    private static void handleApBuildSelection(BotEntry entry, String message) {
        Job job = entry.bot().getJob();
        AgentApBuildDialogueResolver.ApBuildChoice choice = AgentApBuildDialogueResolver.resolve(
                job, entry.bot().getDex(), entry.bot().getLuk(), entry.bot().getStr(), message);
        if (choice != null) {
            applyApBuildChoice(entry, toBotApBuild(choice), choice.confirmMessage(), choice.alreadyMessage());
        }
    }

    private static BotBuildManager.ApBuild toBotApBuild(AgentApBuildDialogueResolver.ApBuildChoice choice) {
        return new BotBuildManager.ApBuild(
                toBotStatType(choice.primaryStat()),
                toBotStatType(choice.secondaryStat()),
                choice.secondaryTarget());
    }

    private static BotBuildManager.StatType toBotStatType(AgentApBuildDialogueResolver.StatType statType) {
        return switch (statType) {
            case STR -> BotBuildManager.StatType.STR;
            case DEX -> BotBuildManager.StatType.DEX;
            case INT -> BotBuildManager.StatType.INT;
            case LUK -> BotBuildManager.StatType.LUK;
        };
    }

    private static void applyApBuildChoice(
            BotEntry entry,
            BotBuildManager.ApBuild build,
            String confirmMsg,
            String alreadyMsg) {
        if (sameApBuild(entry.apBuild(), build)) {
            BotManager.getInstance().botReply(entry, alreadyMsg);
            return;
        }
        BotBuildManager.setApBuild(entry, build, confirmMsg);
    }

    private static boolean sameApBuild(BotBuildManager.ApBuild left, BotBuildManager.ApBuild right) {
        return left != null
                && right != null
                && left.primaryStat() == right.primaryStat()
                && left.secondaryStat() == right.secondaryStat()
                && left.secondaryTarget() == right.secondaryTarget();
    }
}
