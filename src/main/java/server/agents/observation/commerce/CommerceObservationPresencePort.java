package server.agents.observation.commerce;

import client.Character;
import server.maps.MapleMap;

import java.awt.Point;

/** Live-presence seam used only by the observable Commerce cohort presentation. */
public interface CommerceObservationPresencePort {
    boolean live(Character character);

    int channel(Character character);

    MapleMap resolveMap(Character character, int mapId);

    void changeMap(Character character, MapleMap map, Point position);
}
