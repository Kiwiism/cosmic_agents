package client;

import constants.skills.Brawler;
import constants.skills.Magician;
import constants.skills.Warrior;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic permanent HP/MP progression.
 *
 * <p>Natural level growth and job advancement gains are kept separate from the
 * retroactive max-HP/max-MP passives. This makes a passive worth the same amount
 * whether it is learned immediately or after several eligible levels.</p>
 */
public final class HpMpGrowthPolicy {
    public static final Growth BEGINNER_LEVEL_GAIN = new Growth(14, 11);

    private HpMpGrowthPolicy() {
    }

    public record Growth(int hp, int mp) {
        public static final Growth ZERO = new Growth(0, 0);

        public Growth plus(Growth other) {
            return new Growth(hp + other.hp, mp + other.mp);
        }

        public Growth minus(Growth other) {
            return new Growth(hp - other.hp, mp - other.mp);
        }

        public Growth times(int multiplier) {
            return new Growth(hp * multiplier, mp * multiplier);
        }
    }

    public static Growth levelGain(Job job) {
        if (job == null || job == Job.BEGINNER || job == Job.NOBLESSE || job == Job.LEGEND) {
            return BEGINNER_LEVEL_GAIN;
        }
        if (job.isA(Job.GM)) {
            return new Growth(300_000, 300_000);
        }
        if (job.isA(Job.ARAN1)) {
            return new Growth(46, 6);
        }
        if (job.isA(Job.MAGICIAN) || job.isA(Job.BLAZEWIZARD1) || job == Job.EVAN || job.isA(Job.EVAN1)) {
            return new Growth(20, 55);
        }
        if (job.isA(Job.WARRIOR) || job.isA(Job.DAWNWARRIOR1)) {
            return new Growth(45, 10);
        }
        if (job.isA(Job.BOWMAN) || job.isA(Job.WINDARCHER1)) {
            return new Growth(38, 22);
        }
        if (job.isA(Job.BANDIT)) {
            return new Growth(44, 19);
        }
        if (job.isA(Job.THIEF) || job.isA(Job.NIGHTWALKER1)) {
            return new Growth(38, 22);
        }
        if (job.isA(Job.BRAWLER) || job.isA(Job.THUNDERBREAKER2)) {
            return new Growth(35, 20);
        }
        if (job.isA(Job.GUNSLINGER)) {
            return new Growth(42, 28);
        }
        if (job == Job.PIRATE || job == Job.THUNDERBREAKER1) {
            return new Growth(50, 25);
        }
        return BEGINNER_LEVEL_GAIN;
    }

    public static Growth jobAdvancementGain(Job job) {
        if (job == null) {
            return Growth.ZERO;
        }
        return switch (job) {
            case WARRIOR -> new Growth(350, 20);
            case FIGHTER, PAGE, SPEARMAN -> new Growth(600, 100);
            case CRUSADER, WHITEKNIGHT, DRAGONKNIGHT -> new Growth(1_000, 300);
            case HERO, PALADIN, DARKKNIGHT -> new Growth(1_500, 500);

            case MAGICIAN -> new Growth(100, 300);
            case FP_WIZARD, IL_WIZARD, CLERIC -> new Growth(250, 300);
            case FP_MAGE, IL_MAGE, PRIEST -> new Growth(350, 700);
            case FP_ARCHMAGE, IL_ARCHMAGE, BISHOP -> new Growth(600, 1_200);

            case BOWMAN -> new Growth(200, 75);
            case HUNTER, CROSSBOWMAN -> new Growth(300, 150);
            case RANGER, SNIPER -> new Growth(450, 300);
            case BOWMASTER, MARKSMAN -> new Growth(650, 500);

            case THIEF -> new Growth(200, 75);
            case ASSASSIN -> new Growth(300, 150);
            case HERMIT -> new Growth(450, 300);
            case NIGHTLORD -> new Growth(650, 500);
            case BANDIT -> new Growth(350, 150);
            case CHIEFBANDIT -> new Growth(500, 250);
            case SHADOWER -> new Growth(650, 450);

            case PIRATE -> new Growth(200, 75);
            case BRAWLER -> new Growth(500, 150);
            case MARAUDER -> new Growth(700, 250);
            case BUCCANEER -> new Growth(1_000, 400);
            case GUNSLINGER -> new Growth(300, 200);
            case OUTLAW -> new Growth(450, 350);
            case CORSAIR -> new Growth(650, 550);

            // Deterministic compatibility values for disabled/non-Explorer jobs.
            case DAWNWARRIOR1 -> new Growth(350, 20);
            case BLAZEWIZARD1 -> new Growth(100, 300);
            case WINDARCHER1, NIGHTWALKER1, THUNDERBREAKER1 -> new Growth(200, 75);
            default -> legacyDeterministicAdvancementGain(job);
        };
    }

