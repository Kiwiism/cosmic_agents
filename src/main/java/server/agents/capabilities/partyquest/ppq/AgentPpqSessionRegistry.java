package server.agents.capabilities.partyquest.ppq;

import client.Character;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** PPQ-only session indexes. */
public final class AgentPpqSessionRegistry {
    private static final Map<String, AgentPpqSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<Integer, String> BY_MEMBER = new ConcurrentHashMap<>();
    private static final Map<Integer, String> BY_OPERATOR = new ConcurrentHashMap<>();
    private AgentPpqSessionRegistry() { }
    public static synchronized void registerComplete(AgentPpqSession session) {
        if (session == null || session.memberCount() != AgentPpqDefinition.PARTY_SIZE) {
            throw new IllegalArgumentException("complete six-member PPQ session required");
        }
        if (BY_OPERATOR.containsKey(session.operatorId())) throw new IllegalStateException("operator already owns PPQ");
        for (AgentPpqMemberState member : session.members()) if (BY_MEMBER.containsKey(member.characterId())) {
            throw new IllegalStateException("PPQ member already reserved");
        }
        SESSIONS.put(session.sessionId(), session); BY_OPERATOR.put(session.operatorId(), session.sessionId());
        session.members().forEach(member -> BY_MEMBER.put(member.characterId(), session.sessionId()));
    }
    public static AgentPpqSession forMember(int id) { String key = BY_MEMBER.get(id); return key == null ? null : SESSIONS.get(key); }
    public static AgentPpqSession forOperator(int id) { String key = BY_OPERATOR.get(id); return key == null ? null : SESSIONS.get(key); }
    public static boolean active(int id) { return forMember(id) != null; }
    public static Collection<AgentPpqSession> sessions() { return List.copyOf(SESSIONS.values()); }
    public static synchronized void remove(AgentPpqSession session) {
        if (session == null || !SESSIONS.remove(session.sessionId(), session)) return;
        BY_OPERATOR.remove(session.operatorId(), session.sessionId());
        session.members().forEach(member -> BY_MEMBER.remove(member.characterId(), session.sessionId()));
    }
    public static boolean canLootExclusive(Character character, int itemId) {
        if (character == null || !AgentPpqDefinition.EXCLUSIVE_ITEMS.contains(itemId)) return false;
        AgentPpqSession session = forMember(character.getId());
        if (session == null || session.eventInstance() == null
                || character.getEventInstance() != session.eventInstance()) return false;
        if (AgentPpqDefinition.MEDALS.contains(itemId)) return character.getId() == session.eventLeaderId();
        return true;
    }
}
