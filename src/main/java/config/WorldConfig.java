package config;

import java.util.ArrayList;
import java.util.List;

public class WorldConfig {
    public int flag = 0;
    public String server_message = "Welcome!";
    public String event_message = "";
    public String why_am_i_recommended = "";
    public int channels = 1;
    public int exp_rate = 1;
    public float mob_rate = 1;
    public int max_mob_per_spawnpoint = 1;
    public List<MobSpawnOverrideConfig> mob_spawn_overrides = new ArrayList<>();
    public int meso_rate = 1;
    public int drop_rate = 1;
    public int boss_drop_rate = 1;
    public int maker_rate = 1;
    public int quest_rate = 1;
    public int travel_rate = 1;
    public int fishing_rate = 1;
    public boolean allow_all_untradeable_items = false;
    public List<Integer> untradeable_item_allowlist = new ArrayList<>();
    public boolean allow_multiple_one_of_a_kind_items = false;
    public List<Integer> multiple_one_of_a_kind_item_allowlist = new ArrayList<>();
}
