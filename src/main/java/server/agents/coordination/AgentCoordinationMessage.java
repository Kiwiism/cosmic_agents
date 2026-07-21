package server.agents.coordination;

/** Technical agent-to-agent message; presentation chat is a separate optional subscriber. */
public sealed interface AgentCoordinationMessage permits AgentSupplyNeedMessage,
        AgentConversationCoordinationMessage {
    int sourceAgentCharacterId();
    long cohortId();
    int mapId();
    long createdAtMillis();
}
