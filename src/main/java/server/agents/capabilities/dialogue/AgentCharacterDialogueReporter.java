package server.agents.capabilities.dialogue;

import client.Character;

public final class AgentCharacterDialogueReporter {
    private AgentCharacterDialogueReporter() {
    }

    public static String statsReport(Character agent) {
        return AgentDialogueReportFormatter.stats(
                agent.getLevel(), AgentDialogueReportFormatter.jobDisplayName(agent.getJob()),
                agent.getStr(), agent.getDex(), agent.getInt(), agent.getLuk(),
                agent.getHp(), agent.getCurrentMaxHp(),
                agent.getMp(), agent.getCurrentMaxMp());
    }

    public static String buildReport(Character agent) {
        return AgentDialogueReportFormatter.build(
                agent.getStr(), agent.getDex(), agent.getInt(), agent.getLuk(),
                agent.getRemainingAp());
    }
}