    public static Growth jobAdvancementGain(Job oldJob, Job newJob, int characterLevel) {
        Growth fixedGrant = jobAdvancementGain(newJob);
        int minimumLevel = JobProgressionPolicy.minimumLevel(newJob);
        if (minimumLevel == Integer.MAX_VALUE) {
            return fixedGrant;
        }
        int delayedLevels = Math.max(0, characterLevel - minimumLevel);
        Growth timingCorrection = levelGain(newJob).minus(levelGain(oldJob)).times(delayedLevels);
        return fixedGrant.plus(timingCorrection);
    }

    /** Canonical base pools for an administrative job override at the current level. */
    public static Growth baseForJobAtLevel(Job targetJob, int characterLevel) {
        if (targetJob == null) {
            return new Growth(50, 5);
        }

        List<Job> lineage = new ArrayList<>();
        for (Job job = targetJob; job != null; job = JobProgressionPolicy.parentOf(job)) {
            lineage.add(0, job);
        }

        int reachedLevels = Math.max(0, characterLevel - 1);
        Growth total = new Growth(50, 5).plus(levelGain(lineage.getFirst()).times(reachedLevels));
        for (int i = 1; i < lineage.size(); i++) {
            Job parent = lineage.get(i - 1);
            Job job = lineage.get(i);
            total = total.plus(jobAdvancementGain(job));

            int minimumLevel = JobProgressionPolicy.minimumLevel(job);
            int levelsAfterAdvancement = minimumLevel == Integer.MAX_VALUE
                    ? 0
                    : Math.max(0, characterLevel - minimumLevel);
            total = total.plus(levelGain(job).minus(levelGain(parent)).times(levelsAfterAdvancement));
        }
        return total;
    }

    private static Growth legacyDeterministicAdvancementGain(Job job) {
        int jobId = job.getId() % 1000;
        if (jobId > 0 && jobId < 200) {
            return new Growth(325, 0);
        }
        if (jobId > 0 && jobId < 300) {
            return new Growth(0, 475);
        }
        if (jobId > 0) {
            return new Growth(325, 175);
        }
        return Growth.ZERO;
    }

    public static Growth retroactivePassiveBonus(int skillId, int effectX, int characterLevel) {
        int eligibleLevels = switch (skillId) {
            case Warrior.IMPROVED_MAXHP -> Math.max(0, characterLevel - 10);
            case Magician.IMPROVED_MAX_MP_INCREASE -> Math.max(0, characterLevel - 8);
            case Brawler.IMPROVE_MAX_HP -> Math.max(0, characterLevel - 30);
            default -> 0;
        };
        return switch (skillId) {
            case Warrior.IMPROVED_MAXHP, Brawler.IMPROVE_MAX_HP -> new Growth(effectX * eligibleLevels, 0);
            case Magician.IMPROVED_MAX_MP_INCREASE -> new Growth(0, effectX * eligibleLevels);
            default -> Growth.ZERO;
        };
    }

    public static boolean isRetroactivePassive(int skillId) {
        return skillId == Warrior.IMPROVED_MAXHP
                || skillId == Magician.IMPROVED_MAX_MP_INCREASE
                || skillId == Brawler.IMPROVE_MAX_HP;
    }

    public static boolean isHpMpApStat(int statEncoding) {
        return statEncoding == 2048 || statEncoding == 8192;
    }

    public static boolean hasPassivePrerequisite(Character character, int skillId) {
        return switch (skillId) {
            case Warrior.IMPROVED_MAXHP -> character.getSkillLevel(Warrior.IMPROVED_HPREC) >= 5;
            case Magician.IMPROVED_MAX_MP_INCREASE -> character.getSkillLevel(Magician.IMPROVED_MP_RECOVERY) >= 5;
            default -> true;
        };
    }
}
