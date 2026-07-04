package server.agents.integration;

import client.Character;
import client.Job;
import server.agents.capabilities.dialogue.AgentApBuildDialogueResolver;
import server.agents.capabilities.dialogue.AgentBuildDialogueClassifier;
import server.agents.capabilities.dialogue.AgentChatBuildFlow;
import server.agents.capabilities.dialogue.AgentChatJobAdvancementFlow;
import server.agents.capabilities.build.AgentStarterKitService;
import server.agents.capabilities.build.AgentBuildService;
import server.bots.BotEntry;

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
                AgentBotBuildStateRuntime.setSpVariant(entry, AgentBuildDialogueClassifier.ONE_HANDED_SP_VARIANT);
                AgentBotBuildReplyRuntime.replyNow(entry, AgentChatBuildFlow.oneHandedSpVariantReply());
                AgentBuildService.autoAssignSp(entry, bot(entry));
            }

            @Override
            public void twoHanded() {
                AgentBotBuildStateRuntime.setSpVariant(entry, AgentBuildDialogueClassifier.TWO_HANDED_SP_VARIANT);
                AgentBotBuildReplyRuntime.replyNow(entry, AgentChatBuildFlow.twoHandedSpVariantReply());
                AgentBuildService.autoAssignSp(entry, bot(entry));
            }
        };
    }

    public static AgentChatBuildFlow.ApBuildCallbacks apBuildCallbacks(BotEntry entry) {
        return new AgentChatBuildFlow.ApBuildCallbacks() {
            @Override
            public void requestBuildPrompt() {
                AgentBotBuildStateRuntime.clearApBuildPromptState(entry);
                String prompt = AgentBuildService.requestApBuildPrompt(entry, bot(entry));
                if (prompt != null) {
                    AgentBotBuildReplyRuntime.replyNow(entry, prompt);
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
            AgentBotBuildReplyRuntime.replyNow(entry, reply);
            AgentBotBuildSchedulerRuntime.afterRandomDelay(900, 1100, () -> AgentStarterKitService.advanceJob(entry, advJob));
        };
    }

    public static void confirmApBuild(BotEntry entry, String confirmMsg) {
        AgentBotBuildReplyRuntime.replyNow(entry, confirmMsg);
    }

    private static void handleApBuildSelection(BotEntry entry, String message) {
        Character bot = bot(entry);
        Job job = bot.getJob();
        AgentApBuildDialogueResolver.ApBuildChoice choice = AgentApBuildDialogueResolver.resolve(
                job, bot.getDex(), bot.getLuk(), bot.getStr(), message);
        if (choice != null) {
            applyApBuildChoice(entry, toBotApBuild(choice), choice.confirmMessage(), choice.alreadyMessage());
        }
    }

    private static AgentBuildService.ApBuild toBotApBuild(AgentApBuildDialogueResolver.ApBuildChoice choice) {
        return new AgentBuildService.ApBuild(
                toBotStatType(choice.primaryStat()),
                toBotStatType(choice.secondaryStat()),
                choice.secondaryTarget());
    }

    private static AgentBuildService.StatType toBotStatType(AgentApBuildDialogueResolver.StatType statType) {
        return switch (statType) {
            case STR -> AgentBuildService.StatType.STR;
            case DEX -> AgentBuildService.StatType.DEX;
            case INT -> AgentBuildService.StatType.INT;
            case LUK -> AgentBuildService.StatType.LUK;
        };
    }

    private static void applyApBuildChoice(
            BotEntry entry,
            AgentBuildService.ApBuild build,
            String confirmMsg,
            String alreadyMsg) {
        if (sameApBuild(AgentBotBuildStateRuntime.apBuild(entry), build)) {
            AgentBotBuildReplyRuntime.replyNow(entry, alreadyMsg);
            return;
        }
        AgentBuildService.setApBuild(entry, build, confirmMsg);
    }

    private static boolean sameApBuild(AgentBuildService.ApBuild left, AgentBuildService.ApBuild right) {
        return left != null
                && right != null
                && left.primaryStat() == right.primaryStat()
                && left.secondaryStat() == right.secondaryStat()
                && left.secondaryTarget() == right.secondaryTarget();
    }

    private static Character bot(BotEntry entry) {
        return AgentBotRuntimeIdentityRuntime.bot(entry);
    }
}
