package server.agents.progression;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Builds a bounded same-map incidental set for instructor hunts. */
public final class AgentInstructorCombatPolicy {
    private AgentInstructorCombatPolicy() {
    }

    public static Set<Integer> localIncidentalMobIds(Set<Integer> requiredMobIds,
                                                     Map<Integer, Integer> configuredSpawns,
                                                     Map<Integer, Integer> liveMonsters) {
        LinkedHashSet<Integer> incidental = new LinkedHashSet<>();
        addPositiveCounts(incidental, configuredSpawns);
        addPositiveCounts(incidental, liveMonsters);
        if (requiredMobIds != null) {
            incidental.removeAll(requiredMobIds);
        }
        return Set.copyOf(incidental);
    }

    private static void addPositiveCounts(Set<Integer> destination,
                                          Map<Integer, Integer> counts) {
        if (counts == null) {
            return;
        }
        counts.forEach((mobId, count) -> {
            if (mobId != null && mobId > 0 && count != null && count > 0) {
                destination.add(mobId);
            }
        });
    }
}
