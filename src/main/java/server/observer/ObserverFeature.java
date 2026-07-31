package server.observer;

import config.ObserverConfig;
import config.ServerConfig;
import config.YamlConfig;

public final class ObserverFeature {
    private ObserverFeature() {
    }

    public static boolean enabled() {
        ServerConfig server = YamlConfig.config.server;
        ObserverConfig observer = server == null ? null : server.observer;
        return observer != null && observer.enabled;
    }
}
