package server.agents.capabilities.partyquest.kpq;

import client.Character;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Indexes active KPQ sessions by session, operator, and member without touching Agent entries. */
public final class AgentKpqSessionRegistry {
    private static final Map<String, AgentKpqSession> sessions = new ConcurrentHashMap<>();
    private static final Map<Integer, String> sessionByMember = new ConcurrentHashMap<>();
    private static final Map<Integer, String> sessionByOperator = new ConcurrentHashMap<>();

    private AgentKpqSessionRegistry() {
    }

    /** Atomically publishes a fully constructed session and all of its member indexes. */
    public static synchronized void registerComplete(AgentKpqSession session) {
        if (session == null) throw new IllegalArgumentException("KPQ session is required");
        if (sessionByOperator.containsKey(session.operatorId())) {
            throw new IllegalStateException("Operator already owns a KPQ session");
        }
        for (AgentKpqMemberState member : session.members()) {
            String existing = sessionByMember.get(member.characterId());
            if (existing != null && !existing.equals(session.sessionId())) {
                throw new IllegalStateException("Party member already belongs to another KPQ session");
            }
        }
        sessions.put(session.sessionId(), session);
        sessionByOperator.put(session.operatorId(), session.sessionId());
        session.members().forEach(member ->
                sessionByMember.put(member.characterId(), session.sessionId()));
    }

    public static synchronized String registrationBlocker(
            int operatorId, Collection<Integer> characterIds) {
        if (sessionByOperator.containsKey(operatorId)) return "operator-session";
        if (characterIds != null) {
            for (int characterId : characterIds) {
                if (sessionByMember.containsKey(characterId)) return "member-" + characterId;
            }
        }
        return "";
    }

    public static AgentKpqSession forMember(int characterId) {
        String sessionId = sessionByMember.get(characterId);
        return sessionId == null ? null : sessions.get(sessionId);
    }

    public static AgentKpqSession forOperator(int operatorId) {
        String sessionId = sessionByOperator.get(operatorId);
        return sessionId == null ? null : sessions.get(sessionId);
    }

    public static Collection<AgentKpqSession> sessions() {
        return java.util.List.copyOf(sessions.values());
    }

    public static synchronized void remove(AgentKpqSession session) {
        if (session == null) return;
        sessions.remove(session.sessionId(), session);
        sessionByOperator.remove(session.operatorId(), session.sessionId());
        session.members().forEach(member -> sessionByMember.remove(member.characterId(), session.sessionId()));
    }

    public static synchronized void unindexMember(AgentKpqSession session, int characterId) {
        if (session != null) sessionByMember.remove(characterId, session.sessionId());
    }

    public static boolean active(int characterId) {
        return forMember(characterId) != null;
    }

    public static boolean canLootCoupon(int characterId) {
        AgentKpqSession session = forMember(characterId);
        AgentKpqMemberState member = session == null ? null : session.member(characterId);
        return member != null && member.role() == AgentKpqMemberState.Role.COUPON_COLLECTOR;
    }

    public static boolean canLootPass(int characterId) {
        AgentKpqSession session = forMember(characterId);
        AgentKpqMemberState member = session == null ? null : session.member(characterId);
        return member != null && !session.stage5LootDelayActive(System.currentTimeMillis())
                && (member.role() == AgentKpqMemberState.Role.EVENT_LEADER
                        || member.role() == AgentKpqMemberState.Role.STAGE5_PASS_COLLECTOR);
    }

    public static boolean canLootSquishyShoes(int characterId) {
        AgentKpqSession session = forMember(characterId);
        return session != null && !session.stage5LootDelayActive(System.currentTimeMillis())
                && session.squishyShoesWinnerId() == characterId;
    }

    /** Called by Cloto's ordinary NPC script after a human event leader checks a puzzle formation. */
    public static void recordHumanPuzzleValidation(int characterId, int stage, boolean accepted) {
        AgentKpqSession session = forMember(characterId);
        AgentKpqMemberState member = session == null ? null : session.member(characterId);
        if (session == null || session.eventLeaderId() != characterId || member == null
                || member.memberType() != AgentKpqMemberState.MemberType.HUMAN) {
            return;
        }
        session.recordHumanPuzzleValidation(stage, accepted);
    }

    public static boolean isManagedEvent(Character character) {
        return managedSession(character) != null;
    }

    public static boolean isRegisteredParticipant(Character character) {
        AgentKpqSession session = managedSession(character);
        return character != null && session != null && session.member(character.getId()) != null;
    }

    public static boolean beginRewardClaim(Character character) {
        AgentKpqSession session = managedSession(character);
        if (character != null && session != null && !session.rewardEligibilityFrozen()
                && character.getEventInstance().isEventCleared()) {
            session.freezeRewardEligibility();
        }
        return character != null && session != null && session.beginRewardClaim(character.getId());
    }

    public static boolean completeRewardClaim(Character character) {
        AgentKpqSession session = managedSession(character);
        return character != null && session != null && session.completeRewardClaim(character.getId());
    }

    public static void cancelRewardClaim(Character character) {
        AgentKpqSession session = managedSession(character);
        if (character != null && session != null) session.cancelRewardClaim(character.getId());
    }

    public static void forfeitUnclaimedReward(Character character) {
        AgentKpqSession session = character == null ? null : forMember(character.getId());
        if (session != null && session.eventInstance() == character.getEventInstance()) {
            session.forfeitReward(character.getId());
        }
    }

    private static AgentKpqSession managedSession(Character character) {
        if (character == null || character.getEventInstance() == null) return null;
        return sessions.values().stream()
                .filter(session -> session.eventInstance() == character.getEventInstance())
                .findFirst().orElse(null);
    }
}
