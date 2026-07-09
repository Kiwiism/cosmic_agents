package server.agents.capabilities.looting;

import client.Character;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentPacketGatewayRuntime;
import server.maps.MapItem;

public final class AgentLootCleanupService {
    private AgentLootCleanupService() {
    }

    public static void cleanupGhostDrop(Character agent, MapItem drop) {
        if (drop == null) {
            return;
        }
        if (!drop.isPickedUp() && agent.getMap().getMapObject(drop.getObjectId()) == drop) {
            return;
        }

        for (Character player : agent.getMap().getAllPlayers()) {
            if (AgentCharacterGatewayRuntime.characters().isAgentCharacter(player)) {
                continue;
            }
            if (!player.isMapObjectVisible(drop)) {
                continue;
            }
            player.removeVisibleMapObject(drop);
            AgentPacketGatewayRuntime.packets().sendRemoveItemFromMap(player, drop.getObjectId(), 1, 0);
        }
    }
}
