package server.agents.capabilities.expedition.balrog;

import server.agents.field.AgentBalrogTestFixtureService;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/** Pure reward assignment: exact weapon-class matches first, then an even fallback. */
final class AgentEasyBalrogRewardPolicy {
    private AgentEasyBalrogRewardPolicy() {
    }

    static Map<Integer, Integer> assign(List<Member> members, List<Drop> drops) {
        List<Member> orderedMembers = members.stream()
                .sorted(Comparator.comparingInt(Member::ordinal)).toList();
        List<Drop> orderedDrops = drops.stream()
                .sorted(Comparator.comparingInt(Drop::objectId)).toList();
        Map<Integer, Integer> assignedCounts = new HashMap<>();
        Map<Integer, Integer> assignments = new LinkedHashMap<>();
        List<Drop> remaining = new ArrayList<>();

        // Give each exact weapon-class owner at most one priority item first. Extra copies
        // then join the ordinary even-share pass instead of being vacuumed by one class owner.
        for (Drop drop : orderedDrops) {
            AgentBalrogTestFixtureService.WeaponClass rewardClass = weaponClass(drop.itemId());
            List<Member> candidates = rewardClass == null ? List.of() : orderedMembers.stream()
                    .filter(member -> member.weaponClass() == rewardClass).toList();
            Member selected = candidates.stream().min(Comparator
                    .comparingInt((Member member) -> assignedCounts.getOrDefault(member.characterId(), 0))
                    .thenComparingInt(Member::ordinal)).orElse(null);
            if (selected != null && assignedCounts.getOrDefault(selected.characterId(), 0) == 0) {
                assignments.put(drop.objectId(), selected.characterId());
                assignedCounts.merge(selected.characterId(), 1, Integer::sum);
            } else {
                remaining.add(drop);
            }
        }

        for (Drop drop : remaining) {
            Member selected = orderedMembers.stream().min(Comparator
                    .comparingInt((Member member) -> assignedCounts.getOrDefault(member.characterId(), 0))
                    .thenComparingInt(Member::ordinal)).orElse(null);
            if (selected == null) continue;
            assignments.put(drop.objectId(), selected.characterId());
            assignedCounts.merge(selected.characterId(), 1, Integer::sum);
        }
        return assignments;
    }

    private static AgentBalrogTestFixtureService.WeaponClass weaponClass(int itemId) {
        int category = itemId / 10_000;
        return switch (category) {
            case 130 -> AgentBalrogTestFixtureService.WeaponClass.ONE_HANDED_SWORD;
            case 131 -> AgentBalrogTestFixtureService.WeaponClass.ONE_HANDED_AXE;
            case 132 -> AgentBalrogTestFixtureService.WeaponClass.ONE_HANDED_BLUNT;
            case 133 -> AgentBalrogTestFixtureService.WeaponClass.DAGGER;
            case 137 -> AgentBalrogTestFixtureService.WeaponClass.WAND;
            case 138 -> AgentBalrogTestFixtureService.WeaponClass.STAFF;
            case 140 -> AgentBalrogTestFixtureService.WeaponClass.TWO_HANDED_SWORD;
            case 141 -> AgentBalrogTestFixtureService.WeaponClass.TWO_HANDED_AXE;
            case 142 -> AgentBalrogTestFixtureService.WeaponClass.TWO_HANDED_BLUNT;
            case 143 -> AgentBalrogTestFixtureService.WeaponClass.SPEAR;
            case 144 -> AgentBalrogTestFixtureService.WeaponClass.POLEARM;
            case 145 -> AgentBalrogTestFixtureService.WeaponClass.BOW;
            case 146 -> AgentBalrogTestFixtureService.WeaponClass.CROSSBOW;
            case 147 -> AgentBalrogTestFixtureService.WeaponClass.CLAW;
            case 148 -> AgentBalrogTestFixtureService.WeaponClass.KNUCKLE;
            case 149 -> AgentBalrogTestFixtureService.WeaponClass.GUN;
            default -> null;
        };
    }

    record Member(int characterId, int ordinal,
                  AgentBalrogTestFixtureService.WeaponClass weaponClass) {
    }

    record Drop(int objectId, int itemId) {
    }
}
