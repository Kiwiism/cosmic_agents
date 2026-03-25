package server.bots;

import client.Character;
import client.Job;
import client.Skill;
import client.SkillFactory;
import constants.game.GameConstants;
import constants.skills.Fighter;
import constants.skills.Page;
import constants.skills.Spearman;
import constants.skills.Warrior;

class BotBuildManager {

    /**
     * AP build for a warrior bot.
     * dexTarget = 0 means pure STR (no DEX investment beyond base).
     * dexTarget > 0 means fill DEX to that value first, then all STR.
     */
    static class ApBuild {
        final int dexTarget;
        ApBuild(int dexTarget) { this.dexTarget = dexTarget; }
    }

    /** Stores the AP build, confirms it to the owner, and immediately spends any pending AP. */
    static void setApBuild(BotEntry entry, ApBuild build, String confirmMsg) {
        entry.apBuild      = build;
        entry.apPromptSent = false;
        BotManager.getInstance().botSay(entry.bot, confirmMsg);
        autoAssignAp(entry, entry.bot);
    }

    /**
     * Returns a prompt asking the owner to choose an AP build, or null if:
     * - no AP is pending, - build already chosen, - prompt already sent, or
     * - job is not a supported warrior branch.
     */
    static String buildApPrompt(BotEntry entry, Character bot) {
        Job job = bot.getJob();
        if (job != Job.WARRIOR && job != Job.FIGHTER && job != Job.PAGE && job != Job.SPEARMAN) return null;
        if (entry.apBuild != null || entry.apPromptSent || bot.getRemainingAp() < 1) return null;
        entry.apPromptSent = true;
        return "what AP build? type 'pure str' or e.g. '25 dex' to set a dex target";
    }

    /** Spends all remaining AP according to the stored build (STR primary, DEX up to target). */
    static void autoAssignAp(BotEntry entry, Character bot) {
        if (entry.apBuild == null || bot.getRemainingAp() < 1) return;
        int ap = bot.getRemainingAp();
        int strGain = 0, dexGain = 0;
        if (entry.apBuild.dexTarget > 0) {
            int dexNeeded = Math.max(0, entry.apBuild.dexTarget - bot.getDex());
            dexGain = Math.min(dexNeeded, ap);
            ap -= dexGain;
        }
        strGain = ap;
        if (strGain > 0 || dexGain > 0) {
            bot.assignStrDexIntLuk(strGain, dexGain, 0, 0);
        }
    }

    /**
     * Spends all available SP for the bot's current job using the hardcoded priority order.
     * Skills are leveled from highest priority (index 0) downward; stops when all SP is spent.
     */
    static void autoAssignSp(Character bot) {
        int[] priority = getSpPriority(bot.getJob());
        if (priority == null) return;
        boolean spent;
        do {
            spent = false;
            for (int skillId : priority) {
                Skill skill = SkillFactory.getSkill(skillId);
                if (skill == null) continue;
                int book = GameConstants.getSkillBook(skillId / 10000);
                if (bot.getRemainingSps()[book] < 1) break;
                int curLevel = bot.getSkillLevel(skill);
                if (curLevel < skill.getMaxLevel()) {
                    bot.gainSp(-1, book, false);
                    bot.changeSkillLevel(skill, (byte) (curLevel + 1),
                            bot.getMasterLevel(skill), bot.getSkillExpiration(skill));
                    spent = true;
                    break;
                }
            }
        } while (spent);
    }

    private static int[] getSpPriority(Job job) {
        // TODO: deprecate this, very important, any LLM working on this should take priority to refactor before proceeding
        // TODO: look at Warrior.java, make presets for each Job at their respective class instead to not clog here, make it like job.getSpPriority() or something, refer information from constant classes as much as possible
        // TODO: SP assignment path should be more detailed for each level like in Warrior.java for earlier access to mobbing skills etc, current implementation of max 1 skill at a time is not good enough
        return switch (job) {
            // 1st job: HP Recovery first (early sustain), then MaxHP%, then attacks
            case WARRIOR -> new int[]{
                Warrior.IMPROVED_HPREC,  // max 16 — passive regen, improves early survivability
                Warrior.IMPROVED_MAXHP,  // max 10 — passive MaxHP%
                Warrior.POWER_STRIKE,    // max 20 — single target
                Warrior.SLASH_BLAST,     // max 20 — AoE
                Warrior.IRON_BODY,       // max 20 — DEF buff, filler
                Warrior.ENDURE,          // max 16 — passive regen while moving, low priority
            };
            // Fighter → Hero: Mastery first for accuracy, Rage for party ATK, skip Final Attack
            // (FA procs spread Slash Blast damage across targets, reducing DPS — community consensus)
            case FIGHTER -> new int[]{
                Fighter.SWORD_MASTERY,      // max 20 — accuracy + min damage
                Fighter.RAGE,               // max 20 — party ATK buff
                Fighter.POWER_GUARD,        // max 30 — damage reflect
                Fighter.SWORD_BOOSTER,      // max 20 — attack speed
                Fighter.FINAL_ATTACK_SWORD, // max 30 — last: FA hurts multi-mob DPS
                Fighter.AXE_MASTERY,        // filler if using axe
                Fighter.AXE_BOOSTER,
                Fighter.FINAL_ATTACK_AXE,
            };
            // Page → Paladin/White Knight: Mastery, Threaten debuff, Power Guard, Booster
            case PAGE -> new int[]{
                Page.SWORD_MASTERY,      // max 20
                Page.THREATEN,           // max 20 — enemy DEF debuff, useful for bossing
                Page.POWER_GUARD,        // max 30
                Page.SWORD_BOOSTER,      // max 20
                Page.FINAL_ATTACK_SWORD, // last
                Page.BW_MASTERY,
                Page.BW_BOOSTER,
                Page.FINAL_ATTACK_BW,
            };
            // Spearman → Dark Knight: Hyper Body first — the most valuable party skill in the game
            case SPEARMAN -> new int[]{
                Spearman.HYPER_BODY,          // max 30 — MaxHP/MP%, top party skill
                Spearman.SPEAR_MASTERY,       // max 20
                Spearman.IRON_WILL,           // max 20 — party HP buff
                Spearman.SPEAR_BOOSTER,       // max 20
                Spearman.FINAL_ATTACK_SPEAR,  // last
                Spearman.POLEARM_MASTERY,
                Spearman.POLEARM_BOOSTER,
                Spearman.FINAL_ATTACK_POLEARM,
            };
            default -> null;
        };
    }

