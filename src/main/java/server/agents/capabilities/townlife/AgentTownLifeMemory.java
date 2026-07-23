package server.agents.capabilities.townlife;

import server.agents.personality.AgentPersonalityProfile;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bounded session memory; stores only immutable identities and summaries. */
final class AgentTownLifeMemory {
    enum SocialOutcome {
        COMPLETED,
        CANCELLED,
        DECLINED
    }

    record SocialMemory(int peerAgentId,
                        AgentTownLifeEncounterState.Type type,
                        String venueId,
                        SocialOutcome outcome,
                        long occurredAtMs) {
    }

    record RelationshipSummary(int peerAgentId,
                               int encounters,
                               int completed,
                               int declined,
                               long lastEncounterAtMs,
                               AgentTownLifeEncounterState.Type lastType) {
    }

    private static final int RECENT_ACTIVITY_LIMIT = 3;
    private static final int RECENT_SOCIAL_LIMIT = 16;
    private static final int RELATIONSHIP_LIMIT = 24;
    private static final int DESTINATION_LIMIT = 24;
    private static final long DESTINATION_COOLDOWN_MS = 60_000L;
    private static final long FAILED_DESTINATION_COOLDOWN_MS = 120_000L;
    private static final long COMPLETED_PEER_COOLDOWN_MS = 90_000L;
    private static final long CANCELLED_PEER_COOLDOWN_MS = 45_000L;
    private static final long DECLINED_PEER_COOLDOWN_MS = 180_000L;

    private final ArrayDeque<AgentTownLifeState.Activity> recentActivities = new ArrayDeque<>();
    private final ArrayDeque<SocialMemory> recentSocial = new ArrayDeque<>();
    private final Map<String, Long> destinationCooldowns = new HashMap<>();
    private final Map<Integer, MutableRelationship> relationships = new HashMap<>();
    private final Map<Integer, Long> peerCooldowns = new HashMap<>();

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

    synchronized boolean peerAvailable(int peerAgentId, long nowMs) {
        Long until = peerCooldowns.get(peerAgentId);
        if (until != null && until <= nowMs) {
            peerCooldowns.remove(peerAgentId);
            return true;
        }
        return until == null;
    }

    synchronized int peerPreferenceScore(int peerAgentId,
                                         AgentPersonalityProfile.Traits traits,
                                         long nowMs) {
        MutableRelationship relationship = relationships.get(peerAgentId);
        int encounters = relationship == null ? 0 : relationship.encounters;
        int routine = traits == null ? 50 : traits.routinePreference();
        int score = encounters == 0
                ? (routine >= 60 ? 0 : 30)
                : (routine >= 60 ? 40 + Math.min(30, encounters * 6)
                : -Math.min(25, encounters * 5));
        if (!peerAvailable(peerAgentId, nowMs)) {
            score -= 100;
        }
        return score;
    }

    synchronized void remember(AgentTownLifeState.Activity activity,
                               String destinationKey,
                               long nowMs) {
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

    synchronized void rememberSocial(int peerAgentId,
                                     AgentTownLifeEncounterState.Type type,
                                     String venueId,
                                     SocialOutcome outcome,
                                     long nowMs) {
        if (peerAgentId <= 0 || type == null || outcome == null) {
            return;
        }
        recentSocial.addFirst(new SocialMemory(
                peerAgentId, type, venueId == null ? "" : venueId, outcome, nowMs));
        while (recentSocial.size() > RECENT_SOCIAL_LIMIT) {
            recentSocial.removeLast();
        }
        MutableRelationship relationship = relationships.computeIfAbsent(
                peerAgentId, ignored -> new MutableRelationship());
        relationship.encounters++;
        relationship.lastEncounterAtMs = nowMs;
        relationship.lastType = type;
        if (outcome == SocialOutcome.COMPLETED) {
            relationship.completed++;
        } else if (outcome == SocialOutcome.DECLINED) {
            relationship.declined++;
        }
        peerCooldowns.put(peerAgentId, nowMs + switch (outcome) {
            case COMPLETED -> COMPLETED_PEER_COOLDOWN_MS;
            case CANCELLED -> CANCELLED_PEER_COOLDOWN_MS;
            case DECLINED -> DECLINED_PEER_COOLDOWN_MS;
        });
        trimRelationships();
    }

    synchronized void clearVisit() {
        recentActivities.clear();
        destinationCooldowns.clear();
    }

    synchronized void clear() {
        clearVisit();
        recentSocial.clear();
        relationships.clear();
        peerCooldowns.clear();
    }

    synchronized List<AgentTownLifeState.Activity> recentActivitiesSnapshot() {
        return List.copyOf(recentActivities);
    }

    synchronized List<SocialMemory> recentSocialSnapshot() {
        return List.copyOf(recentSocial);
    }

    synchronized List<RelationshipSummary> relationshipSummariesSnapshot() {
        return relationships.entrySet().stream()
                .map(entry -> new RelationshipSummary(
                        entry.getKey(), entry.getValue().encounters,
                        entry.getValue().completed, entry.getValue().declined,
                        entry.getValue().lastEncounterAtMs, entry.getValue().lastType))
                .sorted(java.util.Comparator.comparingLong(
                        RelationshipSummary::lastEncounterAtMs).reversed())
                .toList();
    }

    private void trimRelationships() {
        if (relationships.size() <= RELATIONSHIP_LIMIT) {
            return;
        }
        Integer oldest = relationships.entrySet().stream()
                .min(Map.Entry.comparingByValue(
                        java.util.Comparator.comparingLong(value -> value.lastEncounterAtMs)))
                .map(Map.Entry::getKey)
                .orElse(null);
        relationships.remove(oldest);
        peerCooldowns.remove(oldest);
    }

    private void coolDown(String key, long untilMs) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (destinationCooldowns.size() >= DESTINATION_LIMIT
                && !destinationCooldowns.containsKey(key)) {
            String oldest = destinationCooldowns.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            destinationCooldowns.remove(oldest);
        }
        destinationCooldowns.put(key, untilMs);
    }

    private static final class MutableRelationship {
        private int encounters;
        private int completed;
        private int declined;
        private long lastEncounterAtMs;
        private AgentTownLifeEncounterState.Type lastType;
    }
}
