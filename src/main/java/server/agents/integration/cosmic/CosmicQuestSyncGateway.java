package server.agents.integration.cosmic;

import client.Character;
import client.QuestStatus;
import server.agents.integration.AgentQuestSyncGateway;
import server.agents.integration.AgentQuestSyncHandle;
import server.quest.Quest;

public enum CosmicQuestSyncGateway implements AgentQuestSyncGateway {
    INSTANCE;

    @Override
    public AgentQuestSyncHandle getQuest(int questId) {
        Quest quest = Quest.getInstance(questId);
        return quest == null ? null : new CosmicQuestSyncHandle(quest);
    }

    private record CosmicQuestSyncHandle(Quest quest) implements AgentQuestSyncHandle {
        @Override
        public int id() {
            return quest.getId();
        }

        @Override
        public QuestStatus.Status status(Character character) {
            return character.getQuest(quest).getStatus();
        }

        @Override
        public int npc(Character character) {
            return character.getQuest(quest).getNpc();
        }

        @Override
        public void forceStartWithActions(Character character, int npc) {
            quest.forceStartWithActions(character, npc);
        }

        @Override
        public void forceCompleteWithActions(Character character, int npc, Integer selection) {
            quest.forceCompleteWithActions(character, npc, selection);
        }
    }
}
