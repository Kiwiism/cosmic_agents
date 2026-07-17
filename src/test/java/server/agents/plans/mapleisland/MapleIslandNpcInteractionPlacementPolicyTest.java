package server.agents.plans.mapleisland;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.objective.MapleIslandObjectiveRandomnessRuntime;
import server.agents.capabilities.objective.MapleIslandObjectiveRandomnessSettings;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapleIslandNpcInteractionPlacementPolicyTest {
    @Test
    void edgeNpcBiasCanFavorIncomingTrafficSideInsteadOfCurrentPosition() {
        Point left = new Point(760, 100);
        Point middleLeft = new Point(840, 100);
        Point middleRight = new Point(920, 100);
        Point right = new Point(1_000, 100);

        List<Point> pool = MapleIslandNpcInteractionPlacementPolicy.weightedPool(
                List.of(left, middleLeft, middleRight, right), new Point(1_100, 100),
                50000, 2005, new Point(1_000, 100));

        assertEquals(3L, pool.stream().filter(left::equals).count());
        assertEquals(3L, pool.stream().filter(middleLeft::equals).count());
        assertEquals(1L, pool.stream().filter(middleRight::equals).count());
        assertEquals(1L, pool.stream().filter(right::equals).count());
    }

    @Test
    void trainingCenterNpcsFavorTheirRespectiveZones() {
        List<Point> candidates = List.of(
                new Point(-200, 100), new Point(-100, 100),
                new Point(0, 100), new Point(100, 100), new Point(200, 100));

        List<Point> yoona = MapleIslandNpcInteractionPlacementPolicy.weightedPool(
                candidates, new Point(200, 100), 1010000, 20100, new Point(-100, 100));
        List<Point> mai = MapleIslandNpcInteractionPlacementPolicy.weightedPool(
                candidates, new Point(200, 100), 1010000, 12100, new Point(0, 100));
        List<Point> bari = MapleIslandNpcInteractionPlacementPolicy.weightedPool(
                candidates, new Point(-200, 100), 1010000, 20001, new Point(100, 100));

        assertEquals(3L, yoona.stream().filter(new Point(-200, 100)::equals).count());
        assertEquals(3L, mai.stream().filter(new Point(0, 100)::equals).count());
        assertEquals(3L, bari.stream().filter(new Point(200, 100)::equals).count());
    }

    @Test
    void extendsOnlySamRangeForCohortAnchorVariation() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        MapleIslandObjectiveRandomnessRuntime.configure(
                entry, MapleIslandObjectiveRandomnessSettings.cohort(77L));

        assertEquals(500, MapleIslandNpcInteractionPlacementPolicy.INSTANCE.select(
                entry, null, 50000, 2005, new Point(), new Point(), 300).interactionRangePx());
        assertEquals(300, MapleIslandNpcInteractionPlacementPolicy.INSTANCE.select(
                entry, null, 50000, 2003, new Point(), new Point(), 300).interactionRangePx());

        MapleIslandObjectiveRandomnessRuntime.clear(entry);
        assertEquals(300, MapleIslandNpcInteractionPlacementPolicy.INSTANCE.select(
                entry, null, 50000, 2005, new Point(), new Point(), 300).interactionRangePx());
    }
}
