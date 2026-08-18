package server.agents.field;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.awt.Point;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Loads the small authored layer that complements generic low-density safe-spot selection. */
public final class AgentFieldSafeSpotCatalogRepository {
    private static final String RESOURCE = "/agents/field/victoria-field-safe-spots.json";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final AgentFieldSafeSpotCatalogRepository DEFAULT = load();

    private final Map<Integer, AgentFieldSafeSpotCatalog.MapSpots> byMapId;

    AgentFieldSafeSpotCatalogRepository(AgentFieldSafeSpotCatalog catalog) {
        LinkedHashMap<Integer, AgentFieldSafeSpotCatalog.MapSpots> indexed = new LinkedHashMap<>();
        for (AgentFieldSafeSpotCatalog.MapSpots map : catalog.maps()) {
            if (indexed.putIfAbsent(map.mapId(), map) != null) {
                throw new IllegalArgumentException("duplicate field safe-spot map " + map.mapId());
            }
        }
        byMapId = Map.copyOf(indexed);
    }

    public static AgentFieldSafeSpotCatalogRepository defaultRepository() {
        return DEFAULT;
    }

    public Optional<Point> spot(int mapId, int ordinal) {
        AgentFieldSafeSpotCatalog.MapSpots map = byMapId.get(mapId);
        if (map == null) {
            return Optional.empty();
        }
        AgentFieldSafeSpotCatalog.Spot spot = map.spots().get(
                Math.floorMod(ordinal, map.spots().size()));
        return Optional.of(new Point(spot.x(), spot.y()));
    }

    private static AgentFieldSafeSpotCatalogRepository load() {
        try (InputStream input = AgentFieldSafeSpotCatalogRepository.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing field safe-spot catalog " + RESOURCE);
            }
            return new AgentFieldSafeSpotCatalogRepository(
                    MAPPER.readValue(input, AgentFieldSafeSpotCatalog.class));
        } catch (IOException failure) {
            throw new IllegalStateException("could not load field safe-spot catalog", failure);
        }
    }
}
