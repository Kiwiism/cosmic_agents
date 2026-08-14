package server.agents.economy.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import server.agents.economy.scenario.EconomyConfigException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

/** Generated from Map.wz life nodes by the real provider API. */
public final class NpcLocationIndex implements IntFunction<Integer> {
    public static final String DEFAULT_RESOURCE = "/agents/catalogs/economy/npc-locations.json";
    private final String revision;
    private final Map<Integer, List<Integer>> locations;

    public NpcLocationIndex(String revision, Map<Integer, List<Integer>> locations) {
        if (revision == null || revision.length() != 64 || locations == null || locations.isEmpty())
            throw new IllegalArgumentException("versioned NPC locations are required");
        this.revision = revision;
        this.locations = Map.copyOf(locations);
    }

    public static NpcLocationIndex loadDefault() {
        try (InputStream input = NpcLocationIndex.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (input == null) throw new EconomyConfigException("Missing NPC location catalog");
            Document document = new ObjectMapper().readValue(input, Document.class);
            if (document.schemaVersion != 1 || document.entries == null || document.entries.isEmpty())
                throw new EconomyConfigException("NPC location catalog is empty or unsupported");
            Map<Integer, List<Integer>> locations = document.entries.stream().collect(Collectors.toMap(
                    entry -> entry.npcId, entry -> List.copyOf(entry.mapIds)));
            return new NpcLocationIndex(document.revision, locations);
        } catch (IOException failure) {
            throw new EconomyConfigException("Could not read NPC location catalog", failure);
        }
    }

    public OptionalInt primaryMap(int npcId) {
        List<Integer> maps = locations.get(npcId);
        return maps == null || maps.isEmpty() ? OptionalInt.empty() : OptionalInt.of(maps.getFirst());
    }

    public List<Integer> maps(int npcId) { return locations.getOrDefault(npcId, List.of()); }
    public String revision() { return revision; }

    @Override
    public Integer apply(int npcId) { return primaryMap(npcId).isPresent() ? primaryMap(npcId).getAsInt() : null; }

    public static final class Document {
        public int schemaVersion;
        public String revision;
        public List<Entry> entries;
    }
    public static final class Entry {
        public int npcId;
        public List<Integer> mapIds;
    }
}
