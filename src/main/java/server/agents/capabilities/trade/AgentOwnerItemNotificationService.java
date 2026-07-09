package server.agents.capabilities.trade;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import constants.inventory.ItemConstants;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.util.List;

public final class AgentOwnerItemNotificationService {
    private AgentOwnerItemNotificationService() {
    }

    public static void notifyOwnerGainedItem(Character owner, Item item) {
        if (owner == null || item == null) {
            return;
        }
        if (ItemConstants.getInventoryType(item.getItemId()) != InventoryType.EQUIP) {
            return;
        }

        List<AgentRuntimeEntry> entries = AgentRuntimeRegistry.agentEntriesForLeader(
                AgentRuntimeRegistry.entriesByLeaderId(), owner.getId());
        if (entries.isEmpty()) {
            return;
        }

        AgentSchedulerRuntime.afterDelay(0L, () -> {
            for (AgentRuntimeEntry entry : entries) {
                AgentOfferService.notifyOwnerGainedEquip(entry, AgentRuntimeIdentityRuntime.bot(entry), item);
            }
        });
    }

    public static void notifyOwnerGainedTradeItem(Character recipient, Item item, Character source) {
        if (isItemFromOwnedAgent(recipient, source)) {
            return;
        }
        notifyOwnerGainedItem(recipient, item);
    }

    static boolean isItemFromOwnedAgent(Character owner, Character source) {
        if (owner == null || !AgentCharacterGatewayRuntime.characters().isAgentCharacter(source)) {
            return false;
        }
        Character activeOwner = AgentRuntimeRegistry.activeLeaderByAgentCharacterId(
                AgentRuntimeRegistry.entriesByLeaderId(), source.getId());
        return activeOwner != null && activeOwner.getId() == owner.getId();
    }
}
