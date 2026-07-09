package server.agents.integration;

import client.Character;
import client.Client;

import java.sql.SQLException;

public interface AgentClientGateway {
    Client createHeadlessClient(int world, int channel);

    int createBackingCharacter(Client client, String name);

    Character loadBackingCharacter(int characterId, Client client) throws SQLException;

    boolean hasClient(Character character);

    boolean tryAcquire(Character character);

    void release(Character character);
}
