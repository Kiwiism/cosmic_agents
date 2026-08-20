package server.agents.capabilities.partyquest;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Atomic indexes for parent party-quest engagements. */
public final class AgentPartyQuestEngagementRegistry {
    private static final Map<String, AgentPartyQuestEngagement> engagements = new ConcurrentHashMap<>();
    private static final Map<Integer, String> engagementByOperator = new ConcurrentHashMap<>();
    private static final Map<Integer, String> engagementByMember = new ConcurrentHashMap<>();

    private AgentPartyQuestEngagementRegistry() {
    }

    public static synchronized void register(AgentPartyQuestEngagement engagement) {
        if (engagement == null) throw new IllegalArgumentException("engagement is required");
        if (engagementByOperator.containsKey(engagement.operatorId())) {
            throw new IllegalStateException("operator already owns a party-quest engagement");
        }
        for (int memberId : engagement.memberIds()) {
            String old = engagementByMember.get(memberId);
            if (old != null && !old.equals(engagement.engagementId())) {
                throw new IllegalStateException(
                        "party-quest member already belongs to another engagement");
            }
        }
        engagements.put(engagement.engagementId(), engagement);
        engagementByOperator.put(engagement.operatorId(), engagement.engagementId());
        for (int memberId : engagement.memberIds()) indexMember(engagement, memberId);
    }

    public static synchronized void indexMember(AgentPartyQuestEngagement engagement, int characterId) {
        if (engagement == null || !engagement.memberIds().contains(characterId)) {
            throw new IllegalArgumentException("engagement member must exist before indexing");
        }
        String old = engagementByMember.putIfAbsent(characterId, engagement.engagementId());
        if (old != null && !old.equals(engagement.engagementId())) {
            throw new IllegalStateException("party-quest member already belongs to another engagement");
        }
    }

    public static synchronized void addAndIndexMember(
            AgentPartyQuestEngagement engagement,
            int characterId,
            AgentPartyQuestEngagement.MemberType type,
            long nowMs) {
        if (engagement == null || engagements.get(engagement.engagementId()) != engagement) {
            throw new IllegalArgumentException("registered engagement is required");
        }
        String old = engagementByMember.get(characterId);
        if (old != null && !old.equals(engagement.engagementId())) {
            throw new IllegalStateException("party-quest member already belongs to another engagement");
        }
        engagement.addMember(characterId, type, nowMs);
        engagementByMember.put(characterId, engagement.engagementId());
    }

    public static synchronized void unindexMember(AgentPartyQuestEngagement engagement, int characterId) {
        if (engagement != null) engagementByMember.remove(characterId, engagement.engagementId());
    }

    public static synchronized void removeAndUnindexMember(
            AgentPartyQuestEngagement engagement, int characterId, long nowMs) {
        if (engagement == null) return;
        engagementByMember.remove(characterId, engagement.engagementId());
        engagement.removeMember(characterId, nowMs);
    }

    public static AgentPartyQuestEngagement forMember(int characterId) {
        String id = engagementByMember.get(characterId);
        return id == null ? null : engagements.get(id);
    }

    public static AgentPartyQuestEngagement forOperator(int operatorId) {
        String id = engagementByOperator.get(operatorId);
        return id == null ? null : engagements.get(id);
    }

    public static AgentPartyQuestEngagement byId(String engagementId) {
        return engagementId == null ? null : engagements.get(engagementId);
    }

    public static Collection<AgentPartyQuestEngagement> engagements() {
        return java.util.List.copyOf(engagements.values());
    }

    public static boolean active(int characterId) {
        AgentPartyQuestEngagement engagement = forMember(characterId);
        return engagement != null && engagement.ownsAgent(characterId);
    }

    public static synchronized void remove(AgentPartyQuestEngagement engagement) {
        if (engagement == null) return;
        engagements.remove(engagement.engagementId(), engagement);
        engagementByOperator.remove(engagement.operatorId(), engagement.engagementId());
        engagement.memberIds().forEach(id -> engagementByMember.remove(id, engagement.engagementId()));
    }
}
