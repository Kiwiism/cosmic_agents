package server.agents.capabilities.equipment;

import client.Character;
import client.inventory.Equip;
import client.inventory.InventoryType;
import client.inventory.Item;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prefers distinct visible clothing for active Agents in the same job family.
 * Combat strength remains the optimizer's concern; this policy only removes an
 * already-used appearance item when the Agent owns another legal candidate.
 */
final class AgentEquipmentAppearanceDiversityPolicy {
    static final short HAT_SLOT = -1;
    static final short TOP_OR_OVERALL_SLOT = -5;
    static final short BOTTOM_SLOT = -6;
    private static final Set<Short> APPEARANCE_SLOTS = Set.of(
            HAT_SLOT, TOP_OR_OVERALL_SLOT, BOTTOM_SLOT);

    private AgentEquipmentAppearanceDiversityPolicy() {
    }

    static void preferUnusedSameClassItems(Character agent, Map<Short, List<Equip>> candidatesBySlot) {
        if (agent == null || agent.getJob() == null || candidatesBySlot == null
                || candidatesBySlot.isEmpty()) {
            return;
        }
        Map<Short, Set<Integer>> usedBySlot = usedAppearanceItems(agent);
        for (short slot : APPEARANCE_SLOTS) {
            List<Equip> candidates = candidatesBySlot.get(slot);
            if (candidates == null || candidates.isEmpty()) {
                continue;
            }
            candidatesBySlot.put(slot, preferUnusedCandidates(
                    candidates, usedBySlot.getOrDefault(slot, Set.of())));
        }
    }

    static List<Equip> preferUnusedCandidates(List<Equip> candidates, Set<Integer> usedItemIds) {
        if (candidates == null || candidates.isEmpty() || usedItemIds == null
                || usedItemIds.isEmpty()) {
            return candidates;
        }
        List<Equip> unused = candidates.stream()
                .filter(candidate -> candidate != null
                        && !usedItemIds.contains(candidate.getItemId()))
                .toList();
        return unused.isEmpty() ? candidates : new ArrayList<>(unused);
    }

    private static Map<Short, Set<Integer>> usedAppearanceItems(Character agent) {
        Map<Short, Set<Integer>> usedBySlot = new HashMap<>();
        for (AgentRuntimeEntry entry : AgentRuntimeRegistry.activeEntriesSnapshot()) {
            Character peer = AgentRuntimeIdentityRuntime.bot(entry);
            if (peer == null || peer.getId() == agent.getId() || peer.getJob() == null
                    || peer.getJob().getJobNiche() != agent.getJob().getJobNiche()) {
                continue;
            }
            for (Item item : peer.getInventory(InventoryType.EQUIPPED).list()) {
                if (!(item instanceof Equip equip)) {
                    continue;
                }
                short slot = equip.getPosition();
                if (APPEARANCE_SLOTS.contains(slot)) {
                    usedBySlot.computeIfAbsent(slot, ignored -> new HashSet<>())
                            .add(equip.getItemId());
                }
            }
        }
        return usedBySlot;
    }
}
