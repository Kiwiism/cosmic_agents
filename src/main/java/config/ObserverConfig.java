package config;

import java.util.List;

public class ObserverConfig {
    public boolean enabled;
    public boolean navgraph_enabled;
    public boolean agent_signals_enabled;
    public int minimum_gm_level = 2;
    public List<String> allowed_account_names = List.of();
    public List<String> allowed_character_names = List.of();
}
