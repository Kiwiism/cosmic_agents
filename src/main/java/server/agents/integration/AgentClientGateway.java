package server.agents.integration;

import client.Character;
import client.Client;

import java.sql.SQLException;

@AgentGatewayAffinity(
        value = AgentGatewayThreadAffinity.ASYNC_EXTERNAL,
        rationale = "Backing-client creation and loading are lifecycle/SQL operations outside Agent ticks.")
public interface AgentClientGateway {
    Client createHeadlessClient(int world, int channel);

    int createBackingCharacter(Client client, String name);

    Character loadBackingCharacter(int characterId, Client client) throws SQLException;

    boolean hasClient(Character character);

    @AgentGatewayAffinity(
            value = AgentGatewayThreadAffinity.READ_ONLY_SNAPSHOT,
            rationale = "Client type is immutable for the lifetime of a live Agent or player session.")
    boolean isRealPlayer(Character character);

    boolean tryAcquire(Character character);

    void release(Character character);

    int world(Character character);

    int channel(Character character);
}
