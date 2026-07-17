package server.agents.capabilities.objective;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.npc.AgentNpcInteractionPolicy;
import server.agents.catalog.AgentCatalogService;
import server.agents.catalog.CatalogRecord;

import java.awt.Point;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import server.agents.plans.mapleisland.MapleIslandNpcInteractionAnchorCatalog;

class AgentNpcInteractionAnchorCatalogTest {
    private static final List<NpcPlacement> FULL_PLAN_NPCS = List.of(
            new NpcPlacement(10000, 2101),
            new NpcPlacement(10000, 2100),
            new NpcPlacement(20000, 2000),
            new NpcPlacement(30000, 2102),
            new NpcPlacement(30001, 2001),
            new NpcPlacement(40000, 2004),
            new NpcPlacement(40000, 2002),
            new NpcPlacement(50000, 2003),
            new NpcPlacement(50000, 2005),
            new NpcPlacement(1000000, 2103),
            new NpcPlacement(1000000, 12000),
            new NpcPlacement(1000000, 12101),
            new NpcPlacement(1000000, 10000),
            new NpcPlacement(1010000, 20100),
            new NpcPlacement(1010000, 12100),
            new NpcPlacement(1010000, 20001),
            new NpcPlacement(2000000, 20002));

    @Test
    void everyNpcUsedByFullPlanHasSeveralCuratedSafeAnchors() {
        for (NpcPlacement placement : FULL_PLAN_NPCS) {
            assertTrue(MapleIslandNpcInteractionAnchorCatalog.anchors(
                            placement.mapId(), placement.npcId()).size() >= 4,
                    () -> "missing varied anchors for " + placement);
        }
    }

    @Test
    void curatedAnchorsMatchGeneratedGroundedNpcGeometryAndClickRange() {
        var npcCatalog = AgentCatalogService.loadFromRepoRoot(Path.of(".")).queries().npc();
        long clickRangeSquared = (long) AgentNpcInteractionPolicy.DEFAULT_CLICK_RANGE_PX
                * AgentNpcInteractionPolicy.DEFAULT_CLICK_RANGE_PX;

        for (NpcPlacement npc : FULL_PLAN_NPCS) {
            CatalogRecord placement = npcCatalog.placementsForNpc(npc.npcId()).stream()
                    .filter(candidate -> candidate.intValue("mapId").orElse(-1) == npc.mapId())
                    .findFirst()
                    .orElseThrow();
            int npcX = placement.intValue("x").orElseThrow();
            int npcY = placement.intValue("y").orElseThrow();
            int npcFootholdId = placement.intValue("footholdId").orElseThrow();
            List<CatalogRecord> generatedCandidates =
                    npcCatalog.approachCandidates(npc.npcId(), npc.mapId());

            for (Point anchor : MapleIslandNpcInteractionAnchorCatalog.anchors(
                    npc.mapId(), npc.npcId())) {
                assertTrue(generatedCandidates.stream().anyMatch(candidate ->
                                candidate.intValue("x").orElse(Integer.MIN_VALUE) == anchor.x
                                        && candidate.intValue("y").orElse(Integer.MIN_VALUE) == anchor.y
                                        && candidate.booleanValue("sameFootholdAsNpc").orElse(false)
                                        && candidate.intValue("footholdId").orElse(-1) == npcFootholdId),
                        () -> "anchor is not a generated same-foothold point: " + npc + " " + anchor);
                assertTrue(anchor.distanceSq(npcX, npcY) <= clickRangeSquared,
                        () -> "anchor is outside NPC click range: " + npc + " " + anchor);
            }
        }
    }

    @Test
    void callersCannotMutateCatalogPoints() {
        List<Point> first = MapleIslandNpcInteractionAnchorCatalog.anchors(10000, 2101);
        Point original = new Point(first.getFirst());
        first.getFirst().translate(999, 999);

        assertEquals(original,
                MapleIslandNpcInteractionAnchorCatalog.anchors(10000, 2101).getFirst());
    }

    @Test
    void disabledVariationRetainsLegacyYoonaNearestBehaviorOnly() {
        assertEquals(new Point(-210, 95),
                MapleIslandNpcInteractionAnchorCatalog.nearestLegacy(
                        1010000, 20100, new Point(-250, 95)));
        assertEquals(null,
                MapleIslandNpcInteractionAnchorCatalog.nearestLegacy(
                        10000, 2101, new Point(0, 305)));
    }

    private record NpcPlacement(int mapId, int npcId) {
    }
}
