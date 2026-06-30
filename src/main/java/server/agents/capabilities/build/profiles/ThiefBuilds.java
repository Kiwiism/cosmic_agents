package server.agents.capabilities.build.profiles;

import client.Job;
import client.Skill;
import client.SkillFactory;
import constants.skills.Assassin;
import constants.skills.Hermit;
import constants.skills.NightLord;
import constants.skills.Rogue;
import java.util.List;

public final class ThiefBuilds {

    private ThiefBuilds() {
    }

    public static List<BuildStep> getBuildOrder(Job job) {
        return switch (job) {
            case THIEF -> thiefBuild();
            case ASSASSIN -> assassinBuild();
            case HERMIT -> hermitBuild();
            case NIGHTLORD -> nightLordBuild();
            default -> null;
        };
    }

    private static BuildStep s(int id, int to) {
        return new BuildStep(id, to);
    }

    private static int max(int skillId) {
        Skill skill = SkillFactory.getSkill(skillId);
        return skill != null ? skill.getMaxLevel() : 30;
    }

    private static List<BuildStep> thiefBuild() {
        return List.of(
                s(Rogue.LUCKY_SEVEN, 1),
                s(Rogue.NIMBLE_BODY, 3),
                s(Rogue.KEEN_EYES, 8),
                s(Rogue.LUCKY_SEVEN, 20),
                s(Rogue.DISORDER, 3),
                s(Rogue.DARK_SIGHT, 20),
                s(Rogue.NIMBLE_BODY, 10)
        );
    }

    private static List<BuildStep> assassinBuild() {
        return List.of(
                s(Assassin.CLAW_MASTERY, 1),
                s(Assassin.CLAW_MASTERY, 3),
                s(Assassin.CRITICAL_THROW, 30),
                s(Assassin.CLAW_MASTERY, 5),
                s(Assassin.CLAW_BOOSTER, 2),
                s(Assassin.CLAW_BOOSTER, 6),
                s(Assassin.HASTE, 20),
                s(Assassin.CLAW_MASTERY, 20),
                s(Assassin.CLAW_BOOSTER, 20),
                s(Rogue.NIMBLE_BODY, 20),
                s(Assassin.ENDURE, 20),
                s(Assassin.DRAIN, 1)
        );
    }

    private static List<BuildStep> hermitBuild() {
        return List.of(
                s(Hermit.AVENGER, 1),
                s(Hermit.SHADOW_PARTNER, 30),
                s(Hermit.AVENGER, 5),
                s(Hermit.FLASH_JUMP, 20),
                s(Hermit.AVENGER, 30),
                s(Hermit.ALCHEMIST, 20),
                s(Hermit.SHADOW_WEB, 20),
                s(Hermit.MESO_UP, 20),
                s(Hermit.SHADOW_MESO, 1),
                s(Assassin.DRAIN, 11)
        );
    }

    private static List<BuildStep> nightLordBuild() {
        return List.of(
                s(NightLord.SHADOW_STARS, 1),
                s(NightLord.TRIPLE_THROW, 30),
                s(NightLord.MAPLE_WARRIOR, 10),
                s(NightLord.SHADOW_SHIFTER, 30),
                s(NightLord.SHADOW_STARS, 30),
                s(NightLord.HEROS_WILL, 5),
                s(NightLord.NINJA_STORM, 30),
                s(NightLord.VENOMOUS_STAR, 30),
                s(NightLord.TAUNT, 30),
                s(NightLord.MAPLE_WARRIOR, 20),
                s(NightLord.NINJA_AMBUSH, max(NightLord.NINJA_AMBUSH)),
                s(NightLord.MAPLE_WARRIOR, max(NightLord.MAPLE_WARRIOR))
        );
    }
}
