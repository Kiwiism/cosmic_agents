package server.agents.economy.integration.cosmic;

import client.Character;
import constants.inventory.ItemConstants;
import server.agents.economy.scenario.EconomyAgentProfile;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Restocks configured necessities through actual remote NPC Shop transactions. */
public final class CosmicNpcResourceProcurement
        implements AutonomousFreeMarketBehavior.ResourceProcurement {
    private final CosmicAgentNeedReader needs;
    private final RemoteNpcCommerceService commerce;

    public CosmicNpcResourceProcurement(CosmicAgentNeedReader needs, RemoteNpcCommerceService commerce) {
        this.needs = Objects.requireNonNull(needs); this.commerce = Objects.requireNonNull(commerce);
    }

    @Override
    public Optional<Result> buyNext(Character agent, EconomyAgentProfile profile,
                                    Set<Integer> attemptedItemIds) {
        return needs.missingNpcResources(agent, profile).stream()
                .filter(value -> !attemptedItemIds.contains(value.itemId())).findFirst()
                .map(value -> {
                    int before = agent.getInventory(ItemConstants.getInventoryType(value.itemId()))
                            .countById(value.itemId());
                    var receipt = commerce.buy(agent, value.npcId(), value.itemId(),
                            ItemConstants.isRechargeable(value.itemId()) ? (short) 1
                                    : value.quantity() > Short.MAX_VALUE
                                    ? Short.MAX_VALUE : (short) value.quantity());
                    int after = agent.getInventory(ItemConstants.getInventoryType(value.itemId()))
                            .countById(value.itemId());
                    return new Result(value.itemId(), Math.max(0, after - before), value.npcId(), receipt.success(),
                            receipt.result(), receipt.mesoDelta(), receipt.sourceMapId());
                });
    }
}
