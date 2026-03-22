package client;

import net.packet.Packet;

/**
 * A headless client for bot/companion characters that have no real network connection.
 * sendPacket is a no-op, isLoggedIn always returns true, and updateLoginState/disconnect
 * skip the DB writes and SessionCoordinator operations that require a real session.
 */
public class BotClient extends Client {

    public BotClient(int world, int channel) {
        super(null, -1, "bot", null, world, channel);
    }

    @Override
    public void sendPacket(Packet packet) {
        // no-op: bot has no network socket
    }

    @Override
    public boolean isLoggedIn() {
        return true;
    }

    @Override
    public void updateLoginState(int newState) {
        // skip DB write and SessionCoordinator registration for bot clients
    }

}