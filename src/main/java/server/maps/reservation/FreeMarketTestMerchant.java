package server.maps.reservation;

import client.Character;
import client.Client;
import server.maps.HiredMerchant;
import tools.PacketCreator;

import java.sql.SQLException;

/** Runtime-only merchant used by the FM spot visualization command. */
public final class FreeMarketTestMerchant extends HiredMerchant {
    private final int syntheticOwnerId;
    private final String syntheticOwnerName;

    public FreeMarketTestMerchant(
            Character mockOwner,
            int syntheticOwnerId,
            String syntheticOwnerName,
            String description,
            int permitItemId) {
        super(mockOwner, description, permitItemId);
        this.syntheticOwnerId = syntheticOwnerId;
        this.syntheticOwnerName = syntheticOwnerName;
    }

    @Override
    public int getOwnerId() {
        return syntheticOwnerId;
    }

    @Override
    public String getOwner() {
        return syntheticOwnerName;
    }

    @Override
    public boolean isOwner(Character chr) {
        return false;
    }

    @Override
    public void buy(Client client, int item, short quantity) {
        client.getPlayer().dropMessage(1,
                "This is a runtime-only Free Market layout test stall; purchases are disabled.");
        client.sendPacket(PacketCreator.enableActions());
    }

    @Override
    public void saveItems(boolean shutdown) throws SQLException {
        // Test listings are deliberately not persisted into Fredrick storage.
    }
}
