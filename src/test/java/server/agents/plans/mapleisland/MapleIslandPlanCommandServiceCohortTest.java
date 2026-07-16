package server.agents.plans.mapleisland;

import org.junit.jupiter.api.Test;
import server.agents.plans.mapleisland.cohort.MapleIslandCohortRealismMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MapleIslandPlanCommandServiceCohortTest {
    @Test
    void parsesDefaultFullRun() {
        var parsed = MapleIslandPlanCommandService.parseCohortRunArguments(
                new String[]{"run", "100", "5", "10"});

        assertEquals(100, parsed.total());
        assertEquals(5, parsed.batch());
        assertEquals(10, parsed.intervalSeconds());
        assertNull(parsed.seed());
        assertEquals(MapleIslandCohortRealismMode.FULL, parsed.realismMode());
    }

    @Test
    void acceptsModeWithoutSeedAndSeedWithMode() {
        var light = MapleIslandPlanCommandService.parseCohortRunArguments(
                new String[]{"run", "25", "5", "10", "light"});
        var replay = MapleIslandPlanCommandService.parseCohortRunArguments(
                new String[]{"run", "25", "5", "10", "-42", "off"});

        assertNull(light.seed());
        assertEquals(MapleIslandCohortRealismMode.LIGHT, light.realismMode());
        assertEquals(-42L, replay.seed());
        assertEquals(MapleIslandCohortRealismMode.OFF, replay.realismMode());
    }

    @Test
    void rejectsMalformedOrUnknownOptionalValues() {
        assertThrows(IllegalArgumentException.class, () ->
                MapleIslandPlanCommandService.parseCohortRunArguments(
                        new String[]{"run", "25", "5"}));
        assertThrows(IllegalArgumentException.class, () ->
                MapleIslandPlanCommandService.parseCohortRunArguments(
                        new String[]{"run", "25", "5", "10", "chaos"}));
        assertThrows(IllegalArgumentException.class, () ->
                MapleIslandPlanCommandService.parseCohortRunArguments(
                        new String[]{"run", "25", "5", "10", "seed", "full"}));
    }
}
