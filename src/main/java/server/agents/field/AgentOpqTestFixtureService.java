package server.agents.field;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import constants.inventory.EquipSlot;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Deterministic level-65 OPQ party: every first-job branch, legal second-job
 * equipment, two-hour supplies, and deliberately empty eye/face/medal slots.
 */
public final class AgentOpqTestFixtureService {
    public static final int OPQ_LEVEL = 65;
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
        AgentBalrogTestFixtureService.Build build = AgentBalrogTestFixtureService.ALL_BUILDS.stream()
                .filter(candidate -> candidate.buildId().equals(buildId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("missing OPQ build " + buildId));
        AgentLpqTestFixtureService.applyAppearance(agent, seed);
        AgentFieldObservationFixtureService.Prepared prepared =
                AgentFieldObservationFixtureService.prepareForBalrog(
                        entry, build, OPQ_LEVEL, OPQ_COMBAT_MOBS, nowMs);
        if (!prepared.completeBuild()) throw new IllegalStateException("OPQ fixture has unspent AP/SP");
        assertExcludedSlotsEmpty(agent);
        return new PreparationResult(prepared.level(), prepared.career(), build.job().name(),
                prepared.weaponItemId(), prepared.weaponAttack(), prepared.minimumHitChance());
    }

    static void assertExcludedSlotsEmpty(Character agent) {
        var equipped = agent.getInventory(InventoryType.EQUIPPED);
        for (EquipSlot slot : List.of(EquipSlot.FACE_ACCESSORY, EquipSlot.EYE_ACCESSORY, EquipSlot.MEDAL)) {
            Item item = equipped.getItem((short) slot.getPrimarySlot());
            if (item != null) throw new IllegalStateException("OPQ fixture must leave " + slot + " empty");
        }
    }

    public record PreparationResult(int level, String career, String job,
                                    int weaponItemId, int weaponAttack,
                                    double minimumHitChance) { }
}
