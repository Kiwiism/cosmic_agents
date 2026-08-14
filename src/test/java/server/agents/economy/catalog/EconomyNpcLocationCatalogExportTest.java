package server.agents.economy.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataDirectoryEntry;
import provider.DataFileEntry;
import provider.DataProvider;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in deterministic exporter using the real WZ provider; not a hand-written XML parser. */
class EconomyNpcLocationCatalogExportTest {
    @Test
    void exportNpcLocations() throws Exception {
        String output = System.getProperty("economy.catalog.npc-location-output");
        assumeTrue(output != null && !output.isBlank(), "export is opt-in");
        DataProvider maps = DataProviderFactory.getDataProvider(WZFiles.MAP);
        DataDirectoryEntry mapDirectory = maps.getRoot().getSubdirectories().stream()
                .filter(entry -> "Map".equals(entry.getName())).findFirst().orElseThrow();
        Map<Integer, SortedSet<Integer>> locations = new TreeMap<>();
        MessageDigest revision = MessageDigest.getInstance("SHA-256");
        Path mapRoot = WZFiles.MAP.getFile().resolve("Map");
        for (DataDirectoryEntry group : mapDirectory.getSubdirectories()) {
            List<DataFileEntry> files = new ArrayList<>(group.getFiles());
            files.sort(Comparator.comparing(DataFileEntry::getName));
            for (DataFileEntry file : files) {
                int mapId = Integer.parseInt(file.getName().replace(".img", ""));
                Path xml = mapRoot.resolve(group.getName()).resolve(file.getName() + ".xml");
                revision.update(Files.readAllBytes(xml));
                Data map = maps.getData("Map/" + group.getName() + "/" + file.getName());
                Data life = map == null ? null : map.getChildByPath("life");
                if (life == null) continue;
                for (Data spawn : life) {
                    if (!"n".equals(DataTool.getString("type", spawn, ""))) continue;
                    int npcId = DataTool.getIntConvert("id", spawn, 0);
                    if (npcId > 0) locations.computeIfAbsent(npcId, ignored -> new TreeSet<>()).add(mapId);
                }
            }
        }
        List<Entry> entries = locations.entrySet().stream()
                .map(entry -> new Entry(entry.getKey(), List.copyOf(entry.getValue()))).toList();
        Catalog export = new Catalog(1, HexFormat.of().formatHex(revision.digest()), entries);
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
                .writeValue(Path.of(output).toFile(), export);
    }

    record Catalog(int schemaVersion, String revision, List<Entry> entries) { }
    record Entry(int npcId, List<Integer> mapIds) { }
}
