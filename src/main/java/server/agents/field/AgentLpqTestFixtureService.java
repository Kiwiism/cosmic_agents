package server.agents.field;

import client.Character;
import client.Job;
import constants.skills.ILWizard;
import server.agents.capabilities.build.AgentBuildService;
import server.agents.capabilities.build.profiles.BuildStep;
import server.agents.capabilities.partyquest.lpq.AgentLpqSession;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Legal level-45 LPQ observation builds with deterministic role coverage. */
public final class AgentLpqTestFixtureService {
    private static final int LPQ_START_LEVEL = config.AgentTuning.intValue(
            "server.agents.field.AgentLpqTestFixtureService.LPQ_START_LEVEL");
    static final List<String> BUILD_IDS = List.of(
            "cleric-wand", "il-wizard-wand", "bandit-dagger", "assassin-claw",
            "crossbowman-crossbow", "spearman-spear");
    private static final List<String> TELEPORT_HUMAN_BUILD_IDS = List.of(
            "cleric-wand", "bandit-dagger", "assassin-claw",
            "crossbowman-crossbow", "spearman-spear");
    private static final List<String> DARK_SIGHT_HUMAN_BUILD_IDS = List.of(
            "cleric-wand", "il-wizard-wand", "assassin-claw",
            "crossbowman-crossbow", "spearman-spear");
    private static final int EARRING_ITEM_ID = 1_032_075;
    private static final int CAPE_ITEM_ID = 1_102_055;
    private static final int SHOES_SPEED_SCROLL_10_ITEM_ID = 2_040_708;
    private static final int ROOM_MARKER_MESO_RESERVE = config.AgentTuning.intValue(
            "server.agents.field.AgentLpqTestFixtureService.ROOM_MARKER_MESO_RESERVE");
    private static final List<Integer> WARRIOR_MALE = List.of(
            1_002_098, 1_040_086, 1_060_074, 1_082_025, 1_072_127);
    private static final List<Integer> WARRIOR_FEMALE = List.of(
            1_002_098, 1_041_085, 1_061_084, 1_082_025, 1_072_127);
    private static final List<Integer> MAGICIAN_MALE = List.of(
            1_002_155, 1_050_036, 1_082_064, 1_072_117);
    private static final List<Integer> MAGICIAN_FEMALE = List.of(
            1_002_155, 1_051_024, 1_082_064, 1_072_117);
    private static final List<Integer> IL_MAGICIAN_MALE = List.of(
            1_002_151, 1_050_038, 1_082_064, 1_072_117);
    private static final List<Integer> IL_MAGICIAN_FEMALE = List.of(
            1_002_151, 1_051_025, 1_082_064, 1_072_117);
    private static final List<Integer> BOWMAN_MALE = List.of(
            1_002_992, 1_040_079, 1_060_069, 1_082_070, 1_072_401);
    private static final List<Integer> BOWMAN_FEMALE = List.of(
            1_002_992, 1_041_081, 1_061_080, 1_082_070, 1_072_401);
    private static final List<Integer> THIEF_MALE = List.of(
            1_002_185, 1_040_083, 1_060_072, 1_082_074, 1_072_107);
    private static final List<Integer> THIEF_FEMALE = List.of(
            1_002_185, 1_041_074, 1_061_069, 1_082_074, 1_072_107);
    private static final List<Integer> ASSASSIN_MALE = List.of(
            1_002_181, 1_040_082, 1_060_071, 1_082_074, 1_072_107);
    private static final List<Integer> ASSASSIN_FEMALE = List.of(
            1_002_181, 1_041_075, 1_061_070, 1_082_074, 1_072_107);
    private static final Set<Integer> LPQ_COMBAT_MOBS = Set.of(
            9_300_006, 9_300_007, 9_300_008, 9_300_010, 9_300_012, 9_300_014);
    static final Map<String, Loadout> LPQ_LOADOUTS = Map.of(
            "cleric-wand", new Loadout(1_372_046, 1_092_029, 2_043_701, 2_041_017,
                    MAGICIAN_MALE, MAGICIAN_FEMALE),
            "il-wizard-wand", new Loadout(1_372_046, 1_092_029, 2_043_701, 2_041_017,
                    IL_MAGICIAN_MALE, IL_MAGICIAN_FEMALE),
            "bandit-dagger", new Loadout(1_332_034, 1_092_018, 2_043_301, 2_041_023,
                    THIEF_MALE, THIEF_FEMALE),
            "assassin-claw", new Loadout(1_472_035, 0, 2_044_701, 2_041_023,
                    ASSASSIN_MALE, ASSASSIN_FEMALE),
            "crossbowman-crossbow", new Loadout(1_462_024, 0, 2_044_601, 2_041_020,
                    BOWMAN_MALE, BOWMAN_FEMALE),
            "spearman-spear", new Loadout(1_432_020, 0, 2_044_301, 2_041_014,
                    WARRIOR_MALE, WARRIOR_FEMALE));

    private AgentLpqTestFixtureService() { }

