package server.agents.integration.cosmic;

import client.BotClient;
import client.Character;
import client.Client;
import client.inventory.Inventory;
import client.inventory.Item;
import constants.inventory.ItemConstants;
import scripting.npc.NPCScriptManager;
import server.ItemInformationProvider;
import server.ItemInformationProvider.ScriptedItem;

final class CosmicHeadlessItemScriptGateway {
    private static final int MAX_SCRIPT_ADVANCES = config.AgentTuning.intValue(
            "server.agents.integration.cosmic.CosmicHeadlessNpcScriptGateway.MAX_SCRIPT_ADVANCES");

    private CosmicHeadlessItemScriptGateway() {
    }

    static boolean execute(Character agent, int itemId) {
        if (agent == null || !(agent.getClient() instanceof BotClient client)) {
            return false;
        }
        ScriptedItem scripted = ItemInformationProvider.getInstance().getScriptedItemInfo(itemId);
        if (scripted == null
                || ((scripted.getScript() == null || scripted.getScript().isBlank())
                && scripted.getNpc() <= 0)) {
            return false;
        }
        Inventory inventory = agent.getInventory(ItemConstants.getInventoryType(itemId));
        Item item = inventory == null ? null : inventory.findById(itemId);
        if (item == null || item.getQuantity() < 1) {
            return false;
        }

        NPCScriptManager scripts = NPCScriptManager.getInstance();
        synchronized (scripts) {
            client.removeClickedNPC();
            if (!scripts.start(client, scripted, agent)) {
                return false;
            }
            int advances = 0;
            while (scripts.getCM(client) != null && advances < MAX_SCRIPT_ADVANCES) {
                scripts.action(client, (byte) 1, (byte) 0, 0);
                advances++;
            }
            if (scripts.getCM(client) != null) {
                scripts.dispose(client);
                return false;
            }
        }
        return true;
    }
}
