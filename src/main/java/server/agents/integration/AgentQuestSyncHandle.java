package server.agents.integration;

import client.Character;
import client.QuestStatus;

@AgentGatewayAffinity(
        value = AgentGatewayThreadAffinity.SHARD_SAFE_DIRECT,
        rationale = "Quest actions are scoped to the single-writer Agent session.")
public interface AgentQuestSyncHandle {
    int id();

    QuestStatus.Status status(Character character);

    int npc(Character character);

    void forceStartWithActions(Character character, int npc);

    void forceCompleteWithActions(Character character, int npc, Integer selection);
}
