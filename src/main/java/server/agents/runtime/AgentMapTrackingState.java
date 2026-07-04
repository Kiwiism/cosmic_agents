package server.agents.runtime;

import server.maps.Foothold;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Mutable map/foothold tracking state for a live Agent session.
 */
public final class AgentMapTrackingState {
    private int lastMapId = -1;
    private Map<Integer, Foothold> footholdIndex = new HashMap<>();

    public int lastMapId() {
        return lastMapId;
    }

    public Map<Integer, Foothold> footholdIndex() {
        return Collections.unmodifiableMap(footholdIndex);
    }

    public void setMapTracking(int mapId, Map<Integer, Foothold> footholdIndex) {
        this.lastMapId = mapId;
        this.footholdIndex = footholdIndex == null ? new HashMap<>() : new HashMap<>(footholdIndex);
    }
}
