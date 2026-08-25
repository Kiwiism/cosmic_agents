package server.agents.economy.integration.cosmic;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.manipulator.InventoryManipulator;
import constants.inventory.ItemConstants;
import server.economy.EconomyOperationKind;
import server.economy.EconomyTransactionCoordinator;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Grants only configured real permits and records them as an explicit FM venue subsidy. */
public final class CosmicMarketPermitProvisioner implements CosmicEconomyWorldAdapter.EntryProvisioner {
    private final UUID runId;
    private final long scenarioSeed;
    private final String policy;
    private final List<Integer> permitItemIds;

    public CosmicMarketPermitProvisioner(UUID runId, long scenarioSeed, String policy,
                                         List<Integer> permitItemIds) {
        this.runId = Objects.requireNonNull(runId);
        this.scenarioSeed = scenarioSeed;
        this.policy = Objects.requireNonNull(policy);
        this.permitItemIds = List.copyOf(permitItemIds);
        if (this.permitItemIds.isEmpty() || new HashSet<>(this.permitItemIds).size() != this.permitItemIds.size()
                || this.permitItemIds.stream().anyMatch(itemId -> !ItemConstants.isPlayerShop(itemId)))
            throw new IllegalArgumentException("verified PlayerShop permits are required");
    }

    @Override
    public Result provision(Character agent, String logicalAgentId, UUID requestId) {
        if (ownsAny(agent)) return Result.unchanged("ALREADY_OWNED");
        if (!"GRANT_RANDOM_REAL_PERMIT_ON_ENTRY".equals(policy))
            return Result.unchanged("OWNED_PERMIT_REQUIRED");
        int permitItemId = selectedPermit(logicalAgentId, requestId);
        if (!InventoryManipulator.checkSpace(agent.getClient(), permitItemId, 1, ""))
            return new Result(false, false, 0, "CASH_INVENTORY_HAS_NO_PERMIT_SPACE");
        String key = "fm-venue-permit:" + runId + ':' + logicalAgentId + ':' + requestId;
        EconomyTransactionCoordinator.execute(key, agent, null, EconomyOperationKind.VENUE_SUBSIDY,
                "venue=FREE_MARKET_ENTRY item=" + permitItemId + " quantity=1", context -> {
                    if (!InventoryManipulator.addById(agent.getClient(), permitItemId, (short) 1))
                        throw new IllegalStateException("cash inventory changed during FM permit grant");
                    context.recordEvidence("venueSubsidy", Map.of("venue", "FREE_MARKET_ENTRY",
                            "policy", policy, "itemId", permitItemId, "quantity", 1));
                });
        return new Result(true, true, permitItemId, "GRANTED_VENUE_SUBSIDY");
    }

    private boolean ownsAny(Character agent) {
        return permitItemIds.stream().anyMatch(itemId ->
                agent.getInventory(InventoryType.CASH).countById(itemId) > 0);
    }

    int selectedPermit(String logicalAgentId, UUID requestId) {
        UUID value = UUID.nameUUIDFromBytes((scenarioSeed + ":" + logicalAgentId + ":" + requestId)
                .getBytes(StandardCharsets.UTF_8));
        return permitItemIds.get(Math.floorMod(value.hashCode(), permitItemIds.size()));
    }
}
