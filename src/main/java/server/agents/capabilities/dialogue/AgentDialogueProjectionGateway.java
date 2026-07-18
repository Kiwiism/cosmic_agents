package server.agents.capabilities.dialogue;

@FunctionalInterface
public interface AgentDialogueProjectionGateway {
    void project(AgentDialogueIntentEvent intent);
}
