package server.agents.capabilities.partyquest.lpq;

import client.Character;
import client.Job;
import constants.skills.Archer;
import constants.skills.FPWizard;
import constants.skills.Magician;
import constants.skills.Rogue;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLpqAdmissionServiceTest {
    @Test
    void capableHumanSuppliesDarkSightOnlyWhenExplicitlyAssigned() {
        Character human = character(900, null, Rogue.DARK_SIGHT);
        Character mage = character(101, Job.FP_WIZARD,
                FPWizard.TELEPORT, Magician.MAGIC_CLAW);
        Character archer = character(102, Job.HUNTER, Archer.DOUBLE_SHOT);
        Character agent3 = character(103, Job.FIGHTER);
        Character agent4 = character(104, Job.FIGHTER);
        Character agent5 = character(105, Job.FIGHTER);
        List<Character> party = List.of(human, mage, archer, agent3, agent4, agent5);

        List<Character> agents = party.subList(1, party.size());
        assertFalse(AgentLpqAdmissionService.capabilityCoverage(party, agents, human.getId(),
                AgentLpqSession.HumanRolePreference.DEFAULT).complete());
        assertTrue(AgentLpqAdmissionService.capabilityCoverage(party, agents, human.getId(),
                AgentLpqSession.HumanRolePreference.DARK_SIGHT).complete());
    }

    private static Character character(int id, Job job, int... skills) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getJob()).thenReturn(job);
        for (int skill : skills) when(character.getSkillLevel(skill)).thenReturn(1);
        return character;
    }
}
