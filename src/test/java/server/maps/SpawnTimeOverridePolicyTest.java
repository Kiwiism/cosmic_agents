package server.maps;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpawnTimeOverridePolicyTest {
    @Test
    void mapleIslandUsesItsOverride() {
        assertEquals(5, SpawnTimeOverridePolicy.overrideSeconds(1_010_100, 30, 10, 5));
    }

    @Test
    void otherMapsUseGenericOverride() {
        assertEquals(10, SpawnTimeOverridePolicy.overrideSeconds(100_000_000, 30, 10, 5));
    }

    @Test
    void zeroOverridePreservesSourceDelay() {
        assertEquals(30, SpawnTimeOverridePolicy.overrideSeconds(100_000_000, 30, 0, 5));
        assertEquals(30, SpawnTimeOverridePolicy.overrideSeconds(1_010_100, 30, 10, 0));
    }

    @Test
    void oneShotMobDelayIsNeverOverridden() {
        assertEquals(-1, SpawnTimeOverridePolicy.mobRespawnSeconds(1_010_100, -1));
    }
}
