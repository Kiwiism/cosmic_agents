package server.agents.integration.cosmic;

import client.BotClient;
import client.Character;
import client.Client;
import scripting.quest.QuestScriptManager;

final class CosmicHeadlessQuestScriptGateway {
    private static final int MAX_SCRIPT_ADVANCES = config.AgentTuning.intValue("server.agents.integration.cosmic.CosmicHeadlessQuestScriptGateway.MAX_SCRIPT_ADVANCES");

    private CosmicHeadlessQuestScriptGateway() {
    }

    static boolean start(Character agent, int questId, int npcId) {
        return execute(agent, questId, npcId, true, 1);
    }

    static boolean complete(Character agent, int questId, int npcId) {
        return execute(agent, questId, npcId, false, 2);
    }

    private static boolean execute(Character agent,
                                   int questId,
                                   int npcId,
                                   boolean starting,
                                   int expectedStatus) {
        Client client = agent.getClient();
        if (!(client instanceof BotClient)) {
            return false;
        }

        QuestScriptManager scripts = QuestScriptManager.getInstance();
        synchronized (scripts) {
            client.removeClickedNPC();
            if (starting) {
                scripts.start(client, (short) questId, npcId);
            } else {
                scripts.end(client, (short) questId, npcId);
            }

            int advances = 0;
            while (scripts.getQM(client) != null && advances++ < MAX_SCRIPT_ADVANCES) {
                if (starting) {
                    scripts.start(client, (byte) 1, (byte) 0, 0);
                } else {
                    scripts.end(client, (byte) 1, (byte) 0, 0);
                }
            }
            if (scripts.getQM(client) != null) {
                scripts.dispose(client);
            }
        }
        return agent.getQuestStatus(questId) == expectedStatus;
    }
}
