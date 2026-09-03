package server.agents.capabilities.partyquest.lmpq;

import client.Character;
import server.maps.MapItem;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** LMPQ-only complete session and member indexes. */
public final class AgentLmpqSessionRegistry {
    private static final Map<String, AgentLmpqSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<Integer, String> BY_MEMBER = new ConcurrentHashMap<>();
    private static final Map<Integer, String> BY_OPERATOR = new ConcurrentHashMap<>();

    private AgentLmpqSessionRegistry() { }

    public static synchronized void registerComplete(AgentLmpqSession session) {
        if (session == null || session.memberCount() != session.requestedPartySize()) {
            throw new IllegalArgumentException("complete LMPQ session required");
        }
        if (BY_OPERATOR.containsKey(session.operatorId())) throw new IllegalStateException("operator already owns LMPQ");
        for (AgentLmpqMemberState member : session.members()) {
            if (BY_MEMBER.containsKey(member.characterId())) throw new IllegalStateException("LMPQ member already reserved");
        }
        SESSIONS.put(session.sessionId(), session);
        BY_OPERATOR.put(session.operatorId(), session.sessionId());
        session.members().forEach(member -> BY_MEMBER.put(member.characterId(), session.sessionId()));
    }

    public static synchronized String registrationBlocker(int operatorId, Collection<Integer> ids) {
        if (BY_OPERATOR.containsKey(operatorId)) return "operator-session";
        if (ids != null) for (int id : ids) if (BY_MEMBER.containsKey(id)) return "member-" + id;
        return "";
    }

    public static AgentLmpqSession forMember(int id) {
        String sessionId = BY_MEMBER.get(id);
        return sessionId == null ? null : SESSIONS.get(sessionId);
    }
    public static AgentLmpqSession forOperator(int id) {
        String sessionId = BY_OPERATOR.get(id);
        return sessionId == null ? null : SESSIONS.get(sessionId);
    }
    public static boolean active(int id) { return forMember(id) != null; }
    public static Collection<AgentLmpqSession> sessions() { return List.copyOf(SESSIONS.values()); }

    public static synchronized void remove(AgentLmpqSession session) {
        if (session == null || !SESSIONS.remove(session.sessionId(), session)) return;
        BY_OPERATOR.remove(session.operatorId(), session.sessionId());
        session.members().forEach(member -> BY_MEMBER.remove(member.characterId(), session.sessionId()));
    }

    public static boolean canLootCoupon(Character character) {
        AgentLmpqSession session = character == null ? null : forMember(character.getId());
        return session != null && session.eventInstance() != null
                && character.getEventInstance() == session.eventInstance()
                && AgentLmpqDefinition.isEventMap(character.getMapId());
    }

    public static boolean preservesPortalMarker(Character character, MapItem drop) {
        if (character == null || drop == null || drop.getMeso() != AgentLmpqDefinition.ROOM_MARKER_MESOS) return false;
        AgentLmpqSession session = forMember(character.getId());
        return session != null && session.eventInstance() != null
                && character.getEventInstance() == session.eventInstance()
                && session.phase() == AgentLmpqSession.Phase.FARMING;
    }
}
