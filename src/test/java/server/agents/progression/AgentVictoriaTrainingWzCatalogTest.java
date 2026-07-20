package server.agents.progression;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProvider;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentVictoriaTrainingWzCatalogTest {
    private static final DataProvider MAPS = DataProviderFactory.getDataProvider(WZFiles.MAP);
    private static final DataProvider MOBS = DataProviderFactory.getDataProvider(WZFiles.MOB);

    @Test
    void everyCatalogedMapContainsExactlyTheRecordedMobSpawns() {
        for (AgentVictoriaTrainingCatalog.TrainingMap map
                : AgentVictoriaTrainingCatalogRepository.defaultRepository().catalog().trainingMaps()) {
            Map<Integer, Integer> expected = new LinkedHashMap<>();
            for (AgentVictoriaTrainingCatalog.SpawnGroup spawn : map.spawns()) {
                expected.put(spawn.mobId(), spawn.expectedCount());
            }

            String providerPath = map.sourcePath()
                    .replace("wz/Map.wz/", "")
                    .replace(".img.xml", ".img");
            Data mapData = MAPS.getData(providerPath);
            assertNotNull(mapData, "missing cataloged WZ map " + map.sourcePath());
            assertEquals(expected, mobSpawnCounts(mapData),
                    () -> "WZ spawns changed for map " + map.mapId() + " (" + map.mapName() + ")");
        }
    }

    @Test
    void everyRecordedMobLevelMatchesLocalMobWz() {
        for (AgentVictoriaTrainingCatalog.TrainingMap map
                : AgentVictoriaTrainingCatalogRepository.defaultRepository().catalog().trainingMaps()) {
            for (AgentVictoriaTrainingCatalog.SpawnGroup spawn : map.spawns()) {
                String mobPath = String.format("%07d.img", spawn.mobId());
                Data mob = MOBS.getData(mobPath);
                assertNotNull(mob, "missing cataloged WZ mob " + mobPath);
                assertEquals(spawn.mobLevel(), DataTool.getInt("info/level", mob, -1),
                        () -> "WZ level changed for mob " + spawn.mobId() + " (" + spawn.mobName() + ")");
            }
        }
    }

    private static Map<Integer, Integer> mobSpawnCounts(Data map) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        Data life = map.getChildByPath("life");
        if (life == null) {
            return result;
        }
        for (Data entry : life) {
            if (!"m".equals(DataTool.getString("type", entry, ""))) {
                continue;
            }
            int mobId = Integer.parseInt(DataTool.getString("id", entry, "0"));
            result.merge(mobId, 1, Integer::sum);
        }
        return result;
    }
}
