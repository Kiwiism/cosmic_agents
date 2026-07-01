package server.agents.capabilities.looting;

import client.BotClient;
import client.Character;
import net.packet.Packet;
import server.maps.MapItem;
import tools.PacketCreator;

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

        Packet removePacket = PacketCreator.removeItemFromMap(drop.getObjectId(), 1, 0);
        for (Character player : agent.getMap().getAllPlayers()) {
            if (player.getClient() instanceof BotClient) {
                continue;
            }
            if (!player.isMapObjectVisible(drop)) {
                continue;
            }
            player.removeVisibleMapObject(drop);
            player.sendPacket(removePacket);
        }
    }
}
