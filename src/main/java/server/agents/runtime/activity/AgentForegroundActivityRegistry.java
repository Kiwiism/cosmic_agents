package server.agents.runtime.activity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable, priority-ordered foreground activity registry. */
public final class AgentForegroundActivityRegistry {
    private final List<AgentForegroundActivity> activities;

    public AgentForegroundActivityRegistry(List<? extends AgentForegroundActivity> activities) {
        if (activities == null) {
            throw new IllegalArgumentException("Foreground activities are required");
        }
        List<AgentForegroundActivity> ordered = new ArrayList<>(activities);
        Set<String> ids = new HashSet<>();
        for (AgentForegroundActivity activity : ordered) {
            if (activity == null || activity.id() == null || activity.id().isBlank()) {
                throw new IllegalArgumentException("Every foreground activity requires an id");
            }
            if (!ids.add(activity.id())) {
                throw new IllegalArgumentException(
                        "Duplicate foreground activity id: " + activity.id());
            }
        }
        ordered.sort(Comparator.comparingInt(AgentForegroundActivity::priority).reversed());
        this.activities = List.copyOf(ordered);
    }

    public List<AgentForegroundActivity> activities() {
        return activities;
    }
}
