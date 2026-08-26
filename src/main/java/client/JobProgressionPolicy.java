package client;

import constants.game.GameConstants;

/** Legal, one-way job advancement paths and their minimum levels. */
public final class JobProgressionPolicy {
    private JobProgressionPolicy() {
    }

    public static boolean isLegalAdvancement(Job oldJob, Job newJob, int level) {
        return oldJob != null
                && newJob != null
                && isExplorerJob(newJob)
                && oldJob != newJob
                && parentOf(newJob) == oldJob
                && level >= minimumLevel(newJob);
    }

    private static boolean isExplorerJob(Job job) {
        return job.getId() >= 0 && job.getId() < 1_000;
    }

    public static int classLevelCap(Job job, boolean gameMaster) {
        if (gameMaster) {
            return 255;
        }
        return job != null && GameConstants.isCygnus(job.getId()) ? 120 : 200;
    }

    public static int minimumLevel(Job job) {
        return switch (job) {
            case MAGICIAN -> 8;
            case WARRIOR, BOWMAN, THIEF, PIRATE,
                    DAWNWARRIOR1, BLAZEWIZARD1, WINDARCHER1, NIGHTWALKER1, THUNDERBREAKER1,
                    ARAN1, EVAN1 -> 10;
            case EVAN2 -> 20;
            case FIGHTER, PAGE, SPEARMAN, FP_WIZARD, IL_WIZARD, CLERIC,
                    HUNTER, CROSSBOWMAN, ASSASSIN, BANDIT, BRAWLER, GUNSLINGER,
                    DAWNWARRIOR2, BLAZEWIZARD2, WINDARCHER2, NIGHTWALKER2, THUNDERBREAKER2,
                    ARAN2, EVAN3 -> 30;
            case EVAN4 -> 40;
            case EVAN5 -> 50;
            case EVAN6 -> 60;
            case CRUSADER, WHITEKNIGHT, DRAGONKNIGHT, FP_MAGE, IL_MAGE, PRIEST,
                    RANGER, SNIPER, HERMIT, CHIEFBANDIT, MARAUDER, OUTLAW,
                    DAWNWARRIOR3, BLAZEWIZARD3, WINDARCHER3, NIGHTWALKER3, THUNDERBREAKER3,
                    ARAN3 -> 70;
            case EVAN7 -> 80;
            case EVAN8 -> 100;
            case HERO, PALADIN, DARKKNIGHT, FP_ARCHMAGE, IL_ARCHMAGE, BISHOP,
                    BOWMASTER, MARKSMAN, NIGHTLORD, SHADOWER, BUCCANEER, CORSAIR,
                    DAWNWARRIOR4, BLAZEWIZARD4, WINDARCHER4, NIGHTWALKER4, THUNDERBREAKER4,
                    ARAN4, EVAN9 -> 120;
            case EVAN10 -> 160;
            default -> Integer.MAX_VALUE;
        };
    }

    static Job parentOf(Job job) {
        return switch (job) {
            case WARRIOR, MAGICIAN, BOWMAN, THIEF, PIRATE -> Job.BEGINNER;
            case FIGHTER, PAGE, SPEARMAN -> Job.WARRIOR;
            case CRUSADER -> Job.FIGHTER;
            case HERO -> Job.CRUSADER;
            case WHITEKNIGHT -> Job.PAGE;
            case PALADIN -> Job.WHITEKNIGHT;
            case DRAGONKNIGHT -> Job.SPEARMAN;
            case DARKKNIGHT -> Job.DRAGONKNIGHT;
            case FP_WIZARD, IL_WIZARD, CLERIC -> Job.MAGICIAN;
            case FP_MAGE -> Job.FP_WIZARD;
            case FP_ARCHMAGE -> Job.FP_MAGE;
            case IL_MAGE -> Job.IL_WIZARD;
            case IL_ARCHMAGE -> Job.IL_MAGE;
            case PRIEST -> Job.CLERIC;
            case BISHOP -> Job.PRIEST;
            case HUNTER, CROSSBOWMAN -> Job.BOWMAN;
            case RANGER -> Job.HUNTER;
            case BOWMASTER -> Job.RANGER;
            case SNIPER -> Job.CROSSBOWMAN;
            case MARKSMAN -> Job.SNIPER;
            case ASSASSIN, BANDIT -> Job.THIEF;
            case HERMIT -> Job.ASSASSIN;
            case NIGHTLORD -> Job.HERMIT;
            case CHIEFBANDIT -> Job.BANDIT;
            case SHADOWER -> Job.CHIEFBANDIT;
            case BRAWLER, GUNSLINGER -> Job.PIRATE;
            case MARAUDER -> Job.BRAWLER;
            case BUCCANEER -> Job.MARAUDER;
            case OUTLAW -> Job.GUNSLINGER;
            case CORSAIR -> Job.OUTLAW;

            case DAWNWARRIOR1, BLAZEWIZARD1, WINDARCHER1, NIGHTWALKER1, THUNDERBREAKER1 -> Job.NOBLESSE;
            case DAWNWARRIOR2 -> Job.DAWNWARRIOR1;
            case DAWNWARRIOR3 -> Job.DAWNWARRIOR2;
            case DAWNWARRIOR4 -> Job.DAWNWARRIOR3;
            case BLAZEWIZARD2 -> Job.BLAZEWIZARD1;
            case BLAZEWIZARD3 -> Job.BLAZEWIZARD2;
            case BLAZEWIZARD4 -> Job.BLAZEWIZARD3;
            case WINDARCHER2 -> Job.WINDARCHER1;
            case WINDARCHER3 -> Job.WINDARCHER2;
            case WINDARCHER4 -> Job.WINDARCHER3;
            case NIGHTWALKER2 -> Job.NIGHTWALKER1;
            case NIGHTWALKER3 -> Job.NIGHTWALKER2;
            case NIGHTWALKER4 -> Job.NIGHTWALKER3;
            case THUNDERBREAKER2 -> Job.THUNDERBREAKER1;
            case THUNDERBREAKER3 -> Job.THUNDERBREAKER2;
            case THUNDERBREAKER4 -> Job.THUNDERBREAKER3;

            case ARAN1 -> Job.LEGEND;
            case ARAN2 -> Job.ARAN1;
            case ARAN3 -> Job.ARAN2;
            case ARAN4 -> Job.ARAN3;
            case EVAN1 -> Job.EVAN;
            case EVAN2 -> Job.EVAN1;
            case EVAN3 -> Job.EVAN2;
            case EVAN4 -> Job.EVAN3;
            case EVAN5 -> Job.EVAN4;
            case EVAN6 -> Job.EVAN5;
            case EVAN7 -> Job.EVAN6;
            case EVAN8 -> Job.EVAN7;
            case EVAN9 -> Job.EVAN8;
            case EVAN10 -> Job.EVAN9;

            default -> null;
        };
    }
}
