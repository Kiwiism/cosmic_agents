package server.agents.capabilities.dialogue;

import client.Character;

public final class AgentSkillReportDecisionService {
    private AgentSkillReportDecisionService() {
    }

    public static AgentSkillReportFlow.SkillReportDecision skillReportDecision(Character agent) {
        return AgentSkillReportFlow.reportSkills(
                agent.isBeginnerJob(),
                agent.getRemainingSp(),
                AgentSkillDialogueReporter.collectLearnedBeginnerSkills(agent),
                AgentSkillDialogueReporter.remainingBeginnerSp(agent),
                AgentSkillDialogueReporter.collectLearnedSkillTrees(agent));
    }
}
