package server.agents.capabilities.dialogue;

import client.Job;

public final class AgentBuildPromptReporter {
    private AgentBuildPromptReporter() {
    }

    public static String apPromptForJob(Job job) {
        if (job == null) {
            return null;
        }
        if (job.isA(Job.WARRIOR)) {
            return "what AP build? type 'dexless'/'pure' or e.g. '25 dex' to set a dex target";
        }
        if (job.isA(Job.MAGICIAN)) {
            return "what AP build? type 'lukless'/'pure' or e.g. '25 luk' to set a luk target";
        }
        if (job.isA(Job.BOWMAN)) {
            return "what AP build? type 'strless'/'pure' or e.g. '25 str' to set a str target";
        }
        if (job.isA(Job.THIEF)) {
            return "what AP build? type 'dexless'/'pure' or e.g. '25 dex' to set a dex target";
        }
        return null;
    }

    public static String heroSpVariantPrompt() {
        return "hero build: '1h' (1h sword, Brandish first) or '2h' (interleave AC + Brandish for faster charges)?";
    }

    public static String beginnerJobPrompt(int level) {
        if (level >= 10) {
            return "hey i can change jobs now!! warrior, mage, bowman, thief, or pirate?";
        }
        if (level >= 8) {
            return "i can become a mage already if u want, or wait til lv10 for other jobs";
        }
        return null;
    }

    public static String secondJobPrompt(Job job) {
        return switch (job) {
            case WARRIOR -> "lv30! 2nd job time~ fighter, page, or spearman?";
            case MAGICIAN -> "lv30! pick 2nd job: f/p wizard, i/l wizard, or cleric?";
            case BOWMAN -> "lv30! hunter or crossbowman?";
            case THIEF -> "lv30! assassin or bandit?";
            case PIRATE -> "lv30! brawler or gunslinger?";
            default -> null;
        };
    }

    public static String thirdJobPrompt(Job job) {
        return switch (job) {
            case FIGHTER -> "lv70!! 3rd job, type 'crusader'";
            case PAGE -> "lv70!! type 'white knight' or 'wk'";
            case SPEARMAN -> "lv70!! type 'dragon knight' or 'dk'";
            case FP_WIZARD -> "lv70!! type 'fp mage'";
            case IL_WIZARD -> "lv70!! type 'il mage'";
            case CLERIC -> "lv70!! type 'priest'";
            case HUNTER -> "lv70!! type 'ranger'";
            case CROSSBOWMAN -> "lv70!! type 'sniper'";
            case ASSASSIN -> "lv70!! type 'hermit'";
            case BANDIT -> "lv70!! type 'chief bandit' or 'cb'";
            case BRAWLER -> "lv70!! type 'marauder'";
            case GUNSLINGER -> "lv70!! type 'outlaw'";
            default -> null;
        };
    }

    public static String fourthJobPrompt(Job job) {
        return switch (job) {
            case CRUSADER -> "lv120!! type 'hero' for 4th job!!";
            case WHITEKNIGHT -> "lv120!! type 'paladin'";
            case DRAGONKNIGHT -> "lv120!! type 'dark knight' or 'drk'";
            case FP_MAGE -> "lv120!! type 'fp archmage' or 'fp arch'";
            case IL_MAGE -> "lv120!! type 'il archmage' or 'il arch'";
            case PRIEST -> "lv120!! type 'bishop'";
            case RANGER -> "lv120!! type 'bowmaster' or 'bm'";
            case SNIPER -> "lv120!! type 'marksman' or 'mm'";
            case HERMIT -> "lv120!! type 'night lord' or 'nl'";
            case CHIEFBANDIT -> "lv120!! type 'shadower'";
            case MARAUDER -> "lv120!! type 'buccaneer' or 'bucc'";
            case OUTLAW -> "lv120!! type 'corsair'";
            default -> null;
        };
    }
}
