package server.agents.capabilities.supplies;

import client.Disease;
import client.inventory.Item;
import constants.id.ItemId;
import server.StatEffect;

import java.util.Collection;
import java.util.Set;
import java.util.function.Function;

/** Selects an owned USE item that cures the largest number of active curable diseases. */
final class AgentDiseaseCurePolicy {
    static final Set<Disease> CURABLE_DISEASES = Set.of(
            Disease.SLOW,
            Disease.POISON,
            Disease.SEAL,
            Disease.DARKNESS,
            Disease.WEAKEN,
            Disease.CURSE);

    private AgentDiseaseCurePolicy() {
    }

    static CureChoice select(Collection<Item> items,
                             Function<Integer, StatEffect> effectLookup,
                             Set<Disease> activeDiseases) {
        if (items == null || effectLookup == null || activeDiseases == null
                || activeDiseases.isEmpty()) {
            return null;
        }
        CureChoice best = null;
        int bestCoverage = 0;
        for (Item item : items) {
            if (item == null || item.getQuantity() <= 0
                    || !ItemId.isDiseaseCure(item.getItemId())) continue;
            StatEffect effect = effectLookup.apply(item.getItemId());
            if (effect == null) continue;
            int coverage = (int) activeDiseases.stream()
                    .filter(CURABLE_DISEASES::contains)
                    .filter(effect::curesDisease)
                    .count();
            if (coverage > bestCoverage) {
                best = new CureChoice(item.getPosition(), item.getItemId());
                bestCoverage = coverage;
            }
        }
        return best;
    }

    static long signature(Set<Disease> activeDiseases) {
        if (activeDiseases == null) return 0L;
        long signature = 0L;
        for (Disease disease : activeDiseases) {
            if (CURABLE_DISEASES.contains(disease)) signature |= disease.getValue();
        }
        return signature;
    }

    record CureChoice(short slot, int itemId) {
    }
}
