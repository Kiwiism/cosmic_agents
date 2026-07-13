package client;

import net.packet.Packet;

import java.util.List;

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
    public void sendPackets(List<Packet> packets) {
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

    @Override
    public void disconnectSession() {
        // no-op: bot has no ioChannel — calling ioChannel.disconnect() would NPE
    }

    /**
     * Always report "just now" so TimeoutTask never considers this bot inactive.
     * Bots have no real network I/O and must never be auto-disconnected by the
     * inactivity checker.
     */
    @Override
    public long getLastPacket() {
        return System.currentTimeMillis();
    }
}
