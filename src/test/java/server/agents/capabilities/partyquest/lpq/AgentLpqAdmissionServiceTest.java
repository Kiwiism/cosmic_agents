package server.agents.capabilities.partyquest.lpq;

import client.Character;
import client.Job;
import constants.skills.Archer;
import constants.skills.FPWizard;
import constants.skills.Magician;
import constants.skills.Rogue;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentLpqAdmissionServiceTest {
    @Test
    void humanLeaderCannotSupplyAnAutomationOnlyRoomCapability() {
        assertHumanCapabilityDoesNotCoverAgents(true);
    }

    @Test
    void agentLeaderCannotBorrowAnAutomationOnlyCapabilityFromTheHumanMember() {
        assertHumanCapabilityDoesNotCoverAgents(false);
    }

    private static void assertHumanCapabilityDoesNotCoverAgents(boolean humanLeads) {
        Character human = character(900, null, Rogue.DARK_SIGHT);
        Character mage = character(101, Job.FP_WIZARD,
                FPWizard.TELEPORT, Magician.MAGIC_CLAW);
        Character archer = character(102, Job.HUNTER, Archer.DOUBLE_SHOT);
        Character agent3 = character(103, Job.FIGHTER);
        Character agent4 = character(104, Job.FIGHTER);
        Character agent5 = character(105, Job.FIGHTER);
        List<Character> party = List.of(human, mage, archer, agent3, agent4, agent5);
        Set<Integer> agentIds = Set.of(101, 102, 103, 104, 105);

        try (MockedStatic<AgentRuntimeRegistry> registry = mockStatic(AgentRuntimeRegistry.class)) {
            registry.when(() -> AgentRuntimeRegistry.findByAgentCharacterId(anyInt()))
                    .thenAnswer(invocation -> agentIds.contains(invocation.getArgument(0))
                            ? mock(AgentRuntimeEntry.class) : null);

            AgentLpqAdmissionService.Validation validation = AgentLpqAdmissionService.validate(
                    human, humanLeads ? human : mage, party);

            assertFalse(validation.success());
            assertEquals("Missing Agent LPQ capability: Dark Sight", validation.message());
        }
    }

    private static Character character(int id, Job job, int... skills) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getJob()).thenReturn(job);
        for (int skill : skills) when(character.getSkillLevel(skill)).thenReturn(1);
        return character;
    }
}
