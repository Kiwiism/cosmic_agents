package server.agents.auth;

import client.Character;
import config.YamlConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentAuthorityServiceTest {
    @Test
    void rolesAreCaseInsensitiveAndHierarchical() {
        String oldAdmins = config.AgentYamlConfig.config.agent.AGENT_AUTHORITY_ADMINISTRATOR_NAMES;
        String oldOperators = config.AgentYamlConfig.config.agent.AGENT_AUTHORITY_OPERATOR_NAMES;
        try {
            config.AgentYamlConfig.config.agent.AGENT_AUTHORITY_ADMINISTRATOR_NAMES = "  Kiwi ";
            config.AgentYamlConfig.config.agent.AGENT_AUTHORITY_OPERATOR_NAMES = "Operator";
            Character kiwi = named("kIwI");
            Character operator = named("operator");
            Character stranger = named("stranger");

            assertTrue(AgentAuthorityService.mayAdminister(kiwi));
            assertTrue(AgentAuthorityService.mayOperate(kiwi));
            assertTrue(AgentAuthorityService.mayOperate(operator));
            assertFalse(AgentAuthorityService.mayAdminister(operator));
            assertFalse(AgentAuthorityService.mayObserve(stranger));
        } finally {
            config.AgentYamlConfig.config.agent.AGENT_AUTHORITY_ADMINISTRATOR_NAMES = oldAdmins;
            config.AgentYamlConfig.config.agent.AGENT_AUTHORITY_OPERATOR_NAMES = oldOperators;
        }
    }

    private static Character named(String name) {
        Character character = mock(Character.class);
        when(character.getName()).thenReturn(name);
        return character;
    }
}
