package server.agents.integration;

public interface AgentServerAdapter {
    CharacterGateway characters();

    MapGateway maps();

    PacketGateway packets();

    CombatGateway combat();

    InventoryGateway inventory();

    TradeGateway trade();

    LifeGateway life();

    SkillGateway skills();

    MakerGateway maker();

    AgentQuestSyncGateway questSync();
}

