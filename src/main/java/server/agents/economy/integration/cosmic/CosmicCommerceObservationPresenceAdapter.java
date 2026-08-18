package server.agents.economy.integration.cosmic;

import client.Character;
import server.agents.observation.commerce.CommerceObservationPresencePort;
import server.maps.MapleMap;

import java.awt.Point;

/** Cosmic client/map implementation for the Commerce observation presentation port. */
public enum CosmicCommerceObservationPresenceAdapter
        implements CommerceObservationPresencePort {
    INSTANCE;

    @Override
    public boolean live(Character character) {
        return character != null && character.getClient() != null;
    }

    @Override
    public int channel(Character character) {
        return live(character) ? character.getClient().getChannel() : -1;
    }

    @Override
    public MapleMap resolveMap(Character character, int mapId) {
        if (!live(character)) {
            throw new IllegalStateException("Commerce observation participant is not live");
        }
        return character.getClient().getChannelServer().getMapFactory().getMap(mapId);
    }

    @Override
    public void changeMap(Character character, MapleMap map, Point position) {
        character.changeMap(map, position);
    }
}
