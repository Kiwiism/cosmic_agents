package server.agents.integration.cosmic;

import server.agents.integration.AgentServerAdapter;
import server.agents.integration.CombatGateway;
import server.agents.integration.InventoryGateway;
import server.agents.integration.PacketGateway;
import server.agents.integration.TradeGateway;

public final class CosmicAgentServerAdapter implements AgentServerAdapter {
    public static final CosmicAgentServerAdapter INSTANCE = new CosmicAgentServerAdapter();

    private CosmicAgentServerAdapter() {
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
}
