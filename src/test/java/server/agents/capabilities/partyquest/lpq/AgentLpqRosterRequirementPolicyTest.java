package server.agents.capabilities.partyquest.lpq;

import client.Character;
import client.Job;
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

    @Test
    void stageSevenBoxWackersAreWeaponRangedBranchesRatherThanMages() {
        assertTrue(AgentLpqRosterRequirementPolicy.stageSevenBoxWacker(character(Job.HUNTER)));
        assertTrue(AgentLpqRosterRequirementPolicy.stageSevenBoxWacker(character(Job.CROSSBOWMAN)));
        assertTrue(AgentLpqRosterRequirementPolicy.stageSevenBoxWacker(character(Job.ASSASSIN)));
        assertTrue(AgentLpqRosterRequirementPolicy.stageSevenBoxWacker(character(Job.GUNSLINGER)));
        assertFalse(AgentLpqRosterRequirementPolicy.stageSevenBoxWacker(character(Job.IL_WIZARD)));
        assertFalse(AgentLpqRosterRequirementPolicy.stageSevenBoxWacker(character(Job.BANDIT)));
    }

    @Test
    void stageFourMagicCoverageDoesNotRequireTeleport() {
        Character magicOnly = character(Magician.MAGIC_CLAW);

        assertTrue(AgentLpqRosterRequirementPolicy.magicAttack(magicOnly));
        assertFalse(AgentLpqRosterRequirementPolicy.teleportMagic(magicOnly));
    }

    @Test
    void spearmanHasFirstClaimOnTheTwoMonsterPhysicalRoom() {
        assertEquals(0, AgentLpqRosterRequirementPolicy.stageFourTwoMonsterRoomPriority(
                character(Job.SPEARMAN, Warrior.POWER_STRIKE)));
        assertEquals(1, AgentLpqRosterRequirementPolicy.stageFourTwoMonsterRoomPriority(
                character(Job.FIGHTER, Warrior.POWER_STRIKE)));
        assertEquals(2, AgentLpqRosterRequirementPolicy.stageFourTwoMonsterRoomPriority(
                character(Job.ASSASSIN, Rogue.LUCKY_SEVEN)));
    }

    private static Character character(int... skillIds) {
        Character character = mock(Character.class);
        for (int skillId : skillIds) when(character.getSkillLevel(skillId)).thenReturn(1);
        return character;
    }

    private static Character character(Job job) {
        Character character = mock(Character.class);
        when(character.getJob()).thenReturn(job);
        return character;
    }

    private static Character character(Job job, int... skillIds) {
        Character character = character(job);
        for (int skillId : skillIds) when(character.getSkillLevel(skillId)).thenReturn(1);
        return character;
    }
}
