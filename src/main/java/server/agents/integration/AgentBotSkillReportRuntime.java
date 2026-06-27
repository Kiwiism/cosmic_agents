package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatReportRuntime;
import server.agents.capabilities.dialogue.AgentSkillDialogueReporter;
import server.agents.capabilities.dialogue.AgentSkillReportFlow;
import server.bots.BotEntry;

import java.util.List;
import java.util.Map;

/**
 * Temporary Agent-owned bridge for skill report data while pending action
 * mutations still live in the bot runtime.
 */
public final class AgentBotSkillReportRuntime {
    private AgentBotSkillReportRuntime() {
    }

    public static void reportSkills(BotEntry entry, Character bot) {
        AgentChatReportRuntime.reportSkills(
                bot.isBeginnerJob(),
                bot.getRemainingSp(),
                collectLearnedBeginnerSkills(bot),
                AgentSkillDialogueReporter.remainingBeginnerSp(bot),
                collectLearnedSkillTrees(bot),
                decision -> AgentBotPendingActionRuntime.applySkillReportDecision(entry, decision));
    }

    static AgentSkillReportFlow.SkillReportDecision skillReportDecision(Character bot) {
        return AgentSkillReportFlow.reportSkills(
                bot.isBeginnerJob(),
                bot.getRemainingSp(),
                collectLearnedBeginnerSkills(bot),
                AgentSkillDialogueReporter.remainingBeginnerSp(bot),
                collectLearnedSkillTrees(bot));
    }

    private static Map<Integer, List<AgentSkillReportFlow.SkillLine>> collectLearnedSkillTrees(Character bot) {
        return AgentSkillDialogueReporter.collectLearnedSkillTrees(bot);
    }

    private static List<AgentSkillReportFlow.SkillLine> collectLearnedBeginnerSkills(Character bot) {
        return AgentSkillDialogueReporter.collectLearnedBeginnerSkills(bot);
    }
}