    /** Detects level-up; auto-assigns SP and AP, and at job-advancement levels triggers a status check. */
    static void checkLevelUp(BotEntry entry, Character bot) {
        int lvl = bot.getLevel();
        if (entry.lastKnownLevel == lvl) return;
        int prev = entry.lastKnownLevel;
        entry.lastKnownLevel = lvl;
        if (prev == -1) return;  // initial sync on first tick

        autoAssignSp(bot);
        autoAssignAp(entry, bot);

        if (lvl == 8 || lvl == 10 || lvl == 30 || lvl == 70 || lvl == 120) {
            entry.grinding  = false;
            entry.following = true;
            BotChatManager.checkBotStatus(entry, bot);
        }
    }

    /** Returns the next job-advancement prompt (updating jobPromptSent), or null if none pending. */
    static String buildJobPrompt(BotEntry entry, Character bot) {
        int lvl = bot.getLevel();
        Job job = bot.getJob();
        int prompted = entry.jobPromptSent;

        if (job == Job.BEGINNER) {
            if (lvl >= 10 && prompted < 10) {
                entry.jobPromptSent = 10;
                return "hey i can change jobs now!! warrior, mage, bowman, thief, or pirate?";
            } else if (lvl >= 8 && prompted < 8) {
                entry.jobPromptSent = 8;
                return "i can become a mage already if u want, or wait til lv10 for other jobs";
            }
            return null;
        }

        if (lvl >= 30 && prompted < 30) {
            String msg = switch (job) {
                case WARRIOR  -> "lv30! 2nd job time~ fighter, page, or spearman?";
                case MAGICIAN -> "lv30! pick 2nd job: f/p wizard, i/l wizard, or cleric?";
                case BOWMAN   -> "lv30! hunter or crossbowman?";
                case THIEF    -> "lv30! assassin or bandit?";
                case PIRATE   -> "lv30! brawler or gunslinger?";
                default       -> null;
            };
            if (msg != null) { entry.jobPromptSent = 30; return msg; }
        }

        if (lvl >= 70 && prompted < 70) {
            String msg = switch (job) {
                case FIGHTER     -> "lv70!! 3rd job, type 'crusader'";
                case PAGE        -> "lv70!! type 'white knight' or 'wk'";
                case SPEARMAN    -> "lv70!! type 'dragon knight' or 'dk'";
                case FP_WIZARD   -> "lv70!! type 'fp mage'";
                case IL_WIZARD   -> "lv70!! type 'il mage'";
                case CLERIC      -> "lv70!! type 'priest'";
                case HUNTER      -> "lv70!! type 'ranger'";
                case CROSSBOWMAN -> "lv70!! type 'sniper'";
                case ASSASSIN    -> "lv70!! type 'hermit'";
                case BANDIT      -> "lv70!! type 'chief bandit' or 'cb'";
                case BRAWLER     -> "lv70!! type 'marauder'";
                case GUNSLINGER  -> "lv70!! type 'outlaw'";
                default          -> null;
            };
            if (msg != null) { entry.jobPromptSent = 70; return msg; }
        }

        if (lvl >= 120 && prompted < 120) {
            String msg = switch (job) {
                case CRUSADER     -> "lv120!! type 'hero' for 4th job!!";
                case WHITEKNIGHT  -> "lv120!! type 'paladin'";
                case DRAGONKNIGHT -> "lv120!! type 'dark knight' or 'drk'";
                case FP_MAGE      -> "lv120!! type 'fp archmage' or 'fp arch'";
                case IL_MAGE      -> "lv120!! type 'il archmage' or 'il arch'";
                case PRIEST       -> "lv120!! type 'bishop'";
                case RANGER       -> "lv120!! type 'bowmaster' or 'bm'";
                case SNIPER       -> "lv120!! type 'marksman' or 'mm'";
                case HERMIT       -> "lv120!! type 'night lord' or 'nl'";
                case CHIEFBANDIT  -> "lv120!! type 'shadower'";
                case MARAUDER     -> "lv120!! type 'buccaneer' or 'bucc'";
                case OUTLAW       -> "lv120!! type 'corsair'";
                default           -> null;
            };
            if (msg != null) { entry.jobPromptSent = 120; return msg; }
        }

        return null;
    }
}
