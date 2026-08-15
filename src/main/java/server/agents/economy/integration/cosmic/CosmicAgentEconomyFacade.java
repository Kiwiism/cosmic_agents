package server.agents.economy.integration.cosmic;

import client.Character;
import client.inventory.InventoryType;
import server.agents.economy.ownership.*;

import java.time.Instant;
import java.util.*;

/** Cosmic boundary: only admitted economy participants are governed by this run-scoped facade. */
public final class CosmicAgentEconomyFacade {
    public static final String FM_REMOTE_NPC = "FM_REMOTE_NPC";
    public static final String WORLD_NPC = "WORLD_NPC";
    private final EconomyParticipantRegistry participants;
    private final AgentEconomyFacade facade;
    private final CosmicInventorySnapshotReader snapshots;

    public CosmicAgentEconomyFacade(EconomyParticipantRegistry participants, AgentEconomyFacade facade) {
        this(participants, facade, new CosmicInventorySnapshotReader());
    }

    CosmicAgentEconomyFacade(EconomyParticipantRegistry participants, AgentEconomyFacade facade,
                             CosmicInventorySnapshotReader snapshots) {
        this.participants = Objects.requireNonNull(participants); this.facade = Objects.requireNonNull(facade);
        this.snapshots = Objects.requireNonNull(snapshots);
    }

    public void onFreeMarketEntry(Character agent, String agentId, Instant logicalAt) {
        facade.protectAtFreeMarketEntry(agentId, snapshots.read(agent), logicalAt);
    }

    public MarketSellerPlan appraise(Character agent, String agentId, MarketSellerPlan plan,
                                     Instant logicalAt) {
        InventorySnapshot snapshot = snapshots.read(agent);
        List<LegacyDispositionProposal> proposals = new ArrayList<>();
        plan.npcSales().forEach(sale -> snapshot.find(sale.inventoryType().name(), sale.slot(), sale.itemId())
                .ifPresent(item -> proposals.add(new LegacyDispositionProposal(item.ref(), sale.quantity(),
                        LegacyDispositionProposal.Action.SELL_TO_NPC, sale.reason(), "NPC_ANYWHERE"))));
        plan.stallListings().forEach(listing -> {
            var liveItem = agent.getInventory(listing.inventoryType()).getItem(listing.slot());
            if (liveItem == null) return;
            snapshot.find(listing.inventoryType().name(), listing.slot(), liveItem.getItemId())
                    .ifPresent(item -> proposals.add(new LegacyDispositionProposal(item.ref(),
                            listing.perBundle() * listing.bundles(),
                            LegacyDispositionProposal.Action.LIST_IN_PLAYER_SHOP,
                            "LEGACY_PLAYER_SHOP_PLAN", "FREE_MARKET_STALL")));
        });
        InventoryReview review = facade.appraise(agentId, snapshot, proposals, logicalAt);
        Set<InventoryItemRef> authorizedNpc = review.decisions().stream()
                .filter(value -> value.disposition()
                        == InventoryDispositionDecision.Disposition.NPC_SALE_AUTHORIZED)
                .map(InventoryDispositionDecision::item).collect(java.util.stream.Collectors.toSet());
        Set<InventoryItemRef> reservedListings = review.decisions().stream()
                .filter(value -> value.disposition()
                        == InventoryDispositionDecision.Disposition.PLAYER_SHOP_LISTING_RESERVED)
                .map(InventoryDispositionDecision::item).collect(java.util.stream.Collectors.toSet());
        List<MarketSellerPlan.NpcSale> npc = plan.npcSales().stream().filter(sale ->
                snapshot.find(sale.inventoryType().name(), sale.slot(), sale.itemId())
                        .map(item -> authorizedNpc.contains(item.ref())).orElse(false)).toList();
        List<server.agents.capabilities.shop.AgentFreeMarketStallService.Listing> listings =
                plan.stallListings().stream().filter(listing -> {
                    var item = agent.getInventory(listing.inventoryType()).getItem(listing.slot());
                    return item != null && snapshot.find(listing.inventoryType().name(), listing.slot(),
                                    item.getItemId()).map(value -> reservedListings.contains(value.ref())).orElse(false);
                }).toList();
        return new MarketSellerPlan(npc, listings, plan.preferredRoomMapId(), plan.stallDescription());
    }

    public AgentEconomyFacade.NpcSalePermit claimNpcSale(Character agent, InventoryType type,
                                                          short slot, int itemId, short quantity,
                                                          String venue, Instant logicalAt) {
        Optional<CosmicPublicTradeNegotiator.Participant> participant =
                participants.byCharacterId(agent.getId());
        if (participant.isEmpty()) return new AgentEconomyFacade.NpcSalePermit(
                true, "LEGACY_NON_PARTICIPANT", null);
        InventorySnapshot snapshot = snapshots.read(agent);
        Optional<InventoryItemSnapshot> item = snapshot.find(type.name(), slot, itemId);
        if (item.isEmpty()) return AgentEconomyFacade.NpcSalePermit.denied("STALE_OR_MISSING_ITEM");
        return facade.claimNpcSale(participant.orElseThrow().profile().agentId(), snapshot,
                item.orElseThrow().ref(), quantity, venue, logicalAt);
    }

    public boolean isParticipant(Character agent) { return participants.isAdmittedCharacter(agent.getId()); }
}
