package server.agents.capabilities.townlife;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

final class AgentTownLifeMemory {
    private static final int RECENT_ACTIVITY_LIMIT = 3;
    private static final int DESTINATION_LIMIT = 24;
    private static final long DESTINATION_COOLDOWN_MS = 60_000L;
    private static final long FAILED_DESTINATION_COOLDOWN_MS = 120_000L;

    private final ArrayDeque<AgentTownLifeState.Activity> recentActivities = new ArrayDeque<>();
    private final Map<String, Long> destinationCooldowns = new HashMap<>();

    synchronized boolean recentlyUsed(AgentTownLifeState.Activity activity) {
        return recentActivities.contains(activity);
    }

    synchronized boolean destinationAvailable(String key, long nowMs) {
        if (key == null || key.isBlank()) {
            return true;
        }
        Long until = destinationCooldowns.get(key);
        if (until != null && until <= nowMs) {
            destinationCooldowns.remove(key);
            return true;
        }
        return until == null;
    }

    synchronized void remember(AgentTownLifeState.Activity activity, String destinationKey, long nowMs) {
        if (activity != null && activity != AgentTownLifeState.Activity.NONE) {
            recentActivities.remove(activity);
            recentActivities.addFirst(activity);
            while (recentActivities.size() > RECENT_ACTIVITY_LIMIT) {
                recentActivities.removeLast();
            }
        }
        coolDown(destinationKey, nowMs + DESTINATION_COOLDOWN_MS);
    }

    synchronized void rememberFailure(String destinationKey, long nowMs) {
        coolDown(destinationKey, nowMs + FAILED_DESTINATION_COOLDOWN_MS);
    }

    synchronized void clear() {
        recentActivities.clear();
        destinationCooldowns.clear();
    }

    synchronized List<AgentTownLifeState.Activity> recentActivitiesSnapshot() {
        return List.copyOf(recentActivities);
    }

    private void coolDown(String key, long untilMs) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (destinationCooldowns.size() >= DESTINATION_LIMIT && !destinationCooldowns.containsKey(key)) {
            String oldest = destinationCooldowns.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            destinationCooldowns.remove(oldest);
        }
        destinationCooldowns.put(key, untilMs);
    }
}
