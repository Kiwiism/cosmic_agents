package server.agents.capabilities.partyquest.kpq;

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

    public static synchronized void register(AgentKpqSession session) {
        if (sessionByOperator.containsKey(session.operatorId())) {
            throw new IllegalStateException("Operator already owns a KPQ test session");
        }
        sessions.put(session.sessionId(), session);
        sessionByOperator.put(session.operatorId(), session.sessionId());
    }

    public static synchronized void indexMember(AgentKpqSession session, int characterId) {
        String old = sessionByMember.putIfAbsent(characterId, session.sessionId());
        if (old != null && !old.equals(session.sessionId())) {
            throw new IllegalStateException("Agent already belongs to another KPQ session");
        }
    }

    public static synchronized void unindexMember(AgentKpqSession session, int characterId) {
        if (session != null) sessionByMember.remove(characterId, session.sessionId());
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
        return member != null && (member.role() == AgentKpqMemberState.Role.EVENT_LEADER
                || member.role() == AgentKpqMemberState.Role.STAGE5_PASS_COLLECTOR);
    }

    public static boolean canLootSquishyShoes(int characterId) {
        AgentKpqSession session = forMember(characterId);
        return session != null && session.squishyShoesWinnerId() == characterId;
    }
}
