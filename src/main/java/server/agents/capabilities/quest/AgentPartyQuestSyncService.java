package server.agents.capabilities.quest;

import client.Character;
import client.QuestStatus;
import server.agents.integration.AgentQuestSyncGateway;
import server.agents.integration.AgentQuestSyncGatewayRuntime;
import server.agents.integration.AgentQuestSyncHandle;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.partner.PartnerInteractionPolicy;

import java.util.ArrayList;
import java.util.List;

public final class AgentPartyQuestSyncService {
    private AgentPartyQuestSyncService() {
    }

    public static void syncPartyAgentsQuestStart(Character source, int questId, int npc) {
        syncPartyAgentsQuestStart(source, questId, npc, AgentQuestSyncGatewayRuntime.quests());
    }

    static void syncPartyAgentsQuestStart(Character source, int questId, int npc, AgentQuestSyncGateway quests) {
        AgentQuestSyncHandle quest = quests.getQuest(questId);
        if (quest == null) {
            return;
        }

        for (Character agent : partyAgents(source)) {
            if (quest.status(agent) == QuestStatus.Status.STARTED) {
                continue;
            }
            quest.forceStartWithActions(agent, resolveQuestNpc(source, quest, npc));
        }
    }

    public static void syncPartyAgentsQuestProgress(Character source, int questId, int infoNumber, String progress) {
        if (progress == null) {
            return;
        }
        syncPartyAgentsQuestProgress(source, questId, infoNumber, progress, AgentQuestSyncGatewayRuntime.quests());
    }

    static void syncPartyAgentsQuestProgress(Character source, int questId, int infoNumber, String progress,
                                             AgentQuestSyncGateway quests) {
        if (progress == null) {
            return;
        }

        AgentQuestSyncHandle quest = quests.getQuest(questId);
        if (quest == null) {
            return;
        }
        int npc = resolveQuestNpc(source, quest, quest.npc(source));
        for (Character agent : partyAgents(source)) {
            ensureQuestStarted(agent, quest, npc);
            agent.setQuestProgress(questId, infoNumber, progress);
        }
    }

    public static void syncPartyAgentsQuestComplete(Character source, int questId, int npc, Integer selection) {
        syncPartyAgentsQuestComplete(source, questId, npc, selection, AgentQuestSyncGatewayRuntime.quests());
    }

    static void syncPartyAgentsQuestComplete(Character source, int questId, int npc, Integer selection,
                                             AgentQuestSyncGateway quests) {
        AgentQuestSyncHandle quest = quests.getQuest(questId);
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
        if (source == null || !AgentPartyGatewayRuntime.party().hasParty(source)
                || AgentCharacterGatewayRuntime.characters().isAgentCharacter(source)) {
            return List.of();
        }

        List<Character> partyAgents = new ArrayList<>();
        for (Character member : AgentPartyGatewayRuntime.party().onlineMembers(source)) {
            if (member == null || member.getId() == source.getId()) {
                continue;
            }
            if (AgentCharacterGatewayRuntime.characters().isAgentCharacter(member)
                    && !PartnerInteractionPolicy.isProtectedPartner(member)) {
                partyAgents.add(member);
            }
        }
        return partyAgents;
    }

    private static void ensureQuestStarted(Character agent, AgentQuestSyncHandle quest, int npc) {
        if (quest.status(agent) == QuestStatus.Status.STARTED) {
            return;
        }

        quest.forceStartWithActions(agent, npc);
    }

    static int resolveQuestNpc(Character source, AgentQuestSyncHandle quest, int fallbackNpc) {
        if (fallbackNpc > 0) {
            return fallbackNpc;
        }

        if (source != null) {
            int sourceNpc = quest.npc(source);
            if (sourceNpc > 0) {
                return sourceNpc;
            }
        }

        return constants.id.NpcId.MAPLE_ADMINISTRATOR;
    }
}
