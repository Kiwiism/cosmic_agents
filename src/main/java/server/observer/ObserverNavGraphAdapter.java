package server.observer;

import client.Client;
import net.packet.InPacket;

public interface ObserverNavGraphAdapter {
    void handle(InPacket packet, Client client);
}
