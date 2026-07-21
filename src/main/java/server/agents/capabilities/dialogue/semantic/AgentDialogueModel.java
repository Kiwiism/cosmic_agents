package server.agents.capabilities.dialogue.semantic;

/** Pluggable producer of one semantic dialogue act from a typed context. */
public interface AgentDialogueModel<C> {
    String modelId();

    AgentSemanticDialogueAct produce(C context);
}
