package server.agents.field;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/** Loads and validates the reproducible level 15-25 observation manifest. */
public final class AgentFieldObservationCatalogRepository {
    private static final String RESOURCE = "/agents/field/victoria-level15-25-observation-harness.json";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final AgentFieldObservationCatalogRepository DEFAULT = load();

    private final AgentFieldObservationCatalog catalog;
    private final Map<Integer, AgentFieldObservationCatalog.MapPreset> byMapId;
    private final List<NumberedMap> numberedMaps;
    private final Map<Integer, Integer> numberByMapId;

    AgentFieldObservationCatalogRepository(AgentFieldObservationCatalog catalog) {
        this.catalog = catalog;
        LinkedHashMap<Integer, AgentFieldObservationCatalog.MapPreset> indexed = new LinkedHashMap<>();
        for (AgentFieldObservationCatalog.MapPreset preset : catalog.maps()) {
            if (indexed.putIfAbsent(preset.mapId(), preset) != null) {
                throw new IllegalArgumentException("duplicate field-observation map " + preset.mapId());
            }
        }
        byMapId = Map.copyOf(indexed);
        numberedMaps = IntStream.range(0, catalog.maps().size())
                .mapToObj(index -> new NumberedMap(index + 1, catalog.maps().get(index)))
                .toList();
        LinkedHashMap<Integer, Integer> numbers = new LinkedHashMap<>();
        numberedMaps.forEach(numbered -> numbers.put(
                numbered.map().mapId(), numbered.number()));
        numberByMapId = Map.copyOf(numbers);
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

    public List<NumberedMap> numberedMaps() {
        return numberedMaps;
    }

    public Optional<NumberedMap> numberedMap(int number) {
        return number < 1 || number > numberedMaps.size()
                ? Optional.empty() : Optional.of(numberedMaps.get(number - 1));
    }

    public Optional<NumberedMap> numberedMapForMapId(int mapId) {
        Integer number = numberByMapId.get(mapId);
        return number == null ? Optional.empty() : numberedMap(number);
    }

    public NumberedMap relativeMap(int currentMapId, int offset) {
        if (offset == 0) {
            throw new IllegalArgumentException("observation map offset must not be zero");
        }
        Integer currentNumber = numberByMapId.get(currentMapId);
        if (currentNumber == null) {
            return offset > 0 ? numberedMaps.getFirst() : numberedMaps.getLast();
        }
        int targetIndex = Math.floorMod(currentNumber - 1 + offset, numberedMaps.size());
        return numberedMaps.get(targetIndex);
    }

    public record NumberedMap(
            int number,
            AgentFieldObservationCatalog.MapPreset map) {
        public NumberedMap {
            if (number < 1 || map == null) {
                throw new IllegalArgumentException("valid numbered observation map is required");
            }
        }
    }

    private static AgentFieldObservationCatalogRepository load() {
        try (InputStream input = AgentFieldObservationCatalogRepository.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing field-observation catalog " + RESOURCE);
            }
            AgentFieldObservationManifest manifest = MAPPER.readValue(
                    input, AgentFieldObservationManifest.class);
            Map<Integer, AgentFieldCapacityCatalog.MapCapacity> capacities =
                    AgentFieldCapacityCatalogRepository.index(AgentFieldCapacityCatalogRepository.load());
            List<AgentFieldObservationCatalog.MapPreset> maps = manifest.maps().stream()
                    .map(map -> compose(map, capacities.get(map.mapId())))
                    .toList();
            if (maps.size() != capacities.size()) {
                throw new IllegalStateException("field capacity catalog must exactly cover observation maps");
            }
            return new AgentFieldObservationCatalogRepository(new AgentFieldObservationCatalog(
                    manifest.schemaVersion(), manifest.harnessId(), manifest.rotationWindowMs(),
                    manifest.supplyDurationMs(), maps));
        } catch (IOException failure) {
            throw new IllegalStateException("could not load field-observation catalog", failure);
        }
    }

    private static AgentFieldObservationCatalog.MapPreset compose(
            AgentFieldObservationManifest.MapDefinition map,
            AgentFieldCapacityCatalog.MapCapacity capacity) {
        if (capacity == null) {
            throw new IllegalStateException("missing generated capacity for observation map " + map.mapId());
        }
        return new AgentFieldObservationCatalog.MapPreset(
                map.mapId(), map.mapName(), map.group(), map.level(),
                capacity.recommendedMinimum(), capacity.recommendedMaximum(),
                capacity.maximumAgents(), capacity.activeCounts(), capacity.partySizes(),
                capacity.source(), capacity.confidence(), map.allowedMobIds(), map.excludedMobIds());
    }
}
