package server.agents.field;

import client.Character;
import client.Job;
import constants.skills.Assassin;
import constants.skills.Bandit;
import constants.skills.Brawler;
import constants.skills.Cleric;
import constants.skills.Crossbowman;
import constants.skills.Fighter;
import constants.skills.FPWizard;
import constants.skills.Gunslinger;
import constants.skills.Hunter;
import constants.skills.ILWizard;
import constants.skills.Page;
import constants.skills.Spearman;
import server.agents.capabilities.build.AgentBuildService;
import server.agents.capabilities.build.profiles.BuildStep;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.cosmic.CosmicMapleIslandCohortIdentity;
import server.agents.plans.mapleisland.cohort.MapleIslandCohortCharacterCatalog;
import server.agents.runtime.AgentRuntimeEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.Set;

/** WZ-verified level-60 second-job weapon pool for Easy Balrog expeditions. */
public final class AgentBalrogTestFixtureService {
    public static final int LEVEL = 60;
    public static final int MINIMUM_WEAPON_LEVEL = 40;
    public static final int ROSTER_SIZE = 12;
    public static final Set<Integer> BALROG_COMBAT_MOBS =
            Set.of(8830007, 8830008, 8830009, 6400008, 6400009);

    private static final List<List<Integer>> WARRIOR_MALE_CLOTHING = List.of(
            List.of(1_002_029, 1_040_090, 1_060_079),
            List.of(1_002_084, 1_040_091, 1_060_080),
            List.of(1_002_022, 1_040_092, 1_060_081));
    private static final List<List<Integer>> WARRIOR_FEMALE_CLOTHING = List.of(
            List.of(1_002_029, 1_041_091, 1_061_090),
            List.of(1_002_084, 1_041_092, 1_061_091),
            List.of(1_002_022, 1_041_093, 1_061_092));
    private static final List<List<Integer>> MAGICIAN_MALE_CLOTHING = List.of(
            List.of(1_002_242, 1_050_053),
            List.of(1_002_243, 1_050_054),
            List.of(1_002_244, 1_050_055));
    private static final List<List<Integer>> MAGICIAN_FEMALE_CLOTHING = List.of(
            List.of(1_002_242, 1_051_044),
            List.of(1_002_243, 1_051_045),
            List.of(1_002_244, 1_051_046));
    private static final List<List<Integer>> BOWMAN_MALE_CLOTHING = List.of(
            List.of(1_002_267, 1_050_058),
            List.of(1_002_268, 1_050_059));
    private static final List<List<Integer>> BOWMAN_FEMALE_CLOTHING = List.of(
            List.of(1_002_267, 1_051_041),
            List.of(1_002_268, 1_051_042));
    private static final List<List<Integer>> THIEF_MALE_CLOTHING = List.of(
            List.of(1_002_247, 1_040_098, 1_060_087),
            List.of(1_002_248, 1_040_099, 1_060_088));
    private static final List<List<Integer>> THIEF_FEMALE_CLOTHING = List.of(
            List.of(1_002_247, 1_041_094, 1_061_093),
            List.of(1_002_248, 1_041_095, 1_061_094));
    private static final List<List<Integer>> PIRATE_CLOTHING = List.of(
            List.of(1_002_634, 1_052_119),
            List.of(1_002_631, 1_052_116));

    private static final List<Integer> WARRIOR_ACCESSORIES = List.of(1_082_059, 1_072_147);
    private static final List<Integer> MAGICIAN_ACCESSORIES = List.of(1_082_086, 1_072_136);
    private static final List<Integer> BOWMAN_ACCESSORIES = List.of(1_082_089, 1_072_144);
    private static final List<Integer> THIEF_ACCESSORIES = List.of(1_082_092, 1_072_150);
    private static final List<Integer> PIRATE_ACCESSORIES = List.of(1_082_201, 1_072_306);

