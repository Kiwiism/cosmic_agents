package server.agents.integration.cosmic;

import server.agents.integration.AgentServerAdapter;
import server.agents.integration.AgentClientGateway;
import server.agents.integration.AgentQuestSyncGateway;
import server.agents.integration.CharacterGateway;
import server.agents.integration.CombatGateway;
import server.agents.integration.InventoryGateway;
import server.agents.integration.LifeGateway;
import server.agents.integration.MakerGateway;
import server.agents.integration.MapGateway;
import server.agents.integration.PacketGateway;
import server.agents.integration.PartyGateway;
import server.agents.integration.SkillGateway;
import server.agents.integration.ShopGateway;
import server.agents.integration.TradeGateway;

public final class CosmicAgentServerAdapter implements AgentServerAdapter {
    public static final CosmicAgentServerAdapter INSTANCE = new CosmicAgentServerAdapter();

    private CosmicAgentServerAdapter() {
    }

    @Override
    public AgentClientGateway agentClients() {
        return CosmicAgentClientGateway.INSTANCE;
    }

    @Override
    public CharacterGateway characters() {
        return CosmicCharacterGateway.INSTANCE;
    }

    @Override
    public MapGateway maps() {
        return CosmicMapGateway.INSTANCE;
    }

    @Override
    public PacketGateway packets() {
        return CosmicPacketGateway.INSTANCE;
    }

    @Override
    public CombatGateway combat() {
        return CosmicCombatGateway.INSTANCE;
    }

    @Override
    public InventoryGateway inventory() {
        return CosmicInventoryGateway.INSTANCE;
    }

    @Override
    public ShopGateway shop() {
        return CosmicShopGateway.INSTANCE;
    }

    @Override
    public TradeGateway trade() {
        return CosmicTradeGateway.INSTANCE;
    }

    @Override
    public PartyGateway party() {
        return CosmicPartyGateway.INSTANCE;
    }

    @Override
    public LifeGateway life() {
        return CosmicLifeGateway.INSTANCE;
    }

    @Override
    public SkillGateway skills() {
        return CosmicSkillGateway.INSTANCE;
    }

    @Override
    public MakerGateway maker() {
        return CosmicMakerGateway.INSTANCE;
    }

    @Override
    public AgentQuestSyncGateway questSync() {
        return CosmicQuestSyncGateway.INSTANCE;
    }
}
