package server.agents.field;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/** Loads the generated field-capacity snapshot used by the observation manifest. */
final class AgentFieldCapacityCatalogRepository {
    private static final String RESOURCE = "/agents/field/victoria-field-capacities.json";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private AgentFieldCapacityCatalogRepository() {
    }

    static AgentFieldCapacityCatalog load() {
        try (InputStream input = AgentFieldCapacityCatalogRepository.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing field capacity catalog " + RESOURCE);
            }
            return MAPPER.readValue(input, AgentFieldCapacityCatalog.class);
        } catch (IOException failure) {
            throw new IllegalStateException("could not load field capacity catalog", failure);
        }
    }

    static Map<Integer, AgentFieldCapacityCatalog.MapCapacity> index(AgentFieldCapacityCatalog catalog) {
        LinkedHashMap<Integer, AgentFieldCapacityCatalog.MapCapacity> indexed = new LinkedHashMap<>();
        for (AgentFieldCapacityCatalog.MapCapacity capacity : catalog.maps()) {
            if (indexed.putIfAbsent(capacity.mapId(), capacity) != null) {
                throw new IllegalArgumentException("duplicate field capacity map " + capacity.mapId());
            }
        }
        return Map.copyOf(indexed);
    }
}
