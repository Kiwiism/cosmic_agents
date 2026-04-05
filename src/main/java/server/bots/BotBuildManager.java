package server.bots;

import client.Character;
import client.Job;
import client.Skill;
import client.SkillFactory;
import constants.game.GameConstants;
import constants.skills.Crusader;
import constants.skills.DragonKnight;
import constants.skills.Fighter;
import constants.skills.Hero;
import constants.skills.Page;
import constants.skills.Spearman;
import constants.skills.Warrior;
import constants.skills.WhiteKnight;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class BotBuildManager {

    /**
     * AP build for a warrior bot.
     * dexTarget = 0 means pure STR (no DEX investment beyond base).
     * dexTarget > 0 means fill DEX to that value first, then all STR.
     */
    public static class ApBuild {
        final int dexTarget;
        public ApBuild(int dexTarget) { this.dexTarget = dexTarget; }
    }

    /**
     * One step in a skill build order.
     * Spend SP on skillId until it reaches targetLevel, then advance to the next step.
     * Processing the full list top-to-bottom (skipping already-done steps) is backward-compatible:
     * a bot that missed many levels catches up by draining all available SP in sequence.
     */
    public record BuildStep(int skillId, int targetLevel) {}

    private static BuildStep s(int id, int to) { return new BuildStep(id, to); }

    // ─── AP ───────────────────────────────────────────────────────────────────

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

    // ─── SP ───────────────────────────────────────────────────────────────────

    /**
     * Returns a prompt asking for the SP build variant, or null if not needed.
     * Currently only Hero has two documented builds (1h sword vs 2h).
     * Sets spVariantPromptSent so Hero SP is held until the owner responds.
     */
    static String buildSpVariantPrompt(BotEntry entry, Character bot) {
        if (bot.getJob() != Job.HERO) return null;
        if (entry.spVariant != null || entry.spVariantPromptSent || bot.getRemainingSps()[3] < 1) return null;
        entry.spVariantPromptSent = true;
        return "hero build: '1h' (1h sword, Brandish first) or '2h' (interleave AC + Brandish for faster charges)?";
    }

    /**
     * Spends all available SP following the per-level build order for the bot's current job.
     * Processes steps top-to-bottom; skips steps already at or past their target level.
     * This is naturally backward-compatible: if the bot accumulated SP over many levels,
     * all of it is drained in the correct sequence.
     *
     * For Hero specifically, SP is held until the owner chooses "1h" or "2h".
     */
    static void autoAssignSp(BotEntry entry, Character bot) {
        // Hold Hero SP until owner chooses a variant, regardless of when the prompt was sent.
        if (bot.getJob() == Job.HERO && entry.spVariant == null) return;

        List<BuildStep> steps = getBuildOrder(bot.getJob(), entry.spVariant);
        if (steps == null) return;

        autoAssignSp(bot, steps);
    }

    static String respecSp(BotEntry entry, Character bot) {
        if (bot.getJob() == Job.HERO && entry.spVariant == null) {
            return "need your hero build first. say '1h' or '2h'";
        }

        List<Job> buildPath = getSupportedBuildPath(bot.getJob());
        if (buildPath == null) {
            return "dont have an sp respec build for my job yet";
        }

        int[] refundedSp = new int[5];
        List<Skill> skillsToReset = new ArrayList<>();
        for (Map.Entry<Skill, Character.SkillEntry> learned : bot.getSkills().entrySet()) {
            Skill skill = learned.getKey();
            Character.SkillEntry skillEntry = learned.getValue();
            if (skill == null || skillEntry == null || skillEntry.skillevel <= 0) {
                continue;
            }

            int skillId = skill.getId();
            if (skill.isBeginnerSkill() || GameConstants.isHiddenSkills(skillId)) {
                continue;
            }
            if (!GameConstants.isInJobTree(skillId, bot.getJob().getId())) {
                continue;
            }

            refundedSp[GameConstants.getSkillBook(skillId / 10000)] += skillEntry.skillevel;
            skillsToReset.add(skill);
        }

        for (Skill skill : skillsToReset) {
            bot.changeSkillLevel(skill, (byte) 0, bot.getMasterLevel(skill), bot.getSkillExpiration(skill));
        }
        for (int book = 0; book < refundedSp.length; book++) {
            if (refundedSp[book] > 0) {
                bot.gainSp(refundedSp[book], book, false);
            }
        }

        for (Job job : buildPath) {
            List<BuildStep> steps = getBuildOrder(job, entry.spVariant);
            if (steps != null) {
                autoAssignSp(bot, steps);
            }
        }

        return "ok, rebuilt my sp using the bot build";
    }

    private static void autoAssignSp(Character bot, List<BuildStep> steps) {
        for (BuildStep step : steps) {
            Skill skill = SkillFactory.getSkill(step.skillId());
            if (skill == null) continue;
            int book = GameConstants.getSkillBook(step.skillId() / 10000);
            if (bot.getRemainingSps()[book] < 1) continue; // no SP for this tier; try others
            while (bot.getRemainingSps()[book] > 0) {
                int lv = bot.getSkillLevel(skill);
                if (lv >= step.targetLevel()) break; // step done
                bot.gainSp(-1, book, false);
                bot.changeSkillLevel(skill, (byte) (lv + 1),
                        bot.getMasterLevel(skill), bot.getSkillExpiration(skill));
            }
        }
    }

    private static List<Job> getSupportedBuildPath(Job job) {
        return switch (job) {
            case WARRIOR -> List.of(Job.WARRIOR);
            case FIGHTER -> List.of(Job.WARRIOR, Job.FIGHTER);
            case CRUSADER -> List.of(Job.WARRIOR, Job.FIGHTER, Job.CRUSADER);
            case HERO -> List.of(Job.WARRIOR, Job.FIGHTER, Job.CRUSADER, Job.HERO);
            case PAGE -> List.of(Job.WARRIOR, Job.PAGE);
            case WHITEKNIGHT -> List.of(Job.WARRIOR, Job.PAGE, Job.WHITEKNIGHT);
            case SPEARMAN -> List.of(Job.WARRIOR, Job.SPEARMAN);
            case DRAGONKNIGHT -> List.of(Job.WARRIOR, Job.SPEARMAN, Job.DRAGONKNIGHT);
            default -> null;
        };
    }

    // ─── Build orders ─────────────────────────────────────────────────────────

    private static List<BuildStep> getBuildOrder(Job job, String variant) {
        return switch (job) {
            case WARRIOR      -> warriorBuild();
            case FIGHTER      -> fighterBuild();
            case CRUSADER     -> crusaderBuild();
            case HERO         -> "2h".equals(variant) ? hero2hBuild() : hero1hBuild();
            case PAGE         -> pageBuild();
            case WHITEKNIGHT  -> whiteKnightBuild();
            case SPEARMAN     -> spearmanBuild();
            case DRAGONKNIGHT -> dragonKnightBuild();
            default           -> null;
        };
    }

    /**
     * Warrior (lv10–30) — per Warrior.java build order comments.
     * HP Recovery → MaxHP% → 1pt Power Strike → Slash Blast (AoE, max) → Power Strike (max) → fill HP Recovery.
     */
    private static List<BuildStep> warriorBuild() {
        return List.of(
            s(Warrior.IMPROVED_HPREC, 5),   // lv10–12: early passive regen
            s(Warrior.IMPROVED_MAXHP, 10),  // lv12–15: MaxHP% (max)
            s(Warrior.POWER_STRIKE, 1),     // lv15: 1 pt before switching to AoE
            s(Warrior.SLASH_BLAST, 20),     // lv16–22: max AoE for mobbing
            s(Warrior.POWER_STRIKE, 20),    // lv22–28: max single-target
            s(Warrior.IMPROVED_HPREC, 16)  // lv29–30: fill remaining HP Recovery
        );
    }

    /**
     * Fighter (lv30–70) — per Fighter.java build order comments (sword path).
     * Mastery → early Booster → 1pt Rage → Power Guard (max) → Rage (max) → finish Booster/Mastery.
     * Final Attack deliberately skipped (community consensus: hurts multi-mob DPS via Slash Blast spread).
     */
    private static List<BuildStep> fighterBuild() {
        return List.of(
            s(Fighter.SWORD_MASTERY, 19),   // lv30–36: accuracy + min-damage
            s(Fighter.SWORD_BOOSTER, 6),    // lv37–38: early attack-speed
            s(Fighter.RAGE, 3),             // lv39: 1 Rage for party buff access
            s(Fighter.POWER_GUARD, 30),     // lv40–49: damage reflect (max)
            s(Fighter.RAGE, 30),            // lv50–58: party ATK buff (max)
            s(Fighter.SWORD_BOOSTER, 20),   // lv59–63: finish booster
            s(Fighter.SWORD_MASTERY, 20)    // lv63: final mastery point
        );
    }

    /**
     * Crusader (lv70–120) — per Crusader.java build order comments (sword path).
     * Combo → Coma → Panic → Armor Crash → filler.
     */
    private static List<BuildStep> crusaderBuild() {
        return List.of(
            s(Crusader.COMBO, 30),           // lv70–80: max combo for faster charge rates
            s(Crusader.SWORD_COMA, 30),      // lv80–90: primary mobbing skill
            s(Crusader.SWORD_PANIC, 30),     // lv90–100: primary bossing skill
            s(Crusader.ARMOR_CRASH, 20),     // lv100–107: DEF-reduction debuff
            s(Crusader.SHOUT, 20),           // filler — AoE stun
            s(Crusader.SHIELD_MASTERY, 20),  // filler
            s(Crusader.IMPROVING_MPREC, 20)  // filler
        );
    }

    /**
     * Hero 1h sword + shield build (lv120–200) — per Hero.java 1h build comments.
     * Rush(1) → Brandish(max) → AC(max) → Stance(max) → partial MW → Will(1) → MW → Achilles → Will(max) → Guardian → Enrage → Rush(max) → MW(max).
     */
    private static List<BuildStep> hero1hBuild() {
        return List.of(
            s(Hero.RUSH, 1),               // lv120: unlock rush (mobility + cancel)
            s(Hero.BRANDISH, 30),          // lv120–131: primary AoE attack (max)
            s(Hero.ADVANCED_COMBO, 30),    // lv131–141: AC for bigger Coma/Panic charges
            s(Hero.STANCE, 30),            // lv141–151: knockback immunity (max)
            s(Hero.MAPLE_WARRIOR, 13),     // lv151–155: partial MW before will
            s(Hero.HEROS_WILL, 1),         // lv155: seduce/seal resist unlock
            s(Hero.MAPLE_WARRIOR, 19),     // lv156–157: continue MW
            s(Hero.ACHILLES, 30),          // lv158–167: damage reduction (max)
            s(Hero.HEROS_WILL, 5),         // lv168–169: finish Hero's Will (max)
            s(Hero.GUARDIAN, 30),          // lv169–179: guard chance (max)
            s(Hero.ENRAGE, 30),            // lv179–189: damage boost (max)
            s(Hero.RUSH, 30),              // lv189–197: finish rush
            s(Hero.MAPLE_WARRIOR, 30)      // lv197–200: finish Maple Warrior
        );
    }

    /**
     * Hero 2h build (lv120–200) — per Hero.java 2h build comments.
     * Interleaves AC early so Coma/Panic charge faster before Brandish is maxed;
     * prioritizes reaching Brandish lv21 (hits 3 targets) then maxing AC before finishing Brandish.
     */
    private static List<BuildStep> hero2hBuild() {
        return List.of(
            s(Hero.RUSH, 1),               // lv120: 1 rush
            s(Hero.BRANDISH, 1),           // lv120: 1 brandish
            s(Hero.ADVANCED_COMBO, 1),     // lv120: 1 AC (faster charge rates immediately)
            s(Hero.BRANDISH, 21),          // lv121–128: brandish to 21 (3-mob threshold)
            s(Hero.ADVANCED_COMBO, 30),    // lv128–138: max AC for maximum charge benefit
            s(Hero.BRANDISH, 30),          // lv138–141: finish brandish
            s(Hero.STANCE, 30),            // lv141–151: knockback immunity
            s(Hero.MAPLE_WARRIOR, 13),
            s(Hero.HEROS_WILL, 1),
            s(Hero.MAPLE_WARRIOR, 19),
            s(Hero.ACHILLES, 30),
            s(Hero.HEROS_WILL, 5),
            s(Hero.GUARDIAN, 30),
            s(Hero.ENRAGE, 30),
            s(Hero.RUSH, 30),
            s(Hero.MAPLE_WARRIOR, 30)
        );
    }

    /**
     * Page (lv30–70) — sword path.
     * Mastery → Threaten → Power Guard → Booster.
     * (No official per-level guide; community-standard order.)
     */
    private static List<BuildStep> pageBuild() {
        return List.of(
            s(Page.SWORD_MASTERY, 20),     // accuracy + min-damage
            s(Page.THREATEN, 20),          // enemy DEF debuff — valuable for bossing
            s(Page.POWER_GUARD, 30),       // damage reflect
            s(Page.SWORD_BOOSTER, 20)      // attack speed
        );
    }

    /**
     * White Knight (lv70–120) — sword path.
     * Charge Blow → Lightning Charge → Magic Crash → Shield Mastery → MP Recovery.
     */
    private static List<BuildStep> whiteKnightBuild() {
        return List.of(
            s(WhiteKnight.CHARGE_BLOW, 30),           // primary charged attack
            s(WhiteKnight.SWORD_LIT_CHARGE, 30),      // lightning: attack speed + element
            s(WhiteKnight.MAGIC_CRASH, 20),           // magic cancel utility
            s(WhiteKnight.SHIELD_MASTERY, 20),        // defensive passive
            s(WhiteKnight.IMPROVING_MP_RECOVERY, 20)  // filler
        );
    }

    /**
     * Spearman (lv30–70) — spear path.
     * Hyper Body first (best party skill) → Mastery → Iron Will → Booster.
     */
    private static List<BuildStep> spearmanBuild() {
        return List.of(
            s(Spearman.HYPER_BODY, 30),     // MaxHP/MP% party buff — highest priority
            s(Spearman.SPEAR_MASTERY, 20),  // accuracy + min-damage
            s(Spearman.IRON_WILL, 20),      // party HP buff
            s(Spearman.SPEAR_BOOSTER, 20)   // attack speed
        );
    }

    /**
     * Dragon Knight (lv70–120) — spear path.
     * Dragon Roar (AoE) → Dragon Blood (passive WAtk) → Crusher → Dragon Fury → fillers.
     */
    private static List<BuildStep> dragonKnightBuild() {
        return List.of(
            s(DragonKnight.DRAGON_ROAR, 30),         // best AoE — prioritize for mobbing
            s(DragonKnight.DRAGON_BLOOD, 30),        // passive WAtk buff
            s(DragonKnight.SPEAR_CRUSHER, 30),       // single-target finisher
            s(DragonKnight.SPEAR_DRAGON_FURY, 30),   // extra hits on spear attacks
            s(DragonKnight.SACRIFICE, 20),           // filler
            s(DragonKnight.POWER_CRASH, 20),         // filler
            s(DragonKnight.ELEMENTAL_RESISTANCE, 20) // filler
        );
    }

    // ─── Level-up ─────────────────────────────────────────────────────────────

    /**
     * Detects level-up; sends prompts BEFORE spending SP/AP so that Hero's
     * variant prompt can gate spending until the owner responds.
     */
    static void checkLevelUp(BotEntry entry, Character bot) {
        int lvl = bot.getLevel();
        if (entry.lastKnownLevel == lvl) return;
        int prev = entry.lastKnownLevel;
        entry.lastKnownLevel = lvl;
        if (prev == -1) {
            autoAssignSp(entry, bot);
            autoAssignAp(entry, bot);
            return;
        }

        // Send job/build prompts first — some (Hero SP variant) gate SP spending
        if (lvl == 8 || lvl == 10 || lvl == 30 || lvl == 70 || lvl == 120) {
            entry.grinding  = false;
            entry.following = true;
            BotChatManager.checkBotStatus(entry, bot);
        }

        autoAssignSp(entry, bot);
        autoAssignAp(entry, bot);
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
