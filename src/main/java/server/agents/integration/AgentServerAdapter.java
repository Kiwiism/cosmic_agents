package server.agents.integration;

public interface AgentServerAdapter {
    PacketGateway packets();

    CombatGateway combat();

    InventoryGateway inventory();
}

