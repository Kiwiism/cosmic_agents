package server.agents.capabilities.partyquest.lpq;

import client.Character;
import constants.skills.Archer;
import constants.skills.FPWizard;
import constants.skills.Magician;
import constants.skills.Rogue;
import constants.skills.Warrior;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLpqRosterRequirementPolicyTest {
    @Test
    void oneCharacterMayCoverTeleportMagicAndRanged() {
        Character mage = character(FPWizard.TELEPORT, Magician.MAGIC_CLAW);
        Character thief = character(Rogue.DARK_SIGHT, Rogue.LUCKY_SEVEN);
        Character warrior = character(Warrior.POWER_STRIKE);

        AgentLpqRosterRequirementPolicy.Coverage coverage =
                AgentLpqRosterRequirementPolicy.evaluate(List.of(mage, thief, warrior));

        assertTrue(coverage.complete());
        assertTrue(coverage.missingRequirements().isEmpty());
    }

    @Test
    void learnedTeleportWithoutMagicDamageDoesNotSatisfyRoomCoverage() {
        Character utilityOnly = character(FPWizard.TELEPORT);
        Character archer = character(Archer.DOUBLE_SHOT);

        AgentLpqRosterRequirementPolicy.Coverage coverage =
                AgentLpqRosterRequirementPolicy.evaluate(List.of(utilityOnly, archer));

        assertFalse(coverage.complete());
        assertEquals(List.of("Teleport and magic damage", "Dark Sight"),
                coverage.missingRequirements());
    }

    private static Character character(int... skillIds) {
        Character character = mock(Character.class);
        for (int skillId : skillIds) when(character.getSkillLevel(skillId)).thenReturn(1);
        return character;
    }
}
