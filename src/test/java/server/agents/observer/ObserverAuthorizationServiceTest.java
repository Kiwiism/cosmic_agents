package server.agents.observer;

import client.Character;
import client.Client;
import config.ObserverConfig;
import config.YamlConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObserverAuthorizationServiceTest {
    private int previousMinimumGmLevel;
    private List<String> previousAllowedNames;

    @BeforeEach
    void rememberConfiguration() {
        ObserverConfig observer = YamlConfig.config.server.observer;
        previousMinimumGmLevel = observer.minimum_gm_level;
        previousAllowedNames = observer.allowed_character_names;
        observer.minimum_gm_level = 2;
        observer.allowed_character_names = List.of("WebObserver");
    }

    @AfterEach
    void restoreConfiguration() {
        ObserverConfig observer = YamlConfig.config.server.observer;
        observer.minimum_gm_level = previousMinimumGmLevel;
        observer.allowed_character_names = previousAllowedNames;
    }

    @Test
    void permitsConfiguredGmLevel() {
        assertTrue(ObserverAuthorizationService.mayUse(
                client("AnyName", 2)));
        assertFalse(ObserverAuthorizationService.mayUse(
                client("AnyName", 1)));
    }

    @Test
    void permitsConfiguredCharacterNameWithoutAgentAuthority() {
        assertTrue(ObserverAuthorizationService.mayUse(
                client("webobserver", 0)));
        assertFalse(ObserverAuthorizationService.mayUse(
                client("RegularPlayer", 0)));
    }

    private static Client client(String name, int gmLevel) {
        Client client = mock(Client.class);
        Character character = mock(Character.class);
        when(client.getPlayer()).thenReturn(character);
        when(client.getGMLevel()).thenReturn(gmLevel);
        when(character.getName()).thenReturn(name);
        return client;
    }
}