    public static PreparationResult prepare(AgentRuntimeEntry entry, int ordinal, long seed, long nowMs)
            throws IOException {
        return prepare(entry, BUILD_IDS.get(Math.floorMod(ordinal, BUILD_IDS.size())), seed, nowMs);
    }

    public static PreparationResult prepare(
            AgentRuntimeEntry entry, String buildId, long seed, long nowMs) throws IOException {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null) throw new IllegalArgumentException("a spawned LPQ Agent is required");
        AgentBalrogTestFixtureService.Build build = build(buildId);
        Loadout loadout = LPQ_LOADOUTS.get(buildId);
        build = new AgentBalrogTestFixtureService.Build(
                build.buildId(), build.career(), build.job(), build.weaponClass(),
                loadout.weaponItemId(), loadout.shieldItemId(), build.apBuild(), build.spBuild());
        applyAppearance(agent, seed);
        AgentFieldObservationFixtureService.Prepared prepared =
                AgentFieldObservationFixtureService.prepareForPartyQuest(
                        entry, build, LPQ_START_LEVEL, LPQ_COMBAT_MOBS,
                        loadout.equipment(agent.getGender()), loadout.weaponScrollItemId(),
                        SHOES_SPEED_SCROLL_10_ITEM_ID, loadout.capeScrollItemId(), nowMs);
        if (!prepared.completeBuild()) throw new IllegalStateException("LPQ fixture has unspent AP/SP");
        if (agent.getMeso() < ROOM_MARKER_MESO_RESERVE) {
            agent.gainMeso(ROOM_MARKER_MESO_RESERVE - agent.getMeso(), false, false, false);
            AgentCharacterGatewayRuntime.characters().save(agent, false);
        }
        return new PreparationResult(prepared.level(), prepared.career(),
                prepared.weaponItemId(), prepared.weaponAttack());
    }

    static List<String> mixedPartyBuildIds(AgentLpqSession.HumanRolePreference humanRole) {
        return switch (humanRole == null
                ? AgentLpqSession.HumanRolePreference.DEFAULT : humanRole) {
            case TELEPORT -> TELEPORT_HUMAN_BUILD_IDS;
            case DARK_SIGHT -> DARK_SIGHT_HUMAN_BUILD_IDS;
            default -> BUILD_IDS.subList(0, 5);
        };
    }

    public static String buildIdForTestParty(
            int ordinal, boolean includesHuman,
            AgentLpqSession.HumanRolePreference humanRole) {
        List<String> builds = includesHuman ? mixedPartyBuildIds(humanRole) : BUILD_IDS;
        return builds.get(Math.floorMod(ordinal, builds.size()));
    }

    static AgentLpqAppearanceCatalog.Appearance applyAppearance(Character agent, long seed) {
        AgentLpqAppearanceCatalog.Appearance appearance = AgentLpqAppearanceCatalog.select(seed);
        agent.setGender(appearance.gender());
        agent.setSkinColor(appearance.skinColor());
        agent.setHair(appearance.hairId());
        agent.setFace(appearance.faceId());
        return appearance;
    }

    static AgentBalrogTestFixtureService.Build build(String buildId) {
        if ("il-wizard-wand".equals(buildId)) {
            return new AgentBalrogTestFixtureService.Build(
                    buildId, "magician", Job.IL_WIZARD,
                    AgentBalrogTestFixtureService.WeaponClass.WAND, 1_372_046, 1_092_029,
                    new AgentBuildService.ApBuild(
                            AgentBuildService.StatType.INT, AgentBuildService.StatType.LUK, 63),
                    List.of(
                            new BuildStep(ILWizard.TELEPORT, 1),
                            new BuildStep(ILWizard.THUNDERBOLT, 30),
                            new BuildStep(ILWizard.MEDITATION, 20),
                            new BuildStep(ILWizard.MP_EATER, 20),
                            new BuildStep(ILWizard.TELEPORT, 20),
                            new BuildStep(ILWizard.COLD_BEAM, 30)));
        }
        return AgentBalrogTestFixtureService.ALL_BUILDS.stream()
                .filter(candidate -> candidate.buildId().equals(buildId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("missing LPQ fixture build " + buildId));
    }

    public record PreparationResult(int level, String career, int weaponItemId, int weaponAttack) { }

    record Loadout(int weaponItemId, int shieldItemId, int weaponScrollItemId,
                   int capeScrollItemId, List<Integer> maleArmor, List<Integer> femaleArmor) {
        Loadout {
            maleArmor = List.copyOf(maleArmor);
            femaleArmor = List.copyOf(femaleArmor);
        }

        List<Integer> equipment(int gender) {
            ArrayList<Integer> items = new ArrayList<>(gender == 0 ? maleArmor : femaleArmor);
            items.add(EARRING_ITEM_ID);
            items.add(CAPE_ITEM_ID);
            if (shieldItemId > 0) items.add(shieldItemId);
            items.add(weaponItemId);
            return List.copyOf(items);
        }
    }
}