    public static final List<Build> ALL_BUILDS = List.of(
            warrior("fighter-1h-sword", Job.FIGHTER, WeaponClass.ONE_HANDED_SWORD,
                    1_302_053, 1_092_009, fighterSword()),
            warrior("fighter-2h-sword", Job.FIGHTER, WeaponClass.TWO_HANDED_SWORD,
                    1_402_011, 0, fighterSword()),
            warrior("fighter-1h-axe", Job.FIGHTER, WeaponClass.ONE_HANDED_AXE,
                    1_312_009, 1_092_009, fighterAxe()),
            warrior("fighter-2h-axe", Job.FIGHTER, WeaponClass.TWO_HANDED_AXE,
                    1_412_007, 0, fighterAxe()),
            warrior("page-1h-blunt", Job.PAGE, WeaponClass.ONE_HANDED_BLUNT,
                    1_322_018, 1_092_009, pageBlunt()),
            warrior("page-2h-blunt", Job.PAGE, WeaponClass.TWO_HANDED_BLUNT,
                    1_422_009, 0, pageBlunt()),
            warrior("spearman-spear", Job.SPEARMAN, WeaponClass.SPEAR,
                    1_432_006, 0, spearmanSpear()),
            warrior("spearman-polearm", Job.SPEARMAN, WeaponClass.POLEARM,
                    1_442_010, 0, spearmanPolearm()),
            magician("fp-wizard-wand", Job.FP_WIZARD, WeaponClass.WAND,
                    1_372_008, 1_092_029, fpWizard()),
            magician("il-wizard-staff", Job.IL_WIZARD, WeaponClass.STAFF,
                    1_382_006, 0, ilWizard()),
            magician("cleric-wand", Job.CLERIC, WeaponClass.WAND,
                    1_372_008, 1_092_029, cleric()),
            magician("cleric-staff", Job.CLERIC, WeaponClass.STAFF,
                    1_382_006, 0, cleric()),
            bowman("hunter-bow", Job.HUNTER, WeaponClass.BOW, 1_452_004, 65, hunter()),
            bowman("crossbowman-crossbow", Job.CROSSBOWMAN, WeaponClass.CROSSBOW,
                    1_462_008, 60, crossbowman()),
            thief("assassin-claw", "thief-claw", Job.ASSASSIN, WeaponClass.CLAW,
                    1_472_022, assassin()),
            thief("bandit-dagger", "thief-dagger", Job.BANDIT, WeaponClass.DAGGER,
                    1_332_015, bandit()),
            pirate("brawler-knuckle", "pirate-knuckle", Job.BRAWLER, WeaponClass.KNUCKLE,
                    1_482_008, AgentBuildService.StatType.STR, AgentBuildService.StatType.DEX,
                    brawler()),
            pirate("gunslinger-gun", "pirate-gun", Job.GUNSLINGER, WeaponClass.GUN,
                    1_492_008, AgentBuildService.StatType.DEX, AgentBuildService.StatType.STR,
                    gunslinger()));

    private AgentBalrogTestFixtureService() {
    }

    public static List<Build> selectRoster(long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        ArrayList<Build> roster = new ArrayList<>(ROSTER_SIZE);
        for (Job job : List.of(Job.FIGHTER, Job.PAGE, Job.SPEARMAN,
                Job.FP_WIZARD, Job.IL_WIZARD, Job.CLERIC,
                Job.HUNTER, Job.CROSSBOWMAN, Job.ASSASSIN, Job.BANDIT,
                Job.BRAWLER, Job.GUNSLINGER)) {
            List<Build> candidates = ALL_BUILDS.stream()
                    .filter(build -> build.job() == job).toList();
            if (candidates.isEmpty()) {
                throw new IllegalStateException("missing Easy Balrog fixture for " + job);
            }
            roster.add(candidates.get(random.nextInt(candidates.size())));
        }
        return List.copyOf(roster);
    }

    public static PreparationResult prepare(
            AgentRuntimeEntry entry, Build build, long seed, long nowMs) throws IOException {
        return prepare(entry, build, 0, seed, nowMs);
    }

