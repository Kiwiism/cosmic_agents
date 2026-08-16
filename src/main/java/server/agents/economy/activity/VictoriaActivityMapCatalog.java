package server.agents.economy.activity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import server.agents.economy.scenario.EconomyConfigException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Exact generated WZ map/spawn facts used only to choose calibration candidates. */
public final class VictoriaActivityMapCatalog {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final List<MapFact> maps;

    public VictoriaActivityMapCatalog(String resource) {
        this(load(resource));
    }

    VictoriaActivityMapCatalog(List<MapFact> maps) {
        this.maps = List.copyOf(maps);
        if (this.maps.isEmpty()) throw new IllegalArgumentException("activity map catalog is empty");
    }

    public List<MapFact> candidates(int level) {
        return maps.stream().filter(map -> !map.monsterIds().isEmpty()
                        && map.minMobLevel() <= level + 5 && map.maxMobLevel() >= Math.max(1, level - 8)
                        && map.maxMobLevel() <= level + 10)
                .sorted(Comparator.comparingInt((MapFact map) -> distance(level, map))
                        .thenComparing(Comparator.comparingInt(MapFact::spawnEntries).reversed())
                        .thenComparingInt(MapFact::mapId)).toList();
    }

    private static int distance(int level, MapFact map) {
        return Math.abs(level - (map.minMobLevel() + map.maxMobLevel()) / 2);
    }

    private static List<MapFact> load(String resource) {
        try (InputStream input = VictoriaActivityMapCatalog.class.getResourceAsStream(resource)) {
            if (input == null) throw new EconomyConfigException("Missing activity map catalog " + resource);
            JsonNode root = JSON.readTree(input);
            if (root.path("schemaVersion").asInt() != 1 || root.path("revision").asText().isBlank())
                throw new EconomyConfigException("Unversioned activity map catalog " + resource);
            List<MapFact> result = new ArrayList<>();
            for (JsonNode entry : root.path("entries")) {
                List<Integer> monsters = new ArrayList<>();
                for (JsonNode mob : entry.path("mobs")) monsters.add(mob.path("mobId").asInt());
                if (!monsters.isEmpty()) result.add(new MapFact(entry.path("mapId").asInt(),
                        entry.path("minMobLevel").asInt(), entry.path("maxMobLevel").asInt(),
                        entry.path("totalSpawnEntries").asInt(), monsters));
            }
            return result;
        } catch (IOException failure) {
            throw new EconomyConfigException("Could not read activity map catalog " + resource, failure);
        }
    }

    public record MapFact(int mapId, int minMobLevel, int maxMobLevel,
                          int spawnEntries, List<Integer> monsterIds) {
        public MapFact { monsterIds = List.copyOf(monsterIds); }
    }
}
