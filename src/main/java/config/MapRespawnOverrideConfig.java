package config;

import java.util.ArrayList;
import java.util.List;

/** Per-world map overrides for WZ-defined mob and reactor respawn delays. */
public class MapRespawnOverrideConfig {
    public List<Integer> map_ids = new ArrayList<>();
    public Integer map_id_min;
    public Integer map_id_max;
    public Integer mob_respawn_seconds;
    public Integer reactor_respawn_seconds;

    public boolean matches(int mapId) {
        boolean hasExplicitIds = map_ids != null && !map_ids.isEmpty();
        boolean hasRange = map_id_min != null && map_id_max != null;
        if (!hasExplicitIds && !hasRange) {
            return true;
        }
        return (hasExplicitIds && map_ids.contains(mapId))
                || (hasRange && mapId >= map_id_min && mapId <= map_id_max);
    }

    public static int resolveMobRespawnSeconds(List<MapRespawnOverrideConfig> overrides,
                                               int mapId,
                                               int defaultSeconds) {
        if (defaultSeconds < 0) {
            return defaultSeconds;
        }
        int resolved = defaultSeconds;
        if (overrides != null) {
            for (MapRespawnOverrideConfig override : overrides) {
                if (override != null && override.matches(mapId)
                        && override.mob_respawn_seconds != null
                        && override.mob_respawn_seconds > 0) {
                    resolved = override.mob_respawn_seconds;
                }
            }
        }
        return resolved;
    }

    public static int resolveReactorRespawnSeconds(List<MapRespawnOverrideConfig> overrides,
                                                   int mapId,
                                                   int defaultSeconds) {
        int resolved = defaultSeconds;
        if (overrides != null) {
            for (MapRespawnOverrideConfig override : overrides) {
                if (override != null && override.matches(mapId)
                        && override.reactor_respawn_seconds != null
                        && override.reactor_respawn_seconds > 0) {
                    resolved = override.reactor_respawn_seconds;
                }
            }
        }
        return resolved;
    }
}
