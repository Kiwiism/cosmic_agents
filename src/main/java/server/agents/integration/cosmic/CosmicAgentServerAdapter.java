package server.agents.integration.cosmic;

import server.agents.integration.AgentServerAdapter;
import server.agents.integration.CharacterGateway;
import server.agents.integration.CombatGateway;
import server.agents.integration.InventoryGateway;
import server.agents.integration.LifeGateway;
import server.agents.integration.MapGateway;
import server.agents.integration.PacketGateway;
import server.agents.integration.TradeGateway;

public final class CosmicAgentServerAdapter implements AgentServerAdapter {
    public static final CosmicAgentServerAdapter INSTANCE = new CosmicAgentServerAdapter();

    private CosmicAgentServerAdapter() {
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
    public TradeGateway trade() {
        return CosmicTradeGateway.INSTANCE;
    }

    @Override
    public LifeGateway life() {
        return CosmicLifeGateway.INSTANCE;
    }
}
