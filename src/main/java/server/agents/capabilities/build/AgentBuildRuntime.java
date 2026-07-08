package server.agents.capabilities.build;


import server.agents.runtime.AgentSchedulerRuntime;
import client.Character;
import client.Job;
import server.agents.capabilities.dialogue.AgentApBuildDialogueResolver;
import server.agents.capabilities.dialogue.AgentBuildDialogueClassifier;
import server.agents.capabilities.dialogue.AgentChatBuildFlow;
import server.agents.capabilities.dialogue.AgentChatJobAdvancementFlow;
import server.agents.integration.AgentReplyRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned build callback facade over AP/SP/job reply and scheduling flows.
 */
public final class AgentBuildRuntime {
    private AgentBuildRuntime() {
    }

    public static AgentChatBuildFlow.SpVariantCallbacks spVariantCallbacks(AgentRuntimeEntry entry) {
        return new AgentChatBuildFlow.SpVariantCallbacks() {
            @Override
            public void oneHanded() {
                AgentBuildStateRuntime.setSpVariant(entry, AgentBuildDialogueClassifier.ONE_HANDED_SP_VARIANT);
                AgentReplyRuntime.replyNow(entry, AgentChatBuildFlow.oneHandedSpVariantReply());
                AgentBuildService.autoAssignSp(entry, bot(entry));
            }

            @Override
            public void twoHanded() {
                AgentBuildStateRuntime.setSpVariant(entry, AgentBuildDialogueClassifier.TWO_HANDED_SP_VARIANT);
                AgentReplyRuntime.replyNow(entry, AgentChatBuildFlow.twoHandedSpVariantReply());
                AgentBuildService.autoAssignSp(entry, bot(entry));
            }
        };
    }

    public static AgentChatBuildFlow.ApBuildCallbacks apBuildCallbacks(AgentRuntimeEntry entry) {
        return new AgentChatBuildFlow.ApBuildCallbacks() {
            @Override
            public void requestBuildPrompt() {
                AgentBuildStateRuntime.clearApBuildPromptState(entry);
                String prompt = AgentBuildService.requestApBuildPrompt(entry, bot(entry));
                if (prompt != null) {
                    AgentReplyRuntime.replyNow(entry, prompt);
                }
            }

            @Override
            public void selectBuild(String message) {
                handleApBuildSelection(entry, message);
            }
        };
    }

    public static AgentChatJobAdvancementFlow.JobAdvancementCallbacks jobAdvancementCallbacks(AgentRuntimeEntry entry) {
        return advJob -> {
            String reply = AgentChatJobAdvancementFlow.jobChangeReply(advJob);
            AgentReplyRuntime.replyNow(entry, reply);
            AgentSchedulerRuntime.afterRandomDelay(900, 1100, () -> AgentStarterKitService.advanceJob(entry, advJob));
        };
    }

    public static void confirmApBuild(AgentRuntimeEntry entry, String confirmMsg) {
        AgentReplyRuntime.replyNow(entry, confirmMsg);
    }

    private static void handleApBuildSelection(AgentRuntimeEntry entry, String message) {
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
            AgentRuntimeEntry entry,
            AgentBuildService.ApBuild build,
            String confirmMsg,
            String alreadyMsg) {
        if (sameApBuild(AgentBuildStateRuntime.apBuild(entry), build)) {
            AgentReplyRuntime.replyNow(entry, alreadyMsg);
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

    private static Character bot(AgentRuntimeEntry entry) {
        return AgentRuntimeIdentityRuntime.bot(entry);
    }
}
