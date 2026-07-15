package config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobSpawnOverrideConfigTest {
    @Test
    void matchesExplicitMapIdsOrInclusiveRange() {
        MobSpawnOverrideConfig config = new MobSpawnOverrideConfig();
        config.map_ids = List.of(100, 200);
        config.map_id_min = 300;
        config.map_id_max = 399;

        assertTrue(config.matches(100));
        assertTrue(config.matches(300));
        assertTrue(config.matches(399));
        assertFalse(config.matches(299));
        assertFalse(config.matches(400));
    }

    @Test
    void laterMatchingOverridesWinPerField() {
        MobSpawnOverrideConfig first = new MobSpawnOverrideConfig();
        first.map_ids = List.of(100);
        first.mob_rate = 2f;
        first.max_mob_per_spawnpoint = 4;

        MobSpawnOverrideConfig second = new MobSpawnOverrideConfig();
        second.map_id_min = 50;
        second.map_id_max = 150;
        second.mob_rate = 3f;

        List<MobSpawnOverrideConfig> overrides = List.of(first, second);

        assertEquals(3f, MobSpawnOverrideConfig.resolveMobRate(overrides, 100, 1f));
        assertEquals(4, MobSpawnOverrideConfig.resolveMaxMobPerSpawnpoint(overrides, 100, 1));
        assertEquals(1f, MobSpawnOverrideConfig.resolveMobRate(overrides, 999, 1f));
    }
}
