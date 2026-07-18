package server.agents.capabilities.dialogue;

@FunctionalInterface
public interface AgentDialogueObserverView {
    boolean hasAudience(int agentId, AgentDialogueAudience audience);
}
