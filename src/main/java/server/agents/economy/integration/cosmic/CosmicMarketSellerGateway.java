package server.agents.economy.integration.cosmic;

import client.Character;
import client.inventory.InventoryType;
import server.agents.capabilities.shop.AgentFreeMarketStallCapability;
import server.agents.capabilities.runtime.AgentCapabilityInvocation;
import server.agents.capabilities.runtime.AgentCapabilityRuntime;
import server.agents.economy.market.FreeMarketPhysicalGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.maps.reservation.FreeMarketStorePlacementService;

import java.util.Objects;

public final class CosmicMarketSellerGateway {
    private final RemoteNpcCommerceService npc;
    private final int permitItemId;
    private final long stallOpenTimeoutMs;

    public CosmicMarketSellerGateway(RemoteNpcCommerceService npc, int permitItemId,
                                     long stallOpenTimeoutMs) {
        this.npc = Objects.requireNonNull(npc); this.permitItemId = permitItemId;
        this.stallOpenTimeoutMs = stallOpenTimeoutMs;
    }

    public RemoteNpcCommerceService.Receipt sellNpc(Character agent, MarketSellerPlan.NpcSale sale) {
        return npc.sell(agent, sale.npcId(), sale.inventoryType(), sale.slot(), sale.quantity());
    }

    public FreeMarketPhysicalGateway.ActionStatus requestOpen(Character agent, MarketSellerPlan plan) {
        if (agent.getPlayerShop() != null && agent.getPlayerShop().isOwner(agent)
                && agent.getPlayerShop().isOpen()) return FreeMarketPhysicalGateway.ActionStatus.ARRIVED;
        if (agent.getMapId() != plan.preferredRoomMapId()) return FreeMarketPhysicalGateway.ActionStatus.UNAVAILABLE;
        if (agent.getInventory(InventoryType.CASH).countById(permitItemId) < 1)
            return FreeMarketPhysicalGateway.ActionStatus.UNAVAILABLE;
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByCharacterInstance(agent);
        if (entry == null) return FreeMarketPhysicalGateway.ActionStatus.UNAVAILABLE;
        if (entry.capabilityRuntimeState().hasActiveCapability())
            return FreeMarketPhysicalGateway.ActionStatus.IN_PROGRESS;
        boolean assigned = AgentCapabilityRuntime.assign(entry, new AgentCapabilityInvocation<>(
                new AgentFreeMarketStallCapability(), new AgentFreeMarketStallCapability.Command(
                plan.preferredRoomMapId(), plan.stallDescription(), permitItemId, plan.stallListings()),
                stallOpenTimeoutMs, 2));
        return assigned ? FreeMarketPhysicalGateway.ActionStatus.ASSIGNED
                : FreeMarketPhysicalGateway.ActionStatus.IN_PROGRESS;
    }

    public boolean close(Character agent, String reason) {
        if (agent.getPlayerShop() == null || !agent.getPlayerShop().isOwner(agent)) return false;
        agent.closePlayerShop(reason);
        FreeMarketStorePlacementService.release(agent);
        return agent.getPlayerShop() == null;
    }
}
