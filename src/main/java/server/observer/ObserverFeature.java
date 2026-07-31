package server.observer;

import config.ObserverConfig;
import config.ServerConfig;
import config.YamlConfig;

public final class ObserverFeature {
    private ObserverFeature() {
    }

    public static boolean enabled() {
        ObserverConfig observer = config();
        return observer != null && observer.enabled;
    }

    public static boolean navGraphEnabled() {
        ObserverConfig observer = config();
        return enabled() && observer.navgraph_enabled;
    }

    public static boolean agentSignalsEnabled() {
        ObserverConfig observer = config();
        return enabled() && observer.agent_signals_enabled;
    }

    private static ObserverConfig config() {
        ServerConfig server = YamlConfig.config.server;
        return server == null ? null : server.observer;
    }
}
