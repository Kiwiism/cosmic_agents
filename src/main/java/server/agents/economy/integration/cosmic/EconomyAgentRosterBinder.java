package server.agents.economy.integration.cosmic;

import client.Character;
import server.agents.economy.activity.EconomyJobFamily;
import server.agents.economy.scenario.PopulationAdmissionPlanner;
import client.inventory.InventoryType;
import constants.inventory.ItemConstants;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Binds deterministic scenario slots only to real characters with the same job family. */
public final class EconomyAgentRosterBinder {
    public Map<String, Character> bind(List<PopulationAdmissionPlanner.Admission> admissions,
                                       List<Character> liveCharacters) {
        return bind(admissions, liveCharacters, 0);
    }

    public Map<String, Character> bind(List<PopulationAdmissionPlanner.Admission> admissions,
                                       List<Character> liveCharacters, int shopPermitItemId) {
        Objects.requireNonNull(admissions); Objects.requireNonNull(liveCharacters);
        if (shopPermitItemId != 0 && !ItemConstants.isPlayerShop(shopPermitItemId))
            throw new IllegalArgumentException("configured shop permit is not a real PlayerShop item");
        Map<String, Deque<Character>> withoutPermit = new TreeMap<>();
        Map<String, Deque<Character>> withPermit = new TreeMap<>();
        liveCharacters.stream().filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(Character::getId))
                .forEach(character -> (hasPermit(character, shopPermitItemId) ? withPermit : withoutPermit)
                        .computeIfAbsent(EconomyJobFamily.of(character), ignored -> new ArrayDeque<>())
                        .addLast(character));
        Map<String, Character> result = new LinkedHashMap<>();
        Map<String, Integer> missing = new TreeMap<>();
        Map<String, Integer> missingPermits = new TreeMap<>();
        for (PopulationAdmissionPlanner.Admission admission : admissions) {
            boolean seller = shopPermitItemId != 0 && admission.profile().stallWillingness() >= .5d;
            Character character = seller ? poll(withPermit, admission.jobFamily())
                    : poll(withoutPermit, admission.jobFamily());
            if (character == null && !seller) character = poll(withPermit, admission.jobFamily());
            if (character == null) {
                missing.merge(admission.jobFamily(), 1, Integer::sum);
                if (seller) missingPermits.merge(admission.jobFamily(), 1, Integer::sum);
            } else {
                result.put(admission.agentId(), character);
            }
        }
        if (!missing.isEmpty()) {
            Map<String, Integer> supplied = new TreeMap<>();
            liveCharacters.stream().filter(Objects::nonNull).forEach(character ->
                    supplied.merge(EconomyJobFamily.of(character), 1, Integer::sum));
            throw new IllegalStateException("live roster does not satisfy configured class distribution; missing="
                    + missing + " missingSellerPermits=" + missingPermits + " supplied=" + supplied);
        }
        return Map.copyOf(result);
    }

    private static boolean hasPermit(Character character, int itemId) {
        return itemId != 0 && character.getInventory(InventoryType.CASH).countById(itemId) > 0;
    }

    private static Character poll(Map<String, Deque<Character>> values, String family) {
        Deque<Character> candidates = values.get(family);
        return candidates == null ? null : candidates.pollFirst();
    }

}
