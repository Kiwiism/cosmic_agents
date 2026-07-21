package server.maps;

import config.YamlConfig;
import constants.id.MapId;

import java.util.concurrent.TimeUnit;

/** Resolves server-wide and Maple Island spawn-delay overrides. */
public final class SpawnTimeOverridePolicy {
    private SpawnTimeOverridePolicy() {
    }

    public static int mobRespawnSeconds(int mapId, int sourceSeconds) {
        if (sourceSeconds < 0) {
            return sourceSeconds;
        }
        return overrideSeconds(
                mapId,
                sourceSeconds,
                YamlConfig.config.server.MOB_RESPAWN_TIME_OVERRIDE_SECONDS,
                YamlConfig.config.server.MAPLE_ISLAND_MOB_RESPAWN_TIME_OVERRIDE_SECONDS);
    }

    public static int reactorRespawnMilliseconds(int mapId, int sourceMilliseconds) {
        int seconds = overrideSeconds(
                mapId,
                -1,
                YamlConfig.config.server.REACTOR_RESPAWN_TIME_OVERRIDE_SECONDS,
                YamlConfig.config.server.MAPLE_ISLAND_REACTOR_RESPAWN_TIME_OVERRIDE_SECONDS);
        if (seconds <= 0) {
            return sourceMilliseconds;
        }
        return (int) Math.min(Integer.MAX_VALUE, TimeUnit.SECONDS.toMillis(seconds));
    }

    static int overrideSeconds(int mapId,
                               int sourceSeconds,
                               int genericOverrideSeconds,
                               int mapleIslandOverrideSeconds) {
        int configured = MapId.isMapleIsland(mapId)
                ? mapleIslandOverrideSeconds
                : genericOverrideSeconds;
        return configured > 0 ? configured : sourceSeconds;
    }
}