    public static PreparationResult prepare(
            AgentRuntimeEntry entry, Build build, int clothingRank, long seed, long nowMs)
            throws IOException {
        if (entry == null || build == null) {
            throw new IllegalArgumentException("an Agent runtime and Balrog build are required");
        }
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null) throw new IllegalArgumentException("a live Agent character is required");
        int appearance = (int) Math.floorMod(seed, MapleIslandCohortCharacterCatalog.COMBINATION_COUNT);
        CosmicMapleIslandCohortIdentity.apply(
                agent, MapleIslandCohortCharacterCatalog.template(appearance));
        AgentFieldObservationFixtureService.Prepared prepared =
                AgentFieldObservationFixtureService.prepareForBalrog(
                        entry, build, LEVEL, BALROG_COMBAT_MOBS, clothingRank, nowMs);
        if (!prepared.completeBuild()) {
            throw new IllegalStateException("Balrog fixture left unspent AP/SP for " + prepared.name()
                    + " ap=" + prepared.remainingAp() + " sp="
                    + Arrays.toString(prepared.remainingSps()));
        }
        return new PreparationResult(prepared.level(), prepared.minimumHitChance(), build.job(),
                build.weaponClass(), build.buildId(), prepared.completeBuild(), prepared.remainingAp(),
                prepared.remainingSps(), prepared.weaponItemId(), prepared.weaponAttack());
    }

    private static Build warrior(String id, Job job, WeaponClass weaponClass, int weapon,
                                 int shield, List<BuildStep> spBuild) {
        return new Build(id, "warrior", job, weaponClass, weapon, shield,
                new AgentBuildService.ApBuild(
                        AgentBuildService.StatType.STR, AgentBuildService.StatType.DEX, 60),
                spBuild);
    }

    private static Build magician(String id, Job job, WeaponClass weaponClass, int weapon,
                                  int shield, List<BuildStep> spBuild) {
        return new Build(id, "magician", job, weaponClass, weapon, shield,
                new AgentBuildService.ApBuild(
                        AgentBuildService.StatType.INT, AgentBuildService.StatType.LUK, 63),
                spBuild);
    }

    private static Build bowman(String id, Job job, WeaponClass weaponClass, int weapon,
                                int strengthTarget, List<BuildStep> spBuild) {
        return new Build(id, "bowman", job, weaponClass, weapon, 0,
                new AgentBuildService.ApBuild(
                        AgentBuildService.StatType.DEX, AgentBuildService.StatType.STR, strengthTarget),
                spBuild);
    }

    private static Build thief(String id, String career, Job job, WeaponClass weaponClass,
                               int weapon, List<BuildStep> spBuild) {
        return new Build(id, career, job, weaponClass, weapon, 0,
                new AgentBuildService.ApBuild(
                        AgentBuildService.StatType.LUK, AgentBuildService.StatType.DEX, 100),
                spBuild);
    }

    private static Build pirate(String id, String career, Job job, WeaponClass weaponClass, int weapon,
                                AgentBuildService.StatType primary, AgentBuildService.StatType secondary,
                                List<BuildStep> spBuild) {
        return new Build(id, career, job, weaponClass, weapon, 0,
                new AgentBuildService.ApBuild(primary, secondary, 60), spBuild);
    }

    private static List<BuildStep> fighterSword() {
        return fighter(Fighter.SWORD_MASTERY, Fighter.SWORD_BOOSTER, Fighter.FINAL_ATTACK_SWORD);
    }

    private static List<BuildStep> fighterAxe() {
        return fighter(Fighter.AXE_MASTERY, Fighter.AXE_BOOSTER, Fighter.FINAL_ATTACK_AXE);
    }

    private static List<BuildStep> fighter(int mastery, int booster, int finalAttack) {
        return List.of(s(mastery, 20), s(booster, 20), s(Fighter.RAGE, 3),
                s(Fighter.POWER_GUARD, 30), s(Fighter.RAGE, 20), s(finalAttack, 1));
    }

    private static List<BuildStep> pageBlunt() {
        return List.of(s(Page.BW_MASTERY, 20), s(Page.BW_BOOSTER, 20), s(Page.THREATEN, 3),
                s(Page.POWER_GUARD, 30), s(Page.THREATEN, 20), s(Page.FINAL_ATTACK_BW, 1));
    }

    private static List<BuildStep> spearmanSpear() {
        return spearman(Spearman.SPEAR_MASTERY, Spearman.SPEAR_BOOSTER,
                Spearman.FINAL_ATTACK_SPEAR);
    }

    private static List<BuildStep> spearmanPolearm() {
        return spearman(Spearman.POLEARM_MASTERY, Spearman.POLEARM_BOOSTER,
                Spearman.FINAL_ATTACK_POLEARM);
    }

    private static List<BuildStep> spearman(int mastery, int booster, int finalAttack) {
        return List.of(s(mastery, 20), s(booster, 20), s(Spearman.IRON_WILL, 3),
                s(Spearman.HYPER_BODY, 30), s(finalAttack, 18));
    }

    private static List<BuildStep> cleric() {
        return List.of(s(Cleric.HEAL, 30), s(Cleric.TELEPORT, 20), s(Cleric.INVINCIBLE, 5),
                s(Cleric.BLESS, 20), s(Cleric.MP_EATER, 16));
    }

    private static List<BuildStep> fpWizard() {
        return List.of(s(FPWizard.MP_EATER, 20), s(FPWizard.MEDITATION, 20),
                s(FPWizard.TELEPORT, 20), s(FPWizard.FIRE_ARROW, 30), s(FPWizard.SLOW, 1));
    }

    private static List<BuildStep> ilWizard() {
        return List.of(s(ILWizard.MP_EATER, 20), s(ILWizard.MEDITATION, 20),
                s(ILWizard.TELEPORT, 20), s(ILWizard.COLD_BEAM, 30), s(ILWizard.SLOW, 1));
    }

    private static List<BuildStep> hunter() {
        return List.of(s(Hunter.BOW_MASTERY, 20), s(Hunter.BOW_BOOSTER, 20),
                s(Hunter.SOUL_ARROW, 20), s(Hunter.ARROW_BOMB, 30), s(Hunter.POWER_KNOCKBACK, 1));
    }

    private static List<BuildStep> crossbowman() {
        return List.of(s(Crossbowman.CROSSBOW_MASTERY, 20), s(Crossbowman.CROSSBOW_BOOSTER, 20),
                s(Crossbowman.SOUL_ARROW, 20), s(Crossbowman.IRON_ARROW, 30),
                s(Crossbowman.POWER_KNOCKBACK, 1));
    }

    private static List<BuildStep> assassin() {
        return List.of(s(Assassin.CLAW_MASTERY, 20), s(Assassin.CRITICAL_THROW, 30),
                s(Assassin.CLAW_BOOSTER, 20), s(Assassin.HASTE, 20), s(Assassin.ENDURE, 1));
    }

    private static List<BuildStep> bandit() {
        return List.of(s(Bandit.DAGGER_MASTERY, 20), s(Bandit.DAGGER_BOOSTER, 20),
                s(Bandit.HASTE, 20), s(Bandit.SAVAGE_BLOW, 30), s(Bandit.ENDURE, 1));
    }

    private static List<BuildStep> brawler() {
        return List.of(s(Brawler.IMPROVE_MAX_HP, 10), s(Brawler.KNUCKLER_MASTERY, 20),
                s(Brawler.KNUCKLER_BOOSTER, 20), s(Brawler.CORKSCREW_BLOW, 20),
                s(Brawler.BACK_SPIN_BLOW, 20), s(Brawler.DOUBLE_UPPERCUT, 1));
    }

    private static List<BuildStep> gunslinger() {
        return List.of(s(Gunslinger.GUN_MASTERY, 20), s(Gunslinger.GUN_BOOSTER, 20),
                s(Gunslinger.INVISIBLE_SHOT, 20), s(Gunslinger.GRENADE, 20),
                s(Gunslinger.RECOIL_SHOT, 11));
    }

    private static BuildStep s(int skillId, int level) {
        return new BuildStep(skillId, level);
    }

    public static int clothingRank(List<Build> roster, int ordinal) {
        if (roster == null || ordinal < 0 || ordinal >= roster.size()) {
            throw new IllegalArgumentException("a valid Balrog roster slot is required");
        }
        int family = roster.get(ordinal).job().getId() / 100;
        int rank = 0;
        for (int index = 0; index < ordinal; index++) {
            if (roster.get(index).job().getId() / 100 == family) rank++;
        }
        return rank;
    }

    private static List<Integer> armor(Job job, int gender, int clothingRank) {
        List<List<Integer>> clothing;
        List<Integer> accessories;
        if (job == Job.FIGHTER || job == Job.PAGE || job == Job.SPEARMAN) {
            clothing = gender == 0 ? WARRIOR_MALE_CLOTHING : WARRIOR_FEMALE_CLOTHING;
            accessories = WARRIOR_ACCESSORIES;
        } else if (job == Job.FP_WIZARD || job == Job.IL_WIZARD || job == Job.CLERIC) {
            clothing = gender == 0 ? MAGICIAN_MALE_CLOTHING : MAGICIAN_FEMALE_CLOTHING;
            accessories = MAGICIAN_ACCESSORIES;
        } else if (job == Job.HUNTER || job == Job.CROSSBOWMAN) {
            clothing = gender == 0 ? BOWMAN_MALE_CLOTHING : BOWMAN_FEMALE_CLOTHING;
            accessories = BOWMAN_ACCESSORIES;
        } else if (job == Job.ASSASSIN || job == Job.BANDIT) {
            clothing = gender == 0 ? THIEF_MALE_CLOTHING : THIEF_FEMALE_CLOTHING;
            accessories = THIEF_ACCESSORIES;
        } else {
            clothing = PIRATE_CLOTHING;
            accessories = PIRATE_ACCESSORIES;
        }
        if (clothingRank < 0 || clothingRank >= clothing.size()) {
            throw new IllegalArgumentException("no distinct Balrog clothing set for " + job
                    + " at family rank " + clothingRank);
        }
        ArrayList<Integer> armor = new ArrayList<>(clothing.get(clothingRank));
        armor.addAll(accessories);
        return List.copyOf(armor);
    }

    public enum WeaponClass {
        ONE_HANDED_SWORD,
        TWO_HANDED_SWORD,
        ONE_HANDED_AXE,
        TWO_HANDED_AXE,
        ONE_HANDED_BLUNT,
        TWO_HANDED_BLUNT,
        SPEAR,
        POLEARM,
        WAND,
        STAFF,
        BOW,
        CROSSBOW,
        CLAW,
        DAGGER,
        KNUCKLE,
        GUN
    }

    public record Build(String buildId, String career, Job job, WeaponClass weaponClass,
                        int weaponItemId, int shieldItemId, AgentBuildService.ApBuild apBuild,
                        List<BuildStep> spBuild) {
        public Build {
            if (buildId == null || buildId.isBlank() || career == null || career.isBlank()
                    || job == null || weaponClass == null || weaponItemId <= 0 || shieldItemId < 0
                    || apBuild == null || spBuild == null || spBuild.isEmpty()) {
                throw new IllegalArgumentException("a complete level-60 weapon build is required");
            }
            spBuild = List.copyOf(spBuild);
        }

        public List<Integer> equipment(int gender) {
            return equipment(gender, 0);
        }

        public List<Integer> equipment(int gender, int clothingRank) {
            ArrayList<Integer> items = new ArrayList<>(armor(job, gender, clothingRank));
            if (shieldItemId > 0) items.add(shieldItemId);
            items.add(weaponItemId);
            return List.copyOf(items);
        }
    }

    public record PreparationResult(int level, double minimumHitChance, Job job,
                                    WeaponClass weaponClass, String buildId, boolean completeBuild,
                                    int remainingAp, int[] remainingSps,
                                    int weaponItemId, int weaponAttack) {
        public PreparationResult {
            remainingSps = remainingSps == null ? new int[0] : remainingSps.clone();
        }
    }
}
