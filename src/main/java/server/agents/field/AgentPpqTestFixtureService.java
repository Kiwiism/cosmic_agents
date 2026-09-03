package server.agents.field;

import client.Character;
import client.inventory.InventoryType;
import constants.skills.Bandit;
import constants.skills.Cleric;
import constants.skills.Gunslinger;
import constants.skills.Hunter;
import constants.skills.ILWizard;
import constants.skills.Spearman;
import server.agents.capabilities.build.AgentBuildService;
import server.agents.capabilities.build.profiles.BuildStep;
import server.agents.capabilities.partyquest.ppq.AgentPpqDefinition;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Legal level-67 PPQ roster with distinct class hats and visible clothing. */
public final class AgentPpqTestFixtureService {
    public static final int LEVEL = 67;
    private static final int SHOES_SPEED_SCROLL = 2_040_708;
    private static final int EARRING = 1_032_075;
    private static final int CAPE = 1_102_055;
    static final List<String> BUILD_IDS = List.of(
            "spearman-spear", "cleric-wand", "hunter-bow",
            "bandit-dagger", "gunslinger-gun", "il-wizard-staff");

    static final Map<String, Loadout> LOADOUTS = Map.of(
            "spearman-spear", new Loadout(1_432_022, 2_044_301, 2_041_014,
                    List.of(1_002_029, 1_040_090, 1_060_079, 1_082_059, 1_072_147),
                    List.of(1_002_029, 1_041_091, 1_061_090, 1_082_059, 1_072_147)),
            "cleric-wand", new Loadout(1_372_021, 2_043_701, 2_041_017,
                    List.of(1_002_242, 1_050_053, 1_082_086, 1_072_136),
                    List.of(1_002_242, 1_051_044, 1_082_086, 1_072_136)),
            "hunter-bow", new Loadout(1_452_030, 2_044_501, 2_041_020,
                    List.of(1_002_267, 1_050_058, 1_082_089, 1_072_144),
                    List.of(1_002_267, 1_051_041, 1_082_089, 1_072_144)),
            "bandit-dagger", new Loadout(1_332_036, 2_043_301, 2_041_023,
                    List.of(1_002_247, 1_040_098, 1_060_087, 1_082_092, 1_072_150),
                    List.of(1_002_247, 1_041_094, 1_061_093, 1_082_092, 1_072_150)),
            "gunslinger-gun", new Loadout(1_492_018, 2_044_901, 2_041_020,
                    List.of(1_002_634, 1_052_119, 1_082_201, 1_072_306),
                    List.of(1_002_634, 1_052_116, 1_082_201, 1_072_306)),
            "il-wizard-staff", new Loadout(1_382_023, 2_043_801, 2_041_017,
                    List.of(1_002_243, 1_050_054, 1_082_086, 1_072_136),
                    List.of(1_002_243, 1_051_045, 1_082_086, 1_072_136)));

    private AgentPpqTestFixtureService() { }

    public static PreparationResult prepare(AgentRuntimeEntry entry, int ordinal,
                                             long seed, long nowMs) throws IOException {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null) throw new IllegalArgumentException("a spawned PPQ Agent is required");
        for (int itemId : AgentPpqDefinition.EXCLUSIVE_ITEMS) {
            int quantity = agent.getItemQuantity(itemId, false);
            if (quantity > 0) {
                AgentInventoryGatewayRuntime.inventory().removeById(
                        agent, InventoryType.ETC, itemId, quantity, false, false);
            }
        }
        String buildId = BUILD_IDS.get(Math.floorMod(ordinal, BUILD_IDS.size()));
        AgentBalrogTestFixtureService.Build build = build(buildId);
        Loadout loadout = LOADOUTS.get(buildId);
        AgentLpqTestFixtureService.applyAppearance(agent, seed);
        AgentFieldObservationFixtureService.Prepared prepared =
                AgentFieldObservationFixtureService.prepareForPartyQuest(
                        entry, build, LEVEL, AgentPpqDefinition.COMBAT_MOBS,
                        loadout.equipment(agent.getGender()),
                        loadout.weaponScroll(),
                        SHOES_SPEED_SCROLL, loadout.capeScroll(), nowMs);
        if (!prepared.completeBuild()) throw new IllegalStateException("PPQ fixture has unspent AP/SP");
        return new PreparationResult(prepared.level(), prepared.career(), build.job().name(),
                prepared.weaponItemId(), prepared.weaponAttack());
    }

    static AgentBalrogTestFixtureService.Build build(String buildId) {
        AgentBalrogTestFixtureService.Build base = AgentBalrogTestFixtureService.ALL_BUILDS.stream()
                .filter(candidate -> candidate.buildId().equals(buildId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("missing PPQ build " + buildId));
        Loadout loadout = LOADOUTS.get(buildId);
        AgentBuildService.ApBuild ap = "il-wizard-staff".equals(buildId)
                ? new AgentBuildService.ApBuild(AgentBuildService.StatType.INT,
                AgentBuildService.StatType.LUK, 68) : base.apBuild();
        return new AgentBalrogTestFixtureService.Build(
                base.buildId(), base.career(), base.job(), base.weaponClass(),
                loadout.weapon(), 0, ap, level67SpBuild(base));
    }

    private static List<BuildStep> level67SpBuild(AgentBalrogTestFixtureService.Build build) {
        ArrayList<BuildStep> steps = new ArrayList<>(build.spBuild());
        switch (build.buildId()) {
            case "spearman-spear" -> {
                steps.add(new BuildStep(Spearman.FINAL_ATTACK_SPEAR, 30));
                steps.add(new BuildStep(Spearman.IRON_WILL, 12));
            }
            case "cleric-wand" -> {
                steps.add(new BuildStep(Cleric.MP_EATER, 20));
                steps.add(new BuildStep(Cleric.INVINCIBLE, 20));
                steps.add(new BuildStep(Cleric.HOLY_ARROW, 2));
            }
            case "hunter-bow" -> {
                steps.add(new BuildStep(Hunter.POWER_KNOCKBACK, 20));
                steps.add(new BuildStep(Hunter.FINAL_ATTACK, 2));
            }
            case "bandit-dagger" -> {
                steps.add(new BuildStep(Bandit.ENDURE, 20));
                steps.add(new BuildStep(Bandit.STEAL, 2));
            }
            case "gunslinger-gun" -> {
                steps.add(new BuildStep(Gunslinger.RECOIL_SHOT, 20));
                steps.add(new BuildStep(Gunslinger.BLANK_SHOT, 12));
            }
            case "il-wizard-staff" -> {
                steps.add(new BuildStep(ILWizard.SLOW, 20));
                steps.add(new BuildStep(ILWizard.THUNDERBOLT, 2));
            }
            default -> throw new IllegalArgumentException("unsupported PPQ build " + build.buildId());
        }
        return List.copyOf(steps);
    }

    public record PreparationResult(int level, String career, String job,
                                    int weaponItemId, int weaponAttack) { }

    record Loadout(int weapon, int weaponScroll, int capeScroll,
                   List<Integer> male, List<Integer> female) {
        List<Integer> equipment(int gender) {
            ArrayList<Integer> items = new ArrayList<>(gender == 0 ? male : female);
            items.add(EARRING);
            items.add(CAPE);
            items.add(weapon);
            return List.copyOf(items);
        }
    }
}
