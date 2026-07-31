package server.agents.observer;

import client.Character;
import client.Client;
import config.ObserverConfig;
import config.YamlConfig;

import java.util.Locale;

public final class ObserverAuthorizationService {
    private ObserverAuthorizationService() {
    }

    public static boolean mayUse(Client client) {
        Character character = client == null ? null : client.getPlayer();
        ObserverConfig observer = YamlConfig.config.server.observer;
        if (character == null || observer == null) {
            return false;
        }
        if (client.getGMLevel() >= Math.max(0, observer.minimum_gm_level)) {
            return true;
        }
        String name = normalized(character.getName());
        return observer.allowed_character_names != null
                && observer.allowed_character_names.stream()
                .map(ObserverAuthorizationService::normalized)
                .anyMatch(name::equals);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
