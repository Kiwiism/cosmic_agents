package server.agents.field;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Loads and validates the reproducible level 15-25 observation manifest. */
public final class AgentFieldObservationCatalogRepository {
    private static final String RESOURCE = "/agents/field/victoria-level15-25-observation-harness.json";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final AgentFieldObservationCatalogRepository DEFAULT = load();

    private final AgentFieldObservationCatalog catalog;
    private final Map<Integer, AgentFieldObservationCatalog.MapPreset> byMapId;

    AgentFieldObservationCatalogRepository(AgentFieldObservationCatalog catalog) {
        this.catalog = catalog;
        LinkedHashMap<Integer, AgentFieldObservationCatalog.MapPreset> indexed = new LinkedHashMap<>();
        for (AgentFieldObservationCatalog.MapPreset preset : catalog.maps()) {
            if (indexed.putIfAbsent(preset.mapId(), preset) != null) {
                throw new IllegalArgumentException("duplicate field-observation map " + preset.mapId());
            }
        }
        byMapId = Map.copyOf(indexed);
    }

    public static AgentFieldObservationCatalogRepository defaultRepository() {
        return DEFAULT;
    }

    public AgentFieldObservationCatalog catalog() {
        return catalog;
    }

    public List<AgentFieldObservationCatalog.MapPreset> maps() {
        return catalog.maps();
    }

    public List<AgentFieldObservationCatalog.MapPreset> maps(String group) {
        return catalog.maps().stream().filter(map -> map.group().equals(group)).toList();
    }

    public Optional<AgentFieldObservationCatalog.MapPreset> find(int mapId) {
        return Optional.ofNullable(byMapId.get(mapId));
    }

    private static AgentFieldObservationCatalogRepository load() {
        try (InputStream input = AgentFieldObservationCatalogRepository.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing field-observation catalog " + RESOURCE);
            }
            return new AgentFieldObservationCatalogRepository(
                    MAPPER.readValue(input, AgentFieldObservationCatalog.class));
        } catch (IOException failure) {
            throw new IllegalStateException("could not load field-observation catalog", failure);
        }
    }
}
