package server.agents.economy.integration.cosmic;

import client.Character;
import client.inventory.InventoryType;
import constants.inventory.ItemConstants;
import server.agents.economy.scenario.EconomyEngineConfig;

import java.util.Collection;

/** Reports the real permit constraint without silently manufacturing Cash Shop inventory. */
public final class PlayerShopPermitPreflight {
    public Readiness inspect(EconomyEngineConfig.Bootstrap config, Collection<Character> agents) {
        if (!ItemConstants.isPlayerShop(config.shopPermitItemId)) {
            throw new IllegalStateException("Configured shop permit is not a real Cosmic PlayerShop item");
        }
        int owned = 0;
        for (Character agent : agents) {
            if (agent.getInventory(InventoryType.CASH).countById(config.shopPermitItemId) > 0) owned++;
        }
        boolean external = "EXPLICIT_BOOTSTRAP_ENDOWMENT".equals(config.shopPermitPolicy);
        String limitation = owned == agents.size() ? "NONE"
                : external ? "MISSING_JOURNALED_BOOTSTRAP_ENDOWMENTS"
                : "AGENTS_WITHOUT_REAL_PERMITS_CANNOT_OPEN_STALLS";
        return new Readiness(agents.size(), owned, agents.size() - owned, external, limitation);
    }

    public record Readiness(int agents, int agentsWithPermit, int agentsWithoutPermit,
                            boolean explicitEndowmentConfigured, String limitation) { }
}
