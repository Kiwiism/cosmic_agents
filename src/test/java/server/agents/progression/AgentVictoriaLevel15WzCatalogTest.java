package server.agents.progression;

import org.junit.jupiter.api.Test;
import constants.game.ExpTable;
import provider.Data;
import provider.DataProvider;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentVictoriaLevel15WzCatalogTest {
    private static final DataProvider MAPS = DataProviderFactory.getDataProvider(WZFiles.MAP);
    private static final DataProvider QUESTS = DataProviderFactory.getDataProvider(WZFiles.QUEST);

    @Test
    void mapleIslandHandoffMatchesQuestAndMapSourceData() {
        AgentVictoriaLevel15CatalogRepository repository =
                AgentVictoriaLevel15CatalogRepository.defaultRepository();
        AgentVictoriaLevel15Catalog.IslandHandoff handoff = repository.catalog().islandHandoff();
        Data check = QUESTS.getData("Check.img");
        Data act = QUESTS.getData("Act.img");

        assertTrue(lifeIds(handoff.lithHarborMapId(), "n").contains(handoff.olafNpcId()));
        assertTrue(lifeIds(handoff.grindMapId(), "m").containsAll(handoff.grindMobIds()));
        assertEquals(handoff.biggsNpcId(), DataTool.getInt(
                handoff.biggsQuestId() + "/0/npc", check, -1));
        assertEquals(handoff.olafNpcId(), DataTool.getInt(
                handoff.biggsQuestId() + "/1/npc", check, -1));
        assertEquals(handoff.biggsRewardExp(), DataTool.getInt(
                handoff.biggsQuestId() + "/1/exp", act, -1));
        assertEquals(handoff.olafNpcId(), DataTool.getInt(
                handoff.olafLessonQuestId() + "/0/npc", check, -1));
        assertEquals(handoff.olafNpcId(), DataTool.getInt(
                handoff.olafLessonQuestId() + "/1/npc", check, -1));
        assertEquals(handoff.olafLessonRewardExp(), DataTool.getInt(
                handoff.olafLessonQuestId() + "/1/exp", act, -1));

        for (AgentVictoriaLevel15Catalog.Career career : repository.catalog().careers()) {
            assertEquals(handoff.olafNpcId(), DataTool.getInt(
                    career.olafPathQuestId() + "/0/npc", check, -1));
            assertEquals(career.instructorNpcId(), DataTool.getInt(
                    career.olafPathQuestId() + "/1/npc", check, -1));
            assertEquals(300, DataTool.getInt(career.olafPathQuestId() + "/1/exp", act, -1));
        }

        AgentVictoriaLevel15Catalog.StartVariant questVariant = repository.startVariant("lv9-olaf");
        assertEquals(ExpTable.getExpNeededForLevel(9), questVariant.exp()
                + handoff.biggsRewardExp() + handoff.olafLessonRewardExp());
        AgentVictoriaLevel15Catalog.StartVariant grindVariant = repository.startVariant("lv9-grind");
        assertTrue(grindVariant.exp() + handoff.biggsRewardExp() + handoff.olafLessonRewardExp()
                < ExpTable.getExpNeededForLevel(9));
    }

    @Test
    void everyTrainingTargetActuallySpawnsOnItsCatalogedMap() {
        AgentVictoriaLevel15Catalog catalog =
                AgentVictoriaLevel15CatalogRepository.defaultRepository().catalog();
        for (AgentVictoriaLevel15Catalog.Career career : catalog.careers()) {
            for (AgentVictoriaLevel15Catalog.TrainingStep step : career.trainingSteps()) {
                Set<Integer> spawns = lifeIds(step.huntingMapId(), "m");
                assertTrue(spawns.containsAll(step.mobIds()),
                        () -> "map " + step.huntingMapId() + " does not spawn every mob for quest "
                                + step.questId() + "; spawns=" + spawns + " required=" + step.mobIds());
            }
            Set<Integer> grindSpawns = lifeIds(career.milestoneGrind().huntingMapId(), "m");
            assertTrue(grindSpawns.containsAll(career.milestoneGrind().mobIds()),
                    () -> "milestone map " + career.milestoneGrind().huntingMapId()
                            + " is missing " + career.milestoneGrind().mobIds());
        }
    }

    @Test
    void everyInstructorAndShopNpcExistsOnItsCatalogedMap() {
        for (AgentVictoriaLevel15Catalog.Career career
                : AgentVictoriaLevel15CatalogRepository.defaultRepository().catalog().careers()) {
            assertTrue(lifeIds(career.instructorMapId(), "n").contains(career.instructorNpcId()),
                    () -> "instructor " + career.instructorNpcId() + " is missing from "
                            + career.instructorMapId());
            assertTrue(lifeIds(career.shopMapId(), "n").contains(career.shopNpcId()),
                    () -> "shop NPC " + career.shopNpcId() + " is missing from " + career.shopMapId());
        }
    }

    @Test
    void everyCatalogCorridorCanBeTraversedInBothDirections() {
        AgentVictoriaLevel15Catalog catalog =
                AgentVictoriaLevel15CatalogRepository.defaultRepository().catalog();
        for (AgentVictoriaLevel15Catalog.RouteCorridor corridor : catalog.routeCorridors()) {
            for (int i = 1; i < corridor.mapIds().size(); i++) {
                int left = corridor.mapIds().get(i - 1);
                int right = corridor.mapIds().get(i);
                assertTrue(hasPortal(catalog, left, right),
                        () -> corridor.corridorId() + " has no " + left + " -> " + right + " portal");
                assertTrue(hasPortal(catalog, right, left),
                        () -> corridor.corridorId() + " has no " + right + " -> " + left + " portal");
            }
        }
    }

    private static Set<Integer> lifeIds(int mapId, String type) {
        Data lifeData = map(mapId).getChildByPath("life");
        Set<Integer> ids = new HashSet<>();
        if (lifeData == null) {
            return ids;
        }
        for (Data life : lifeData) {
            if (type.equals(DataTool.getString("type", life, ""))) {
                ids.add(Integer.parseInt(DataTool.getString("id", life, "0")));
            }
        }
        return ids;
    }

    private static boolean hasPortal(AgentVictoriaLevel15Catalog catalog, int sourceMapId, int destinationMapId) {
        Data portals = map(sourceMapId).getChildByPath("portal");
        if (portals != null) {
            for (Data portal : portals) {
                if (DataTool.getInt("tm", portal, 999_999_999) == destinationMapId) {
                    return true;
                }
            }
        }
        return catalog.scriptedPortals().stream().anyMatch(portal ->
                portal.sourceMapId() == sourceMapId && portal.destinationMapId() == destinationMapId);
    }

    private static Data map(int mapId) {
        String path = "Map/Map" + (mapId / 100_000_000) + "/" + String.format("%09d", mapId) + ".img";
        Data data = MAPS.getData(path);
        assertNotNull(data, "missing cataloged WZ map " + path);
        return data;
    }
}
