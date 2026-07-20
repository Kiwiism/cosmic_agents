package config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapRespawnOverrideConfigTest {
    @Test
    void selectorlessOverrideMatchesEveryMap() {
        MapRespawnOverrideConfig override = new MapRespawnOverrideConfig();

        assertTrue(override.matches(1000000));
        assertTrue(override.matches(910000000));
    }

    @Test
    void explicitMapAndRangeSelectorsOnlyMatchTheirMaps() {
        MapRespawnOverrideConfig override = new MapRespawnOverrideConfig();
        override.map_ids = List.of(1000000);
        override.map_id_min = 2000000;
        override.map_id_max = 2000010;

        assertTrue(override.matches(1000000));
        assertTrue(override.matches(2000005));
        assertFalse(override.matches(3000000));
    }

    @Test
    void laterMatchingEntriesWinIndependentlyPerRespawnType() {
        MapRespawnOverrideConfig worldDefault = new MapRespawnOverrideConfig();
        worldDefault.mob_respawn_seconds = 5;

        MapRespawnOverrideConfig amherst = new MapRespawnOverrideConfig();
        amherst.map_ids = List.of(1000000);
        amherst.reactor_respawn_seconds = 5;

        MapRespawnOverrideConfig amherstMob = new MapRespawnOverrideConfig();
        amherstMob.map_ids = List.of(1000000);
        amherstMob.mob_respawn_seconds = 7;

        List<MapRespawnOverrideConfig> overrides = List.of(worldDefault, amherst, amherstMob);

        assertEquals(7, MapRespawnOverrideConfig.resolveMobRespawnSeconds(overrides, 1000000, 90));
        assertEquals(5, MapRespawnOverrideConfig.resolveMobRespawnSeconds(overrides, 1010000, 90));
        assertEquals(5, MapRespawnOverrideConfig.resolveReactorRespawnSeconds(overrides, 1000000, 180));
        assertEquals(180, MapRespawnOverrideConfig.resolveReactorRespawnSeconds(overrides, 1010000, 180));
    }

    @Test
    void nonRespawningMobRemainsNonRespawning() {
        MapRespawnOverrideConfig override = new MapRespawnOverrideConfig();
        override.mob_respawn_seconds = 5;

        assertEquals(-1, MapRespawnOverrideConfig.resolveMobRespawnSeconds(
                List.of(override), 1000000, -1));
    }

    @Test
    void nonPositiveValuesDoNotReplaceWzDelays() {
        MapRespawnOverrideConfig override = new MapRespawnOverrideConfig();
        override.mob_respawn_seconds = 0;
        override.reactor_respawn_seconds = -1;

        assertEquals(30, MapRespawnOverrideConfig.resolveMobRespawnSeconds(
                List.of(override), 1000000, 30));
        assertEquals(90, MapRespawnOverrideConfig.resolveReactorRespawnSeconds(
                List.of(override), 1000000, 90));
    }
}
