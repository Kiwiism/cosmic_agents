package server.agents.field;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.Equip;
import constants.inventory.EquipSlot;
import constants.skills.Bandit;
import constants.skills.Cleric;
import constants.skills.Gunslinger;
import constants.skills.Hunter;
import constants.skills.ILWizard;
import constants.skills.Spearman;
import server.agents.capabilities.build.profiles.BuildStep;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic level-65 OPQ party: every first-job branch, legal second-job
 * equipment, two-hour supplies, and deliberately empty eye/face/medal slots.
 */
public final class AgentOpqTestFixtureService {
    public static final int OPQ_LEVEL = 65;
    private static final int SHOES_JUMP_SCROLL_100_ITEM_ID = 2_040_710;
    private static final int OPQ_MINIMUM_JUMP_STAT = 120;
    static final List<String> BUILD_IDS = List.of(
            "spearman-spear", "cleric-wand", "hunter-bow",
            "bandit-dagger", "gunslinger-gun", "il-wizard-staff");
    private static final Set<Integer> OPQ_COMBAT_MOBS = Set.of(
            9_300_041, 9_300_042, 9_300_043, 9_300_045, 9_300_046,
            9_300_047, 9_300_048, 9_300_049, 9_300_054, 9_300_055,
            9_300_056, 9_300_039);

    private AgentOpqTestFixtureService() { }

    public static PreparationResult prepare(AgentRuntimeEntry entry, int ordinal,
                                            long seed, long nowMs) throws IOException {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null) throw new IllegalArgumentException("a spawned OPQ Agent is required");
        String buildId = BUILD_IDS.get(Math.floorMod(ordinal, BUILD_IDS.size()));
        AgentBalrogTestFixtureService.Build base = AgentBalrogTestFixtureService.ALL_BUILDS.stream()
                .filter(candidate -> candidate.buildId().equals(buildId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("missing OPQ build " + buildId));
        AgentBalrogTestFixtureService.Build build = level65Build(base);
        AgentLpqTestFixtureService.applyAppearance(agent, seed);
        AgentFieldObservationFixtureService.Prepared prepared =
                AgentFieldObservationFixtureService.prepareForBalrog(
                        entry, build, OPQ_LEVEL, OPQ_COMBAT_MOBS, nowMs);
        if (!prepared.completeBuild()) {
            throw new IllegalStateException("OPQ fixture has unspent AP/SP: ap="
                    + prepared.remainingAp() + ", sp=" + Arrays.toString(prepared.remainingSps()));
        }
        enhanceJumpEquipment(agent);
        assertExcludedSlotsEmpty(agent);
        return new PreparationResult(prepared.level(), prepared.career(), build.job().name(),
                prepared.weaponItemId(), prepared.weaponAttack(), prepared.minimumHitChance());
    }

    private static void enhanceJumpEquipment(Character agent) {
        if (agent.getTotalJumpStat() >= OPQ_MINIMUM_JUMP_STAT) {
            return;
        }
        Item item = agent.getInventory(InventoryType.EQUIPPED).getItem((short) -7);
        if (!(item instanceof Equip shoes)) {
            throw new IllegalStateException("OPQ fixture did not equip shoes");
        }
        int successes = Math.min(5, shoes.getUpgradeSlots());
        if (successes <= 0) {
            throw new IllegalStateException("OPQ fixture shoes have no upgrade slots and only "
                    + agent.getTotalJumpStat() + " jump");
        }
        Map<String, Integer> effects = AgentInventoryGatewayRuntime.inventory()
                .getEquipStats(SHOES_JUMP_SCROLL_100_ITEM_ID);
        AgentFieldObservationFixtureService.applySuccessfulScrollEffects(shoes, effects, successes);
        agent.equipChanged();
        if (agent.getTotalJumpStat() < OPQ_MINIMUM_JUMP_STAT) {
            throw new IllegalStateException("OPQ fixture requires at least "
                    + OPQ_MINIMUM_JUMP_STAT + " jump for the authored Lounge platforms");
        }
        AgentCharacterGatewayRuntime.characters().save(agent, false);
    }

    static void assertExcludedSlotsEmpty(Character agent) {
        var equipped = agent.getInventory(InventoryType.EQUIPPED);
        for (EquipSlot slot : List.of(EquipSlot.FACE_ACCESSORY, EquipSlot.EYE_ACCESSORY, EquipSlot.MEDAL)) {
            Item item = equipped.getItem((short) slot.getPrimarySlot());
            if (item != null) throw new IllegalStateException("OPQ fixture must leave " + slot + " empty");
        }
    }

    /** Extends each level-60 Balrog plan by the 15 SP earned through level 65. */
    static AgentBalrogTestFixtureService.Build level65Build(AgentBalrogTestFixtureService.Build base) {
        ArrayList<BuildStep> steps = new ArrayList<>(base.spBuild());
        switch (base.buildId()) {
            case "spearman-spear" -> {
                steps.add(new BuildStep(Spearman.FINAL_ATTACK_SPEAR, 30));
                steps.add(new BuildStep(Spearman.IRON_WILL, 6));
            }
            case "cleric-wand" -> {
                steps.add(new BuildStep(Cleric.MP_EATER, 20));
                steps.add(new BuildStep(Cleric.INVINCIBLE, 16));
            }
            case "hunter-bow" -> steps.add(new BuildStep(Hunter.POWER_KNOCKBACK, 16));
            case "bandit-dagger" -> steps.add(new BuildStep(Bandit.ENDURE, 16));
            case "gunslinger-gun" -> {
                steps.add(new BuildStep(Gunslinger.RECOIL_SHOT, 20));
                steps.add(new BuildStep(Gunslinger.BLANK_SHOT, 6));
            }
            case "il-wizard-staff" -> steps.add(new BuildStep(ILWizard.SLOW, 16));
            default -> throw new IllegalArgumentException("unsupported OPQ build " + base.buildId());
        }
        return new AgentBalrogTestFixtureService.Build(
                base.buildId(), base.career(), base.job(), base.weaponClass(),
                base.weaponItemId(), base.shieldItemId(), base.apBuild(), List.copyOf(steps));
    }

    public record PreparationResult(int level, String career, String job,
                                    int weaponItemId, int weaponAttack,
                                    double minimumHitChance) { }
}
