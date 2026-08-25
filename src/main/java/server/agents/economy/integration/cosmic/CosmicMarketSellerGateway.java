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
    private final java.util.List<Integer> permitItemIds;
    private final long stallOpenTimeoutMs;

    public CosmicMarketSellerGateway(RemoteNpcCommerceService npc, int permitItemId,
                                     long stallOpenTimeoutMs) {
        this(npc, java.util.List.of(permitItemId), stallOpenTimeoutMs);
    }

    public CosmicMarketSellerGateway(RemoteNpcCommerceService npc, java.util.List<Integer> permitItemIds,
                                     long stallOpenTimeoutMs) {
        this.npc = Objects.requireNonNull(npc);
        this.permitItemIds = java.util.List.copyOf(permitItemIds);
        if (this.permitItemIds.isEmpty()) throw new IllegalArgumentException("shop permit pool is required");
        this.stallOpenTimeoutMs = stallOpenTimeoutMs;
    }

    public RemoteNpcCommerceService.Receipt sellNpc(Character agent, MarketSellerPlan.NpcSale sale,
                                                     java.time.Instant logicalAt) {
        return server.agents.integration.AgentEconomicActionGuardRuntime.withNpcSaleContext(
                logicalAt, CosmicAgentEconomyFacade.FM_REMOTE_NPC,
                () -> npc.sell(agent, sale.npcId(), sale.inventoryType(), sale.slot(), sale.quantity()));
    }

    public boolean hasPlayerShopPermit(Character agent) {
        return ownedPermit(agent) != 0;
    }

    public FreeMarketPhysicalGateway.ActionStatus requestOpen(Character agent, MarketSellerPlan plan) {
        if (agent.getPlayerShop() != null && agent.getPlayerShop().isOwner(agent)
                && agent.getPlayerShop().isOpen()) return FreeMarketPhysicalGateway.ActionStatus.ARRIVED;
        if (agent.getMapId() != plan.preferredRoomMapId()) return FreeMarketPhysicalGateway.ActionStatus.UNAVAILABLE;
        if (!hasPlayerShopPermit(agent))
            return FreeMarketPhysicalGateway.ActionStatus.UNAVAILABLE;
        int permitItemId = ownedPermit(agent);
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

    private int ownedPermit(Character agent) {
        return permitItemIds.stream().filter(itemId ->
                agent.getInventory(InventoryType.CASH).countById(itemId) > 0).findFirst().orElse(0);
    }

    public boolean close(Character agent, String reason) {
        if (agent.getPlayerShop() == null || !agent.getPlayerShop().isOwner(agent)) return false;
        agent.closePlayerShop(reason);
        FreeMarketStorePlacementService.release(agent);
        return agent.getPlayerShop() == null;
    }
}
