package server.agents.integration;

public interface AgentServerAdapter {
    AgentClientGateway agentClients();

    CharacterGateway characters();

    MapGateway maps();

    PacketGateway packets();

    CombatGateway combat();

    InventoryGateway inventory();

    ShopGateway shop();

    TradeGateway trade();

    PartyGateway party();

    LifeGateway life();

    SkillGateway skills();

    MakerGateway maker();

    AgentQuestSyncGateway questSync();

    AgentPersistenceGateway persistence();
}

