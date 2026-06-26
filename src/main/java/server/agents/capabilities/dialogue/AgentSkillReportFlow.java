package server.agents.capabilities.dialogue;

import java.util.List;
import java.util.Map;

public final class AgentSkillReportFlow {
    private AgentSkillReportFlow() {
    }

    public static SkillReportDecision reportSkills(
            boolean beginnerJob,
            int remainingSp,
            List<SkillLine> beginnerSkills,
            int beginnerSpLeft,
            Map<Integer, List<SkillLine>> skillTrees) {
        if (beginnerJob) {
            return reportBeginnerSkills(beginnerSkills, beginnerSpLeft);
        }

        if (skillTrees.isEmpty()) {
            return SkillReportDecision.reply(AgentDialogueCatalog.noJobSkillsWithSpReply(remainingSp));
        }

        if (skillTrees.size() == 1) {
            Map.Entry<Integer, List<SkillLine>> onlyTree =
                    skillTrees.entrySet().iterator().next();
            return SkillReportDecision.replies(skillTreeReportLines(onlyTree.getKey(), onlyTree.getValue()));
        }

        return SkillReportDecision.pendingChoice(
                AgentDialogueReportFormatter.skillTreeChoicePrompt(skillTrees.keySet()));
    }

    public static SkillReportDecision resolveSkillTreeChoice(
            Map<Integer, List<SkillLine>> skillTrees,
            String message) {
        if (skillTrees.isEmpty()) {
            return SkillReportDecision.clearPendingReply(AgentDialogueCatalog.noJobSkillsReply());
        }

        if (skillTrees.size() == 1) {
            Map.Entry<Integer, List<SkillLine>> onlyTree =
                    skillTrees.entrySet().iterator().next();
            return SkillReportDecision.clearPendingReplies(
                    skillTreeReportLines(onlyTree.getKey(), onlyTree.getValue()));
        }

        Integer treeId = AgentBuildDialogueClassifier.resolveSkillTreeChoice(message, skillTrees.keySet());
        if (treeId == null) {
            return SkillReportDecision.reply(
                    AgentDialogueReportFormatter.skillTreeChoicePrompt(skillTrees.keySet()));
        }

        return SkillReportDecision.clearPendingReplies(skillTreeReportLines(treeId, skillTrees.get(treeId)));
    }

    private static SkillReportDecision reportBeginnerSkills(
            List<SkillLine> beginnerSkills,
            int beginnerSpLeft) {
        if (beginnerSkills.isEmpty()) {
            return SkillReportDecision.reply(AgentDialogueCatalog.noBeginnerSkillsReply(beginnerSpLeft));
        }

        return SkillReportDecision.reply(
                AgentDialogueReportFormatter.beginnerSkillReport(beginnerSkills, beginnerSpLeft));
    }

    private static List<String> skillTreeReportLines(
            int treeId,
            List<SkillLine> skills) {
        if (skills == null || skills.isEmpty()) {
            return List.of(AgentDialogueCatalog.noLearnedSkillsInReply(
                    AgentDialogueReportFormatter.skillTreeLabel(treeId)));
        }

        return AgentDialogueReportFormatter.skillTreeReportLines(treeId, skills);
    }

    public record SkillLine(int id, String name, int level) {
    }

    public record SkillReportDecision(
            List<String> replies,
            boolean requestSkillTreeChoice,
            boolean clearPendingAction) {
        private static SkillReportDecision reply(String reply) {
            return replies(List.of(reply));
        }

        private static SkillReportDecision replies(List<String> replies) {
            return new SkillReportDecision(List.copyOf(replies), false, false);
        }

        private static SkillReportDecision pendingChoice(String reply) {
            return new SkillReportDecision(List.of(reply), true, false);
        }

        private static SkillReportDecision clearPendingReply(String reply) {
            return clearPendingReplies(List.of(reply));
        }

        private static SkillReportDecision clearPendingReplies(List<String> replies) {
            return new SkillReportDecision(List.copyOf(replies), false, true);
        }
    }
}
