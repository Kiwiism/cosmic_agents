package server.agents.integration;

public interface AgentServerAdapter {
    MapGateway maps();

    PacketGateway packets();

    CombatGateway combat();

    InventoryGateway inventory();

    TradeGateway trade();
}

