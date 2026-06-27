package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentCharacterDialogueReporter;

/**
 * Temporary Agent-owned bridge for character report lines backed by Cosmic
 * character state.
 */
public final class AgentBotCharacterReportRuntime {
    private AgentBotCharacterReportRuntime() {
    }

    public static String statsReport(Character bot) {
        return AgentCharacterDialogueReporter.statsReport(bot);
    }

    public static String buildReport(Character bot) {
        return AgentCharacterDialogueReporter.buildReport(bot);
    }

    public static String mesoReport(Character bot) {
        return AgentCharacterDialogueReporter.mesoReport(bot);
    }

    public static String expReport(Character bot) {
        return AgentCharacterDialogueReporter.expReport(bot);
    }
}
