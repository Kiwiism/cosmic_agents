package server.agents.integration;

import client.Character;

public interface CombatGateway {
    boolean dispatchSyntheticPacket(Character agent, byte[] packetBytes);
}

