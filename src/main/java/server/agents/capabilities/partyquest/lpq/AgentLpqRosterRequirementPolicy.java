package server.agents.capabilities.partyquest.lpq;

import client.Character;
import client.Job;
import constants.skills.Archer;
import constants.skills.BlazeWizard;
import constants.skills.Cleric;
import constants.skills.Evan;
import constants.skills.FPWizard;
import constants.skills.ILWizard;
import constants.skills.Magician;
import constants.skills.NightWalker;
import constants.skills.Pirate;
import constants.skills.Rogue;
import constants.skills.Warrior;

import java.util.ArrayList;
import java.util.List;

/** Validates live LPQ action coverage from learned skills rather than job labels alone. */
public final class AgentLpqRosterRequirementPolicy {
    private static final int[] TELEPORT_SKILLS = {
            FPWizard.TELEPORT, ILWizard.TELEPORT, Cleric.TELEPORT,
            BlazeWizard.TELEPORT, Evan.TELEPORT
    };
    private static final int[] MAGIC_ATTACK_SKILLS = {
            Magician.ENERGY_BOLT, Magician.MAGIC_CLAW,
            2_101_004, 2_101_005, 2_201_004, 2_201_005, 2_301_004
    };
    private static final int[] RANGED_ATTACK_SKILLS = {
            Archer.ARROW_BLOW, Archer.DOUBLE_SHOT, Rogue.LUCKY_SEVEN,
            Pirate.DOUBLE_SHOT, Magician.ENERGY_BOLT, Magician.MAGIC_CLAW
    };
    private static final int[] PHYSICAL_ATTACK_SKILLS = {
            Warrior.POWER_STRIKE, Warrior.SLASH_BLAST,
            Archer.ARROW_BLOW, Archer.DOUBLE_SHOT,
            Rogue.DOUBLE_STAB, Rogue.LUCKY_SEVEN,
            Pirate.FLASH_FIST, Pirate.SOMERSAULT_KICK, Pirate.DOUBLE_SHOT
    };

    private AgentLpqRosterRequirementPolicy() {
    }

    public static boolean teleportMagic(Character character) {
        return hasAny(character, TELEPORT_SKILLS) && hasAny(character, MAGIC_ATTACK_SKILLS);
    }

    public static boolean magicAttack(Character character) {
        return hasAny(character, MAGIC_ATTACK_SKILLS);
    }

    public static boolean darkSight(Character character) {
        return hasAny(character, Rogue.DARK_SIGHT, NightWalker.DARK_SIGHT);
    }

    public static boolean rangedAttack(Character character) {
        return hasAny(character, RANGED_ATTACK_SKILLS);
    }

    public static boolean physicalAttack(Character character) {
        return hasAny(character, PHYSICAL_ATTACK_SKILLS);
    }

    /** Prefer the party's spear warrior for Stage 4's two-monster physical room. */
    public static int stageFourTwoMonsterRoomPriority(Character character) {
        if (character == null || character.getJob() == null) return 3;
        Job job = character.getJob();
        if (job.isA(Job.SPEARMAN)) return 0;
        if (job.isA(Job.WARRIOR)) return 1;
        return physicalAttack(character) ? 2 : 3;
    }

    /** Weapon-ranged branches that can close-wack the Stage 7 trigger boxes. */
    public static boolean stageSevenBoxWacker(Character character) {
        if (character == null || character.getJob() == null) return false;
        Job job = character.getJob();
        return job.isA(Job.BOWMAN) || job.isA(Job.ASSASSIN) || job.isA(Job.GUNSLINGER);
    }

    public static Coverage evaluate(List<Character> members) {
        List<Character> roster = members == null ? List.of() : members.stream()
                .filter(java.util.Objects::nonNull).toList();
        List<String> missing = new ArrayList<>();
        if (roster.stream().noneMatch(AgentLpqRosterRequirementPolicy::teleportMagic)) {
            missing.add("Teleport and magic damage");
        }
        if (roster.stream().noneMatch(AgentLpqRosterRequirementPolicy::darkSight)) {
            missing.add("Dark Sight");
        }
        if (roster.stream().noneMatch(AgentLpqRosterRequirementPolicy::rangedAttack)) {
            missing.add("ranged attack");
        }
        if (roster.stream().noneMatch(AgentLpqRosterRequirementPolicy::physicalAttack)) {
            missing.add("physical attack");
        }
        return new Coverage(missing.isEmpty(), List.copyOf(missing));
    }

    private static boolean hasAny(Character character, int... skillIds) {
        if (character == null) return false;
        for (int skillId : skillIds) {
            if (character.getSkillLevel(skillId) > 0) return true;
        }
        return false;
    }

    public record Coverage(boolean complete, List<String> missingRequirements) {
        public Coverage {
            missingRequirements = List.copyOf(missingRequirements == null
                    ? List.of() : missingRequirements);
        }
    }
}
