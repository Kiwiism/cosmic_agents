package server.agents.economy.integration.cosmic;

import client.Character;
import constants.inventory.ItemConstants;
import client.inventory.Item;
import server.ItemInformationProvider;
import server.agents.economy.scenario.EconomyAgentProfile;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Restocks configured necessities through actual remote NPC Shop transactions. */
public final class CosmicNpcResourceProcurement
        implements AutonomousFreeMarketBehavior.ResourceProcurement {
    private final CosmicAgentNeedReader needs;
    private final RemoteNpcCommerceService commerce;
    private final RechargeCapacity rechargeCapacity;

    public CosmicNpcResourceProcurement(CosmicAgentNeedReader needs, RemoteNpcCommerceService commerce) {
        this(needs, commerce, (agent, itemId) -> ItemInformationProvider.getInstance()
                .getSlotMax(agent.getClient(), itemId));
    }

    CosmicNpcResourceProcurement(CosmicAgentNeedReader needs, RemoteNpcCommerceService commerce,
                                 RechargeCapacity rechargeCapacity) {
        this.needs = Objects.requireNonNull(needs); this.commerce = Objects.requireNonNull(commerce);
        this.rechargeCapacity = Objects.requireNonNull(rechargeCapacity);
    }

    @Override
    public Optional<Result> buyNext(Character agent, EconomyAgentProfile profile,
                                    Set<Integer> attemptedItemIds) {
        return needs.missingNpcResources(agent, profile).stream()
                .filter(value -> !attemptedItemIds.contains(value.itemId())).findFirst()
                .map(value -> {
                    int before = agent.getInventory(ItemConstants.getInventoryType(value.itemId()))
                            .countById(value.itemId());
                    boolean rechargeable = ItemConstants.isRechargeable(value.itemId());
                    Item depleted = rechargeable ? agent.getInventory(ItemConstants.getInventoryType(value.itemId()))
                            .listById(value.itemId()).stream().filter(item -> item.getQuantity()
                                    < rechargeCapacity.maximum(agent, value.itemId()))
                            .min(java.util.Comparator.comparingInt(Item::getQuantity)).orElse(null) : null;
                    var receipt = depleted != null
                            ? commerce.recharge(agent, value.npcId(), depleted.getPosition())
                            : commerce.buy(agent, value.npcId(), value.itemId(),
                            rechargeable ? (short) 1 : value.quantity() > Short.MAX_VALUE
                                    ? Short.MAX_VALUE : (short) value.quantity());
                    int after = agent.getInventory(ItemConstants.getInventoryType(value.itemId()))
                            .countById(value.itemId());
                    return new Result(value.itemId(), Math.max(0, after - before), value.npcId(), receipt.success(),
                            receipt.result(), receipt.mesoDelta(), receipt.sourceMapId(),
                            depleted == null ? "BUY" : "RECHARGE");
                });
    }

    @FunctionalInterface interface RechargeCapacity { int maximum(Character agent, int itemId); }
}
