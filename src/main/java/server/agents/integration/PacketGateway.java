package server.agents.integration;

import client.Character;

public interface PacketGateway {
    void broadcastMovePlayer(Character agent, byte[] movementData);
}

