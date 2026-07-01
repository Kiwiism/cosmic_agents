package server.agents.capabilities.quest;

import client.BotClient;
import client.Character;
import client.QuestStatus;
import server.quest.Quest;

import java.util.ArrayList;
import java.util.List;

public final class AgentPartyQuestSyncService {
    private AgentPartyQuestSyncService() {
    }

    public static void syncPartyAgentsQuestStart(Character source, Quest quest, int npc) {
        if (quest == null) {
            return;
        }

        for (Character agent : partyAgents(source)) {
            if (agent.getQuest(quest).getStatus() == QuestStatus.Status.STARTED) {
                continue;
            }
            quest.forceStartWithActions(agent, resolveQuestNpc(source, quest, npc));
        }
    }

    public static void syncPartyAgentsQuestProgress(Character source, int questId, int infoNumber, String progress) {
        if (progress == null) {
            return;
        }

        Quest quest = Quest.getInstance(questId);
        int npc = resolveQuestNpc(source, quest, source.getQuest(quest).getNpc());
        for (Character agent : partyAgents(source)) {
            ensureQuestStarted(agent, quest, npc);
            agent.setQuestProgress(questId, infoNumber, progress);
        }
    }

    public static void syncPartyAgentsQuestComplete(Character source, Quest quest, int npc, Integer selection) {
        if (quest == null) {
            return;
        }

        int resolvedNpc = resolveQuestNpc(source, quest, npc);
        for (Character agent : partyAgents(source)) {
            ensureQuestStarted(agent, quest, resolvedNpc);
            quest.forceCompleteWithActions(agent, resolvedNpc, selection);
        }
    }

    static List<Character> partyAgents(Character source) {
        if (source == null || source.getParty() == null || source.getClient() instanceof BotClient) {
            return List.of();
        }

        List<Character> partyAgents = new ArrayList<>();
        for (Character member : source.getPartyMembersOnline()) {
            if (member == null || member.getId() == source.getId()) {
                continue;
            }
            if (member.getClient() instanceof BotClient) {
                partyAgents.add(member);
            }
        }
        return partyAgents;
    }

    private static void ensureQuestStarted(Character agent, Quest quest, int npc) {
        if (agent.getQuest(quest).getStatus() == QuestStatus.Status.STARTED) {
            return;
        }

        quest.forceStartWithActions(agent, npc);
    }

    static int resolveQuestNpc(Character source, Quest quest, int fallbackNpc) {
        if (fallbackNpc > 0) {
            return fallbackNpc;
        }

        if (source != null) {
            int sourceNpc = source.getQuest(quest).getNpc();
            if (sourceNpc > 0) {
                return sourceNpc;
            }
        }

        return constants.id.NpcId.MAPLE_ADMINISTRATOR;
    }
}
