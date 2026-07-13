package server.agents.integration;

@AgentGatewayAffinity(
        value = AgentGatewayThreadAffinity.SHARD_SAFE_DIRECT,
        rationale = "Quest handles mutate only the owning Agent through existing authoritative quest APIs.")
public interface AgentQuestSyncGateway {
    AgentQuestSyncHandle getQuest(int questId);
}
