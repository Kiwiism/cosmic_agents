package server.agents.capabilities.partyquest.epq;

import client.Character;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** EPQ-only complete session indexes. */
public final class AgentEpqSessionRegistry {
    private static final Map<String, AgentEpqSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<Integer, String> BY_MEMBER = new ConcurrentHashMap<>();
    private static final Map<Integer, String> BY_OPERATOR = new ConcurrentHashMap<>();
    private AgentEpqSessionRegistry() { }

    public static synchronized void registerComplete(AgentEpqSession session) {
        if (session == null || session.memberCount() < AgentEpqDefinition.MIN_PARTY_SIZE
                || session.memberCount() > AgentEpqDefinition.MAX_PARTY_SIZE) {
            throw new IllegalArgumentException("complete 4-6 member EPQ session required");
        }
        if (BY_OPERATOR.containsKey(session.operatorId())) throw new IllegalStateException("operator already owns EPQ");
        for (AgentEpqMemberState member : session.members()) if (BY_MEMBER.containsKey(member.characterId())) {
            throw new IllegalStateException("EPQ member already reserved");
        }
        SESSIONS.put(session.sessionId(), session);
        BY_OPERATOR.put(session.operatorId(), session.sessionId());
        session.members().forEach(member -> BY_MEMBER.put(member.characterId(), session.sessionId()));
    }

    public static AgentEpqSession forMember(int id) {
        String sessionId = BY_MEMBER.get(id);
        return sessionId == null ? null : SESSIONS.get(sessionId);
    }
    public static AgentEpqSession forOperator(int id) {
        String sessionId = BY_OPERATOR.get(id);
        return sessionId == null ? null : SESSIONS.get(sessionId);
    }
    public static boolean active(int id) { return forMember(id) != null; }
    public static Collection<AgentEpqSession> sessions() { return List.copyOf(SESSIONS.values()); }
    public static synchronized void remove(AgentEpqSession session) {
        if (session == null || !SESSIONS.remove(session.sessionId(), session)) return;
        BY_OPERATOR.remove(session.operatorId(), session.sessionId());
        session.members().forEach(member -> BY_MEMBER.remove(member.characterId(), session.sessionId()));
    }

    public static boolean canLootExclusive(Character character, int itemId) {
        if (character == null || !AgentEpqDefinition.EXCLUSIVE_ITEMS.contains(itemId)) return false;
        AgentEpqSession session = forMember(character.getId());
        return session != null && session.eventInstance() != null
                && character.getEventInstance() == session.eventInstance()
                && AgentEpqDefinition.isEventMap(character.getMapId());
    }
}
