package server.agents.integration;

import client.Character;

public interface CombatGateway {
    int currentTimestamp();

    boolean dispatchSyntheticPacket(Character agent, byte[] packetBytes);
}

