package config;

import java.util.ArrayList;
import java.util.List;

public class MobSpawnOverrideConfig {
    public List<Integer> map_ids = new ArrayList<>();
    public Integer map_id_min;
    public Integer map_id_max;
    public Float mob_rate;
    public Integer max_mob_per_spawnpoint;

    public boolean matches(int mapId) {
        if (map_ids != null && map_ids.contains(mapId)) {
            return true;
        }

        return map_id_min != null && map_id_max != null
                && mapId >= map_id_min && mapId <= map_id_max;
    }

    public static float resolveMobRate(List<MobSpawnOverrideConfig> overrides, int mapId, float defaultRate) {
        float resolved = defaultRate;
        if (overrides != null) {
            for (MobSpawnOverrideConfig override : overrides) {
                if (override != null && override.matches(mapId) && override.mob_rate != null) {
                    resolved = override.mob_rate;
                }
            }
        }
        return Math.max(resolved, 1f);
    }

    public static int resolveMaxMobPerSpawnpoint(List<MobSpawnOverrideConfig> overrides, int mapId, int defaultMaximum) {
        int resolved = defaultMaximum;
        if (overrides != null) {
            for (MobSpawnOverrideConfig override : overrides) {
                if (override != null && override.matches(mapId) && override.max_mob_per_spawnpoint != null) {
                    resolved = override.max_mob_per_spawnpoint;
                }
            }
        }
        return Math.max(resolved, 1);
    }
}
