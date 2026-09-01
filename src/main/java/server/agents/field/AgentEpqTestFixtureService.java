package server.agents.field;

import client.Character;
import client.Job;
import client.inventory.Equip;
import client.inventory.InventoryType;
import client.inventory.Item;
import server.agents.capabilities.partyquest.epq.AgentEpqRosterRequirementPolicy;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.combat.CombatFormulaProvider;
import server.life.LifeFactory;
import server.life.Monster;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Legal level-55 EPQ builds with one member from every Explorer family. */
public final class AgentEpqTestFixtureService {
    public static final int EPQ_LEVEL = 55;
    static final int GLOVE_DEX_10 = 2_040_802;
    static final int GLOVE_DEX_60 = 2_040_801;
    static final int GLOVE_MAGIC_ATTACK_10 = 2_040_816;
    static final int GLOVE_MAGIC_ATTACK_60 = 2_040_817;
    static final int OVERALL_DEX_60 = 2_040_501;
    static final int OVERALL_INT_60 = 2_040_513;
    static final int BOTTOM_DEX_10 = 2_040_612;
    static final int BOTTOM_DEX_60 = 2_040_613;
    static final double MINIMUM_BOSS_HIT_CHANCE = 0.60d;
    private static final Set<Integer> EPQ_COMBAT_MOBS = Set.of(
            9_300_172, 9_300_173, 9_300_175,
            9_300_177, 9_300_178, 9_300_179,
            9_300_180, 9_300_181, 9_300_182);
    private static final Map<Job, String> BUILD_BY_BRANCH = builds();

    private AgentEpqTestFixtureService() { }

    public static PreparationResult prepare(AgentRuntimeEntry entry, Job branch,
                                            long seed, long nowMs) throws IOException {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        String buildId = BUILD_BY_BRANCH.get(branch);
        if (agent == null || buildId == null) {
            throw new IllegalArgumentException("a spawned EPQ Agent and Explorer branch are required");
        }
        AgentBalrogTestFixtureService.Build build = AgentBalrogTestFixtureService.ALL_BUILDS.stream()
                .filter(candidate -> candidate.buildId().equals(buildId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("missing EPQ build " + buildId));
        AgentLpqTestFixtureService.applyAppearance(agent, seed);
        AgentFieldObservationFixtureService.Prepared prepared =
                AgentFieldObservationFixtureService.prepareForBalrog(
                        entry, build, EPQ_LEVEL, EPQ_COMBAT_MOBS, nowMs);
        applyEpqScrolls(agent, branch);
        agent.equipChanged();
        double minimumHitChance = minimumHitChance(agent, branch == Job.MAGICIAN);
        if (minimumHitChance < MINIMUM_BOSS_HIT_CHANCE) {
            throw new IllegalStateException(agent.getName() + " has only "
                    + Math.round(minimumHitChance * 100.0d)
                    + "% minimum EPQ hit chance after legal scrolling");
        }
        if (!prepared.completeBuild()) throw new IllegalStateException("EPQ fixture has unspent AP/SP");
        AgentCharacterGatewayRuntime.characters().save(agent, false);
        return new PreparationResult(prepared.level(), branch, buildId,
                prepared.weaponItemId(), prepared.weaponAttack(), minimumHitChance);
    }

    public static List<Job> agentBranchesFor(Character human) {
        Job humanBranch = AgentEpqRosterRequirementPolicy.branch(human);
        if (human != null && humanBranch == null) {
            throw new IllegalArgumentException("the human EPQ member must be an Explorer class");
        }
        return AgentEpqRosterRequirementPolicy.branches().stream()
                .filter(branch -> branch != humanBranch).toList();
    }

    private static void applyEpqScrolls(Character agent, Job branch) {
        Equip gloves = equipped(agent, (short) -8);
        if (branch == Job.MAGICIAN) {
            apply(gloves, GLOVE_MAGIC_ATTACK_10, 2);
            apply(gloves, GLOVE_MAGIC_ATTACK_60, 3);
            apply(equipped(agent, (short) -5), OVERALL_INT_60, 10);
            return;
        }
        apply(gloves, GLOVE_DEX_10, 2);
        apply(gloves, GLOVE_DEX_60, 3);
        Item overall = agent.getInventory(InventoryType.EQUIPPED).getItem((short) -5);
        if (overall instanceof Equip equip && equip.getUpgradeSlots() >= 10) {
            apply(equip, OVERALL_DEX_60, 10);
            return;
        }
        Item bottom = agent.getInventory(InventoryType.EQUIPPED).getItem((short) -6);
        if (bottom instanceof Equip equip && equip.getUpgradeSlots() >= 5) {
            apply(equip, BOTTOM_DEX_10, 2);
            apply(equip, BOTTOM_DEX_60, 3);
        }
    }

    private static void apply(Equip equip, int scrollId, int successes) {
        AgentFieldObservationFixtureService.applySuccessfulScrollEffects(
                equip, server.agents.integration.AgentInventoryGatewayRuntime.inventory()
                        .getEquipStats(scrollId), successes);
    }

    private static Equip equipped(Character agent, short slot) {
        Item item = agent.getInventory(InventoryType.EQUIPPED).getItem(slot);
        if (!(item instanceof Equip equip)) {
            throw new IllegalStateException("EPQ fixture is missing equipped slot " + slot);
        }
        return equip;
    }

    private static double minimumHitChance(Character agent, boolean magic) {
        CombatFormulaProvider formulas = CombatFormulaProvider.getInstance();
        double minimum = 1.0d;
        for (int mobId : EPQ_COMBAT_MOBS) {
            Monster monster = LifeFactory.getMonster(mobId);
            if (monster == null) continue;
            minimum = Math.min(minimum, formulas.calculateMobHitChance(agent, monster, magic));
        }
        return minimum;
    }

    private static Map<Job, String> builds() {
        EnumMap<Job, String> result = new EnumMap<>(Job.class);
        result.put(Job.WARRIOR, "spearman-spear");
        result.put(Job.MAGICIAN, "cleric-wand");
        result.put(Job.BOWMAN, "hunter-bow");
        result.put(Job.THIEF, "assassin-claw");
        result.put(Job.PIRATE, "gunslinger-gun");
        return Map.copyOf(result);
    }

    public record PreparationResult(int level, Job branch, String buildId,
                                    int weaponItemId, int weaponAttack,
                                    double minimumHitChance) { }
}
