package server.bots;

import client.Character;
import client.Job;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.WeaponType;
import constants.skills.Assassin;
import constants.skills.Crusader;
import constants.skills.DragonKnight;
import constants.skills.Fighter;
import constants.skills.Pirate;
import constants.skills.Rogue;
import constants.skills.Spearman;
import org.junit.jupiter.api.Test;
import server.life.MonsterStats;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotEquipManagerTest {

    @Test
    void firstJobBowmanAcceptsBowAndCrossbowOnly() {
        Character bot = mock(Character.class);
        when(bot.getJob()).thenReturn(Job.BOWMAN);

        assertTrue(BotEquipManager.isWeaponCompatible(bot, WeaponType.BOW));
        assertTrue(BotEquipManager.isWeaponCompatible(bot, WeaponType.CROSSBOW));
        assertFalse(BotEquipManager.isWeaponCompatible(bot, WeaponType.CLAW));
    }

    @Test
    void hunterOnlyAcceptsBows() {
        Character bot = mock(Character.class);
        when(bot.getJob()).thenReturn(Job.HUNTER);

        assertTrue(BotEquipManager.isWeaponCompatible(bot, WeaponType.BOW));
        assertFalse(BotEquipManager.isWeaponCompatible(bot, WeaponType.CROSSBOW));
    }

    @Test
    void firstJobPirateUsesSkillsToPickGunOrKnuckle() {
        Character gunPirate = mock(Character.class);
        when(gunPirate.getJob()).thenReturn(Job.PIRATE);
        when(gunPirate.getSkillLevel(Pirate.DOUBLE_SHOT)).thenReturn(1);
        when(gunPirate.getSkillLevel(Pirate.FLASH_FIST)).thenReturn(0);
        when(gunPirate.getSkillLevel(Pirate.SOMERSAULT_KICK)).thenReturn(0);

        assertTrue(BotEquipManager.isWeaponCompatible(gunPirate, WeaponType.GUN));
        assertFalse(BotEquipManager.isWeaponCompatible(gunPirate, WeaponType.KNUCKLE));

        Character knucklePirate = mock(Character.class);
        when(knucklePirate.getJob()).thenReturn(Job.PIRATE);
        when(knucklePirate.getSkillLevel(Pirate.DOUBLE_SHOT)).thenReturn(0);
        when(knucklePirate.getSkillLevel(Pirate.FLASH_FIST)).thenReturn(1);
        when(knucklePirate.getSkillLevel(Pirate.SOMERSAULT_KICK)).thenReturn(0);

        assertTrue(BotEquipManager.isWeaponCompatible(knucklePirate, WeaponType.KNUCKLE));
        assertFalse(BotEquipManager.isWeaponCompatible(knucklePirate, WeaponType.GUN));
    }

    @Test
    void fighterUsesSwordOrAxeSkillsToChooseWeaponFamily() {
        Character swordFighter = mock(Character.class);
        when(swordFighter.getJob()).thenReturn(Job.FIGHTER);
        when(swordFighter.getSkillLevel(Fighter.SWORD_MASTERY)).thenReturn(1);
        when(swordFighter.getSkillLevel(Fighter.SWORD_BOOSTER)).thenReturn(0);
        when(swordFighter.getSkillLevel(Fighter.AXE_MASTERY)).thenReturn(0);
        when(swordFighter.getSkillLevel(Fighter.AXE_BOOSTER)).thenReturn(0);

        assertTrue(BotEquipManager.isWeaponCompatible(swordFighter, WeaponType.SWORD1H));
        assertTrue(BotEquipManager.isWeaponCompatible(swordFighter, WeaponType.SWORD2H));
        assertFalse(BotEquipManager.isWeaponCompatible(swordFighter, WeaponType.GENERAL1H_SWING));

        Character axeFighter = mock(Character.class);
        when(axeFighter.getJob()).thenReturn(Job.FIGHTER);
        when(axeFighter.getSkillLevel(Fighter.SWORD_MASTERY)).thenReturn(0);
        when(axeFighter.getSkillLevel(Fighter.SWORD_BOOSTER)).thenReturn(0);
        when(axeFighter.getSkillLevel(Fighter.AXE_MASTERY)).thenReturn(1);
        when(axeFighter.getSkillLevel(Fighter.AXE_BOOSTER)).thenReturn(0);

        assertTrue(BotEquipManager.isWeaponCompatible(axeFighter, WeaponType.GENERAL1H_SWING));
        assertTrue(BotEquipManager.isWeaponCompatible(axeFighter, WeaponType.GENERAL2H_SWING));
        assertFalse(BotEquipManager.isWeaponCompatible(axeFighter, WeaponType.SWORD1H));
    }

    @Test
    void spearmanUsesSpearOrPolearmSkillsToChooseWeaponFamily() {
        Character spearBot = mock(Character.class);
        when(spearBot.getJob()).thenReturn(Job.SPEARMAN);
        when(spearBot.getSkillLevel(Spearman.SPEAR_MASTERY)).thenReturn(1);
        when(spearBot.getSkillLevel(Spearman.SPEAR_BOOSTER)).thenReturn(0);
        when(spearBot.getSkillLevel(Spearman.POLEARM_MASTERY)).thenReturn(0);
        when(spearBot.getSkillLevel(Spearman.POLEARM_BOOSTER)).thenReturn(0);

        assertTrue(BotEquipManager.isWeaponCompatible(spearBot, WeaponType.SPEAR_STAB));
        assertTrue(BotEquipManager.isWeaponCompatible(spearBot, WeaponType.SPEAR_SWING));
        assertFalse(BotEquipManager.isWeaponCompatible(spearBot, WeaponType.POLE_ARM_SWING));

        Character polearmBot = mock(Character.class);
        when(polearmBot.getJob()).thenReturn(Job.SPEARMAN);
        when(polearmBot.getSkillLevel(Spearman.SPEAR_MASTERY)).thenReturn(0);
        when(polearmBot.getSkillLevel(Spearman.SPEAR_BOOSTER)).thenReturn(0);
        when(polearmBot.getSkillLevel(Spearman.POLEARM_MASTERY)).thenReturn(1);
        when(polearmBot.getSkillLevel(Spearman.POLEARM_BOOSTER)).thenReturn(0);

        assertTrue(BotEquipManager.isWeaponCompatible(polearmBot, WeaponType.POLE_ARM_SWING));
        assertTrue(BotEquipManager.isWeaponCompatible(polearmBot, WeaponType.POLE_ARM_STAB));
        assertFalse(BotEquipManager.isWeaponCompatible(polearmBot, WeaponType.SPEAR_STAB));
    }

    @Test
    void fourthJobWarriorsKeepTheirThirdJobWeaponFamily() {
        Character hero = mock(Character.class);
        when(hero.getJob()).thenReturn(Job.HERO);
        when(hero.getSkillLevel(Crusader.SWORD_COMA)).thenReturn(1);
        when(hero.getSkillLevel(Crusader.SWORD_PANIC)).thenReturn(0);
        when(hero.getSkillLevel(Crusader.AXE_COMA)).thenReturn(0);
        when(hero.getSkillLevel(Crusader.AXE_PANIC)).thenReturn(0);

        assertTrue(BotEquipManager.isWeaponCompatible(hero, WeaponType.SWORD1H));
        assertFalse(BotEquipManager.isWeaponCompatible(hero, WeaponType.GENERAL1H_SWING));

        Character darkKnight = mock(Character.class);
        when(darkKnight.getJob()).thenReturn(Job.DARKKNIGHT);
        when(darkKnight.getSkillLevel(DragonKnight.SPEAR_CRUSHER)).thenReturn(0);
        when(darkKnight.getSkillLevel(DragonKnight.SPEAR_DRAGON_FURY)).thenReturn(0);
        when(darkKnight.getSkillLevel(DragonKnight.POLE_ARM_CRUSHER)).thenReturn(1);
        when(darkKnight.getSkillLevel(DragonKnight.POLE_ARM_DRAGON_FURY)).thenReturn(0);

        assertTrue(BotEquipManager.isWeaponCompatible(darkKnight, WeaponType.POLE_ARM_SWING));
        assertFalse(BotEquipManager.isWeaponCompatible(darkKnight, WeaponType.SPEAR_STAB));
    }

    @Test
    void luckySevenThiefPrefersClaws() {
        Character bot = mock(Character.class);
        when(bot.getJob()).thenReturn(Job.THIEF);
        when(bot.getSkillLevel(Rogue.LUCKY_SEVEN)).thenReturn(1);
        when(bot.getSkillLevel(Rogue.DOUBLE_STAB)).thenReturn(0);

        assertTrue(BotEquipManager.isWeaponCompatible(bot, WeaponType.CLAW));
        assertFalse(BotEquipManager.isWeaponCompatible(bot, WeaponType.DAGGER_OTHER));
    }

    @Test
    void mageUsefulGearScoreIgnoresDexAndValuesInt() {
        Equip dexOverall = mock(Equip.class);
        when(dexOverall.getDex()).thenReturn((short) 10);
        Equip intOverall = mock(Equip.class);
        when(intOverall.getInt()).thenReturn((short) 5);

        assertTrue(BotEquipManager.usefulStatSum(intOverall, Job.MAGICIAN)
                > BotEquipManager.usefulStatSum(dexOverall, Job.MAGICIAN));
    }

    @Test
    void expectedDamageReducesToMidMinusWdefBelowMin() {
        // wdef <= rawMin: every roll is unclamped, expected = (rawMin + rawMax)/2 - wdef
        assertEquals(60.0, BotEquipManager.expectedDamageAfterDef(100, 15), 0.01);
    }

    @Test
    void expectedDamageClampsToOneWhenWdefExceedsMax() {
        assertEquals(1.0, BotEquipManager.expectedDamageAfterDef(100, 200), 0.01);
    }

    @Test
    void expectedDamagePreservesUpperTailWhenWdefExceedsMid() {
        // rawMax=100, rawMin=50, wdef=80 (between mid=75 and max=100). Old formula would have
        // returned max(1, 75-80)=1, erasing the [80,100] upper tail. Integral of clamped uniform
        // here equals 0.6 (clamped fraction) + 400/(2*50) = 0.6 + 4 = 4.6.
        assertEquals(4.6, BotEquipManager.expectedDamageAfterDef(100, 80), 0.01);
    }

    @Test
    void expectedDamageDifferentiatesCandidatesAgainstHighWdefMob() {
        // Two candidates with slightly different rawMax against a high-WDEF mob: old formula
        // collapsed both to 1; new formula keeps a meaningful gap.
        double weaker = BotEquipManager.expectedDamageAfterDef(100, 80);
        double stronger = BotEquipManager.expectedDamageAfterDef(120, 80);
        assertTrue(stronger > weaker + 0.5,
                "expected stronger > weaker by margin; got " + stronger + " vs " + weaker);
    }

    @Test
    void mapDamageProfilePrefersHigherLevelThenAvoidability() {
        MonsterStats weaker = new MonsterStats();
        weaker.setLevel(48);
        weaker.setAvoidability(10);
        weaker.setPDDamage(120);

        MonsterStats moreEvasive = new MonsterStats();
        moreEvasive.setLevel(48);
        moreEvasive.setAvoidability(25);
        moreEvasive.setPDDamage(80);

        BotEquipManager.MapDamageProfile profile = BotEquipManager.MapDamageProfile.fromStats(
                List.of(weaker, moreEvasive));

        assertEquals(48, profile.mobLevel());
        assertEquals(25, profile.mobAvoid());
        assertEquals(80, profile.mobWdef());
    }

    /**
     * Reproduces equiplog-Clawer-2026-05-04T134011.txt: a level-41 ASSASSIN with weak naked
     * stats had Bronze Wolfskin (LUK0) and Blue Moon Gloves (LUK2) reserved as "RESERVED_FOR_SELF"
     * even though Purple Work Gloves (LUK4) was already equipped and dominated them on the
     * stat track that matters for an assassin (LUK). The new baseline-beat rule should keep
     * only items that exceed the bot's currently-wearable best on a relevant stat (LUK/DEX/WATK
     * for thieves), and trash everything else.
     */
    @Test
    void selectItemsBeatingBaselineReproducesAssassinGloveLog() {
        // baseline = items the bot can wear with naked stats (incl. currently-equipped)
        Equip purpleWork = glove(/*luk*/ 4, /*dex*/ 0, /*watk*/ 0);    // equipped
        Equip redMarker = glove(/*luk*/ 0, /*dex*/ 0, /*watk*/ 0);     // INT3 — irrelevant for sin
        Equip bronzeWolfskinBaseline = glove(0, 0, 0);                 // baseline copy
        Equip dexEq2 = glove(/*luk*/ 0, /*dex*/ 2, /*watk*/ 0);
        Equip dexEq4 = glove(/*luk*/ 0, /*dex*/ 4, /*watk*/ 0);

        // candidates = bag items (subset of which are also in baseline if naked-wearable)
        Equip bronzeWolfskin = bronzeWolfskinBaseline;                 // naked-wearable
        Equip blueMoon = glove(/*luk*/ 2, 0, 0);                       // not naked-wearable (dex90 req)
        Equip orichalcon = glove(/*luk*/ 7, 0, 0);                     // not naked-wearable (dex70 req)
        Equip dexUneq3 = glove(0, /*dex*/ 3, 0);                       // not naked-wearable
        Equip dexUneq5 = glove(0, /*dex*/ 5, 0);                       // not naked-wearable

        List<Equip> baseline = List.of(purpleWork, redMarker, bronzeWolfskinBaseline, dexEq2, dexEq4);
        List<Equip> bagItems = List.of(redMarker, bronzeWolfskin, blueMoon, orichalcon,
                                       dexEq2, dexEq4, dexUneq3, dexUneq5);

        Set<Equip> keep = BotEquipManager.selectItemsBeatingBaseline(
                BotEquipManager.relevantStatsFor(Job.ASSASSIN), bagItems, baseline);

        // Identity comparison: orichalcon beats LUK baseline 4, dexEq4 ties DEX baseline 4,
        // dexUneq5 beats DEX baseline 4. Everything else fails on every relevant stat.
        Set<Equip> expected = identitySet(orichalcon, dexEq4, dexUneq5);
        assertEquals(expected, identitySet(keep.toArray(new Equip[0])),
                "expected only Orichalcon, +4 DEX equippable, and +5 DEX unequippable");

        // Spot-check trash exclusions for the original log items
        assertFalse(keep.contains(bronzeWolfskin), "Bronze Wolfskin (LUK0) should be trash for sin");
        assertFalse(keep.contains(blueMoon), "Blue Moon (LUK2 < baseline 4) should be trash");
        assertFalse(keep.contains(redMarker), "Red Marker (INT-only) should be trash for sin");
        assertFalse(keep.contains(dexEq2), "+2 DEX equippable (< baseline 4) should be trash");
        assertFalse(keep.contains(dexUneq3), "+3 DEX unequippable (< baseline 4) should be trash");
    }

    /**
     * Balanced gear must survive when baseline only has lopsided alternatives. With baseline
     * gloves at (LUK10,DEX0) and (LUK0,DEX10), candidates (9,9), (10,8), and (1,10) are all
     * Pareto-non-dominated on the {LUK,DEX} subset and must be kept; only candidates strictly
     * dominated by some baseline item (e.g. (9,0) dominated by (10,0)) get trashed.
     */
    @Test
    void selectItemsBeatingBaselineKeepsParetoNonDominatedCombinations() {
        Equip pureLuk = glove(/*luk*/ 10, 0, 0);
        Equip pureDex = glove(0, /*dex*/ 10, 0);
        List<Equip> baseline = List.of(pureLuk, pureDex);

        Equip balanced99 = glove(9, 9, 0);
        Equip dexHeavy108 = glove(8, 10, 0);
        Equip lukHeavy101 = glove(10, 1, 0);
        Equip strictlyWorseLuk = glove(9, 0, 0);   // dominated by pureLuk
        Equip strictlyWorseDex = glove(0, 9, 0);   // dominated by pureDex

        List<Equip> bagItems = List.of(balanced99, dexHeavy108, lukHeavy101,
                strictlyWorseLuk, strictlyWorseDex);

        Set<Equip> keep = BotEquipManager.selectItemsBeatingBaseline(
                BotEquipManager.relevantStatsFor(Job.ASSASSIN), bagItems, baseline);

        assertTrue(keep.contains(balanced99), "(9 LUK, 9 DEX) should be kept — not dominated by either pure baseline");
        assertTrue(keep.contains(dexHeavy108), "(8 LUK, 10 DEX) should be kept");
        assertTrue(keep.contains(lukHeavy101), "(10 LUK, 1 DEX) should be kept");
        assertFalse(keep.contains(strictlyWorseLuk), "(9 LUK, 0 DEX) should be trash — dominated by pure-LUK baseline");
        assertFalse(keep.contains(strictlyWorseDex), "(0 LUK, 9 DEX) should be trash — dominated by pure-DEX baseline");
    }

    /**
     * Future-tier candidates do NOT compete with each other — only the naked-wearable
     * baseline pool can trash them. Two future gloves where one strictly dominates the
     * other (LUK8/DEX10 vs LUK7/DEX10) must both survive, since the bot may unlock them
     * at different stat thresholds and we don't want to prematurely throw away any path.
     */
    @Test
    void selectItemsBeatingBaselineKeepsAllFutureTierEvenWhenOneDominatesAnother() {
        Equip pureLuk = glove(10, 0, 0);
        Equip pureDex = glove(0, 10, 0);
        List<Equip> baseline = List.of(pureLuk, pureDex);

        Equip dexHeavy108 = glove(/*luk*/ 8, /*dex*/ 10, 0);
        Equip dexHeavy107 = glove(/*luk*/ 7, /*dex*/ 10, 0);

        Set<Equip> keep = BotEquipManager.selectItemsBeatingBaseline(
                BotEquipManager.relevantStatsFor(Job.ASSASSIN),
                List.of(dexHeavy108, dexHeavy107), baseline);

        assertTrue(keep.contains(dexHeavy108), "(LUK8, DEX10) should be kept");
        assertTrue(keep.contains(dexHeavy107),
                "(LUK7, DEX10) should also be kept — future-tier candidates don't compete with each other");
    }

    /**
     * Naked-wearable items in the same slot dominate each other on the relevant subset:
     * a (LUK15, DEX1) glove evicts a pure (LUK15, DEX0) glove from the keep set because
     * the former is strictly better on the relevant-stat vector and both are wearable now.
     */
    @Test
    void selectItemsBeatingBaselineLuk15Dex1DominatesPureLuk15() {
        Equip pureLuk15 = glove(/*luk*/ 15, /*dex*/ 0, 0);
        Equip luk15Dex1 = glove(/*luk*/ 15, /*dex*/ 1, 0);
        List<Equip> baseline = List.of(pureLuk15, luk15Dex1);   // both naked-wearable
        List<Equip> bagItems = List.of(pureLuk15, luk15Dex1);

        Set<Equip> keep = BotEquipManager.selectItemsBeatingBaseline(
                BotEquipManager.relevantStatsFor(Job.ASSASSIN), bagItems, baseline);

        assertTrue(keep.contains(luk15Dex1), "(LUK15, DEX1) should be kept");
        assertFalse(keep.contains(pureLuk15),
                "(LUK15, DEX0) should be trash — dominated by naked-wearable (LUK15, DEX1)");
    }

    @Test
    void relevantStatsForJobMatchesClassRoles() {
        assertEquals(EnumSet.of(BotEquipManager.RelevantStat.LUK, BotEquipManager.RelevantStat.DEX,
                                BotEquipManager.RelevantStat.WATK),
                BotEquipManager.relevantStatsFor(Job.ASSASSIN));
        assertEquals(EnumSet.of(BotEquipManager.RelevantStat.STR, BotEquipManager.RelevantStat.DEX,
                                BotEquipManager.RelevantStat.WATK, BotEquipManager.RelevantStat.ACC),
                BotEquipManager.relevantStatsFor(Job.FIGHTER));
        assertEquals(EnumSet.of(BotEquipManager.RelevantStat.STR, BotEquipManager.RelevantStat.DEX,
                                BotEquipManager.RelevantStat.WATK, BotEquipManager.RelevantStat.ACC),
                BotEquipManager.relevantStatsFor(Job.BRAWLER));
        assertEquals(EnumSet.of(BotEquipManager.RelevantStat.DEX, BotEquipManager.RelevantStat.STR,
                                BotEquipManager.RelevantStat.WATK),
                BotEquipManager.relevantStatsFor(Job.GUNSLINGER));
        assertEquals(EnumSet.of(BotEquipManager.RelevantStat.DEX, BotEquipManager.RelevantStat.STR,
                                BotEquipManager.RelevantStat.WATK),
                BotEquipManager.relevantStatsFor(Job.HUNTER));
        assertEquals(EnumSet.of(BotEquipManager.RelevantStat.INT, BotEquipManager.RelevantStat.LUK,
                                BotEquipManager.RelevantStat.MATK),
                BotEquipManager.relevantStatsFor(Job.CLERIC));
    }

    private static Equip glove(int luk, int dex, int watk) {
        Equip e = mock(Equip.class);
        when(e.getStr()).thenReturn((short) 0);
        when(e.getDex()).thenReturn((short) dex);
        when(e.getInt()).thenReturn((short) 0);
        when(e.getLuk()).thenReturn((short) luk);
        when(e.getWatk()).thenReturn((short) watk);
        when(e.getMatk()).thenReturn((short) 0);
        when(e.getAcc()).thenReturn((short) 0);
        return e;
    }

    @Test
    void pureIntOverallUsefulToMageSiblingButNotToAssassin() {
        // Reproduces Blue-Calas scenario: a mage overall has positive INT (mage-relevant)
        // but zero LUK/DEX/WATK, so it has no relevant stat for an Assassin.
        // This validates the Pareto predicate shared by self-reserve and sibling-reserve.
        Equip pureInt = mageOverall(/*int*/ 7, /*luk*/ 0);

        Set<Equip> usefulToMage = BotEquipManager.selectItemsBeatingBaseline(
                BotEquipManager.relevantStatsFor(Job.MAGICIAN), List.of(pureInt), List.of());
        Set<Equip> usefulToSin = BotEquipManager.selectItemsBeatingBaseline(
                BotEquipManager.relevantStatsFor(Job.ASSASSIN), List.of(pureInt), List.of());

        assertTrue(usefulToMage.contains(pureInt),
                "INT overall beats empty baseline for mage sibling");
        assertFalse(usefulToSin.contains(pureInt),
                "INT-only overall has no LUK/DEX/WATK — not useful to Assassin");
    }

    @Test
    void siblingItemNotUsefulWhenEquippedBaselineDominates() {
        Equip blueCalas    = mageOverall(/*int*/ 7,  /*luk*/ 0);
        Equip betterOverall = mageOverall(/*int*/ 10, /*luk*/ 0);

        Set<Equip> useful = BotEquipManager.selectItemsBeatingBaseline(
                BotEquipManager.relevantStatsFor(Job.MAGICIAN),
                List.of(blueCalas), List.of(betterOverall));

        assertFalse(useful.contains(blueCalas),
                "item should not be reserved when mage sibling already wears a strictly better overall");
    }

    @Test
    void shouldReserveUsefulHunterShoesForSelfFromJohnLog() {
        Character bot = mock(Character.class);
        when(bot.getJob()).thenReturn(Job.HUNTER);
        when(bot.getInventory(InventoryType.EQUIPPED)).thenReturn(mock(Inventory.class));

        Inventory equipped = bot.getInventory(InventoryType.EQUIPPED);
        Equip wornBoots = mock(Equip.class);
        Equip redPierre = mock(Equip.class);
        when(equipped.list()).thenReturn(List.of(wornBoots));

        when(wornBoots.getItemId()).thenReturn(1072082);
        when(wornBoots.getStr()).thenReturn((short) 0);
        when(wornBoots.getDex()).thenReturn((short) 5);
        when(wornBoots.getInt()).thenReturn((short) 0);
        when(wornBoots.getLuk()).thenReturn((short) 0);
        when(wornBoots.getWatk()).thenReturn((short) 0);
        when(wornBoots.getMatk()).thenReturn((short) 0);
        when(wornBoots.getAcc()).thenReturn((short) 0);

        when(redPierre.getItemId()).thenReturn(1072132);
        when(redPierre.getStr()).thenReturn((short) 8);
        when(redPierre.getDex()).thenReturn((short) 0);
        when(redPierre.getInt()).thenReturn((short) 0);
        when(redPierre.getLuk()).thenReturn((short) 0);
        when(redPierre.getWatk()).thenReturn((short) 0);
        when(redPierre.getMatk()).thenReturn((short) 0);
        when(redPierre.getAcc()).thenReturn((short) 0);

        BotEquipManager.EquipUsefulnessHooks hooks = mock(BotEquipManager.EquipUsefulnessHooks.class);
        when(hooks.isCash(1072082)).thenReturn(false);
        when(hooks.isCash(1072132)).thenReturn(false);
        when(hooks.getEquipmentSlot(1072082)).thenReturn("So");
        when(hooks.getEquipmentSlot(1072132)).thenReturn("So");
        when(hooks.meetsReqs(wornBoots, Job.HUNTER, Short.MAX_VALUE,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4, Short.MAX_VALUE)).thenReturn(true);
        when(hooks.meetsReqs(redPierre, Job.HUNTER, Short.MAX_VALUE,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4, Short.MAX_VALUE)).thenReturn(true);
        assertTrue(BotEquipManager.shouldReserveOwnedItem(bot, hooks, redPierre),
                "John's +8 STR Red Pierre Shoes should stay reserved for self ahead of owner/sibling offers");
    }

    @Test
    void bagGloveNotReservedWhenEquippedGloveAlreadyDominatesJohnCase() {
        Character bot = mock(Character.class);
        when(bot.getJob()).thenReturn(Job.HUNTER);

        Equip equippedBlueWork = equipWithIdStats(1082001, 0, 7, 0);
        Equip bagMithrilScaler = equipWithIdStats(1082047, 0, 2, 0);

        BotEquipManager.SelfReserveHooks hooks = mock(BotEquipManager.SelfReserveHooks.class);
        stubReserveItem(hooks, Job.HUNTER, equippedBlueWork, "Gv", 10, 0, 0, 0, 0, 0, 0);
        stubReserveItem(hooks, Job.HUNTER, bagMithrilScaler, "Gv", 35, 4, 0, 115, 0, 0, 0);

        Set<Equip> keep = BotEquipManager.selectOwnedItemsForSelfReserve(bot, hooks,
                List.of(equippedBlueWork, bagMithrilScaler));

        assertTrue(keep.contains(equippedBlueWork), "equipped baseline glove should stay in the reserve set");
        assertFalse(keep.contains(bagMithrilScaler),
                "bag glove should not be reserved when equipped glove already dominates it");
    }

    @Test
    void shouldReserveSwordUpgradeEvenWhenEquippedAxeIsStronger() {
        Character bot = mock(Character.class);
        when(bot.getJob()).thenReturn(Job.FIGHTER);
        when(bot.getSkillLevel(Fighter.SWORD_MASTERY)).thenReturn(1);
        when(bot.getSkillLevel(Fighter.SWORD_BOOSTER)).thenReturn(0);
        when(bot.getSkillLevel(Fighter.AXE_MASTERY)).thenReturn(0);
        when(bot.getSkillLevel(Fighter.AXE_BOOSTER)).thenReturn(0);
        when(bot.getInventory(InventoryType.EQUIPPED)).thenReturn(mock(Inventory.class));

        Inventory equipped = bot.getInventory(InventoryType.EQUIPPED);
        Equip equippedAxe = equipWithIdStats(1300001, 12, 0, 0);
        Equip candidateSword = equipWithIdStats(1300002, 8, 0, 0);
        when(equipped.list()).thenReturn(List.of(equippedAxe));

        BotEquipManager.EquipUsefulnessHooks hooks = mock(BotEquipManager.EquipUsefulnessHooks.class);
        when(hooks.isCash(1300001)).thenReturn(false);
        when(hooks.isCash(1300002)).thenReturn(false);
        when(hooks.getEquipmentSlot(1300001)).thenReturn("Wp");
        when(hooks.getEquipmentSlot(1300002)).thenReturn("Wp");
        when(hooks.getWeaponType(1300001)).thenReturn(WeaponType.GENERAL1H_SWING);
        when(hooks.getWeaponType(1300002)).thenReturn(WeaponType.SWORD1H);
        when(hooks.meetsReqs(equippedAxe, Job.FIGHTER, Short.MAX_VALUE,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4, Short.MAX_VALUE)).thenReturn(true);
        when(hooks.meetsReqs(candidateSword, Job.FIGHTER, Short.MAX_VALUE,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4, Short.MAX_VALUE)).thenReturn(true);

        assertTrue(BotEquipManager.shouldReserveOwnedItem(bot, hooks, candidateSword),
                "equipped axe should not block a sword-spec fighter from reserving a sword");
    }

    @Test
    void shouldReserveSwordAndAxeWhenBuildHasBothMasteries() {
        Character bot = mock(Character.class);
        when(bot.getJob()).thenReturn(Job.FIGHTER);
        when(bot.getSkillLevel(Fighter.SWORD_MASTERY)).thenReturn(1);
        when(bot.getSkillLevel(Fighter.SWORD_BOOSTER)).thenReturn(0);
        when(bot.getSkillLevel(Fighter.AXE_MASTERY)).thenReturn(1);
        when(bot.getSkillLevel(Fighter.AXE_BOOSTER)).thenReturn(0);
        when(bot.getInventory(InventoryType.EQUIPPED)).thenReturn(mock(Inventory.class));

        Inventory equipped = bot.getInventory(InventoryType.EQUIPPED);
        Equip equippedSword = equipWithIdStats(1400001, 10, 0, 0);
        Equip candidateAxe = equipWithIdStats(1300001, 8, 0, 0);
        when(equipped.list()).thenReturn(List.of(equippedSword));

        BotEquipManager.EquipUsefulnessHooks hooks = mock(BotEquipManager.EquipUsefulnessHooks.class);
        when(hooks.isCash(1400001)).thenReturn(false);
        when(hooks.isCash(1300001)).thenReturn(false);
        when(hooks.getEquipmentSlot(1400001)).thenReturn("Wp");
        when(hooks.getEquipmentSlot(1300001)).thenReturn("Wp");
        when(hooks.getWeaponType(1400001)).thenReturn(WeaponType.SWORD1H);
        when(hooks.getWeaponType(1300001)).thenReturn(WeaponType.GENERAL1H_SWING);
        when(hooks.meetsReqs(equippedSword, Job.FIGHTER, Short.MAX_VALUE,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4, Short.MAX_VALUE)).thenReturn(true);
        when(hooks.meetsReqs(candidateAxe, Job.FIGHTER, Short.MAX_VALUE,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4, Short.MAX_VALUE)).thenReturn(true);

        assertTrue(BotEquipManager.shouldReserveOwnedItem(bot, hooks, equippedSword),
                "dual-mastery fighter should keep sword family");
        assertTrue(BotEquipManager.shouldReserveOwnedItem(bot, hooks, candidateAxe),
                "dual-mastery fighter should also keep axe family");
    }

    @Test
    void selfReserveSameReqSameItemIdKeepsOnlyBestIvoryCopies() {
        Character bot = mock(Character.class);
        when(bot.getJob()).thenReturn(Job.SPEARMAN);

        Equip ivoryTop8 = equipWithIdStats(1040087, 0, 8, 0);
        Equip ivoryTop7 = equipWithIdStats(1040087, 0, 7, 0);
        Equip ivoryTop3 = equipWithIdStats(1040087, 0, 3, 0);
        Equip ivoryPant6 = equipWithIdStats(1060076, 0, 6, 4);
        Equip ivoryPant2 = equipWithIdStats(1060076, 0, 2, 0);
        Equip ivoryPant1 = equipWithIdStats(1060076, 0, 1, 2);

        BotEquipManager.SelfReserveHooks hooks = mock(BotEquipManager.SelfReserveHooks.class);
        stubReserveItem(hooks, Job.SPEARMAN, ivoryTop8, "Ma", 50, 1, 180, 0, 0, 0, 20);
        stubReserveItem(hooks, Job.SPEARMAN, ivoryTop7, "Ma", 50, 1, 180, 0, 0, 0, 20);
        stubReserveItem(hooks, Job.SPEARMAN, ivoryTop3, "Ma", 50, 1, 180, 0, 0, 0, 20);
        stubReserveItem(hooks, Job.SPEARMAN, ivoryPant6, "Pn", 50, 1, 180, 0, 0, 0, 20);
        stubReserveItem(hooks, Job.SPEARMAN, ivoryPant2, "Pn", 50, 1, 180, 0, 0, 0, 20);
        stubReserveItem(hooks, Job.SPEARMAN, ivoryPant1, "Pn", 50, 1, 180, 0, 0, 0, 20);

        Set<Equip> keep = BotEquipManager.selectOwnedItemsForSelfReserve(bot, hooks,
                List.of(ivoryTop8, ivoryTop7, ivoryTop3, ivoryPant6, ivoryPant2, ivoryPant1));

        assertTrue(keep.contains(ivoryTop8));
        assertFalse(keep.contains(ivoryTop7));
        assertFalse(keep.contains(ivoryTop3));
        assertTrue(keep.contains(ivoryPant6));
        assertFalse(keep.contains(ivoryPant2));
        assertFalse(keep.contains(ivoryPant1));
    }

    @Test
    void selfReserveSameReqSameRelevantStatsKeepsBestDuplicateByFullStats() {
        Character bot = mock(Character.class);
        when(bot.getJob()).thenReturn(Job.ASSASSIN);
        when(bot.getSkillLevel(Assassin.CLAW_MASTERY)).thenReturn(1);
        when(bot.getSkillLevel(Assassin.CLAW_BOOSTER)).thenReturn(0);

        Equip bronzeWdef0 = clawWithStats(1472010, 2, 21, 0);
        Equip bronzeWdef1 = clawWithStats(1472010, 2, 21, 1);
        Equip bronzeWdef2 = clawWithStats(1472010, 2, 21, 2);

        BotEquipManager.SelfReserveHooks hooks = mock(BotEquipManager.SelfReserveHooks.class);
        stubReserveItem(hooks, Job.ASSASSIN, bronzeWdef0, "Wp", 35, 8, 0, 70, 0, 95, 0);
        stubReserveItem(hooks, Job.ASSASSIN, bronzeWdef1, "Wp", 35, 8, 0, 70, 0, 95, 0);
        stubReserveItem(hooks, Job.ASSASSIN, bronzeWdef2, "Wp", 35, 8, 0, 70, 0, 95, 0);
        when(hooks.getWeaponType(1472010)).thenReturn(WeaponType.CLAW);

        Set<Equip> keep = BotEquipManager.selectOwnedItemsForSelfReserve(bot, hooks,
                List.of(bronzeWdef0, bronzeWdef1, bronzeWdef2));

        assertFalse(keep.contains(bronzeWdef0));
        assertFalse(keep.contains(bronzeWdef1));
        assertTrue(keep.contains(bronzeWdef2),
                "same-id same-req duplicate with equal LUK/WATK should keep only best full-stat copy");
    }

    @Test
    void selfReserveSameReqDifferentItemIdDoesNotDominate() {
        Character bot = mock(Character.class);
        when(bot.getJob()).thenReturn(Job.SPEARMAN);

        Equip itemA = equipWithIdStats(2000001, 0, 8, 0);
        Equip itemB = equipWithIdStats(2000002, 0, 7, 0);

        BotEquipManager.SelfReserveHooks hooks = mock(BotEquipManager.SelfReserveHooks.class);
        stubReserveItem(hooks, Job.SPEARMAN, itemA, "Ma", 50, 1, 180, 0, 0, 0, 20);
        stubReserveItem(hooks, Job.SPEARMAN, itemB, "Ma", 50, 1, 180, 0, 0, 0, 20);

        Set<Equip> keep = BotEquipManager.selectOwnedItemsForSelfReserve(bot, hooks, List.of(itemA, itemB));

        assertTrue(keep.contains(itemA));
        assertTrue(keep.contains(itemB),
                "same req signature but different itemId should not dominate");
    }

    @Test
    void selfReserveRejectsStrictlyWorseHigherReqBowmanGlove() {
        Character bot = mock(Character.class);
        when(bot.getJob()).thenReturn(Job.HUNTER);

        Equip currentAllClassGlove = equipWithIdStats(1082000, 0, 8, 0);
        Equip worseBowmanGlove = equipWithIdStats(1082100, 0, 2, 0);

        BotEquipManager.SelfReserveHooks hooks = mock(BotEquipManager.SelfReserveHooks.class);
        stubReserveItem(hooks, Job.HUNTER, currentAllClassGlove, "Gv", 10, 0, 0, 0, 0, 0, 0);
        stubReserveItem(hooks, Job.HUNTER, worseBowmanGlove, "Gv", 35, 4, 0, 0, 0, 0, 0);

        Set<Equip> keep = BotEquipManager.selectOwnedItemsForSelfReserve(bot, hooks,
                List.of(currentAllClassGlove, worseBowmanGlove));

        assertTrue(keep.contains(currentAllClassGlove));
        assertFalse(keep.contains(worseBowmanGlove),
                "proactive future pool should not keep a strictly worse glove with higher level/job reqs");
    }

    @Test
    void statOnlyBlockedAllowsMissingStatsButRejectsLevelOrFameBlockedItems() {
        Character bot = mock(Character.class);
        when(bot.getJob()).thenReturn(Job.ASSASSIN);
        when(bot.getLevel()).thenReturn(43);
        when(bot.getFame()).thenReturn(1);

        Equip statBlocked = mock(Equip.class);
        Equip levelBlocked = mock(Equip.class);
        BotEquipManager.EquipUsefulnessHooks hooks = mock(BotEquipManager.EquipUsefulnessHooks.class);

        when(hooks.meetsReqs(statBlocked, Job.ASSASSIN, 43,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4, 1)).thenReturn(true);
        when(hooks.meetsReqs(levelBlocked, Job.ASSASSIN, 43,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4, 1)).thenReturn(false);

        assertTrue(BotEquipManager.statOnlyBlocked(bot, hooks, statBlocked),
                "immediate optimizer may consider gear that only needs more stats");
        assertFalse(BotEquipManager.statOnlyBlocked(bot, hooks, levelBlocked),
                "immediate optimizer must skip gear blocked by current level/job/fame");
    }

    private static Equip mageOverall(int int_, int luk) {
        Equip e = mock(Equip.class);
        when(e.getStr()).thenReturn((short) 0);
        when(e.getDex()).thenReturn((short) 0);
        when(e.getInt()).thenReturn((short) int_);
        when(e.getLuk()).thenReturn((short) luk);
        when(e.getWatk()).thenReturn((short) 0);
        when(e.getMatk()).thenReturn((short) 0);
        when(e.getAcc()).thenReturn((short) 0);
        return e;
    }

    private static Equip clawWithStats(int itemId, int luk, int watk, int wdef) {
        Equip e = mock(Equip.class);
        when(e.getItemId()).thenReturn(itemId);
        when(e.getStr()).thenReturn((short) 0);
        when(e.getDex()).thenReturn((short) 0);
        when(e.getInt()).thenReturn((short) 0);
        when(e.getLuk()).thenReturn((short) luk);
        when(e.getWatk()).thenReturn((short) watk);
        when(e.getMatk()).thenReturn((short) 0);
        when(e.getWdef()).thenReturn((short) wdef);
        when(e.getMdef()).thenReturn((short) 0);
        when(e.getAcc()).thenReturn((short) 0);
        when(e.getAvoid()).thenReturn((short) 0);
        when(e.getHp()).thenReturn((short) 0);
        when(e.getMp()).thenReturn((short) 0);
        when(e.getSpeed()).thenReturn((short) 0);
        when(e.getJump()).thenReturn((short) 0);
        return e;
    }

    private static Equip equipWithIdStats(int itemId, int str, int dex, int acc) {
        Equip e = mock(Equip.class);
        when(e.getItemId()).thenReturn(itemId);
        when(e.getStr()).thenReturn((short) str);
        when(e.getDex()).thenReturn((short) dex);
        when(e.getInt()).thenReturn((short) 0);
        when(e.getLuk()).thenReturn((short) 0);
        when(e.getWatk()).thenReturn((short) 0);
        when(e.getMatk()).thenReturn((short) 0);
        when(e.getAcc()).thenReturn((short) acc);
        return e;
    }

    private static void stubReserveItem(BotEquipManager.SelfReserveHooks hooks, Job job, Equip equip, String slot,
                                        int reqLevel, int reqJob, int reqStr, int reqDex,
                                        int reqInt, int reqLuk, int reqPop) {
        int itemId = equip.getItemId();
        when(hooks.isCash(itemId)).thenReturn(false);
        when(hooks.getEquipmentSlot(itemId)).thenReturn(slot);
        when(hooks.meetsReqs(equip, job, Short.MAX_VALUE,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4, Short.MAX_VALUE)).thenReturn(true);
        when(hooks.getEquipLevelReq(itemId)).thenReturn(reqLevel);
        when(hooks.getEquipStats(itemId)).thenReturn(java.util.Map.of(
                "reqJob", reqJob,
                "reqSTR", reqStr,
                "reqDEX", reqDex,
                "reqINT", reqInt,
                "reqLUK", reqLuk,
                "reqPOP", reqPop));
    }

    private static Set<Equip> identitySet(Equip... items) {
        Set<Equip> set = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Equip e : items) set.add(e);
        return set;
    }
}
