package server.agents.capabilities.partyquest.opq;

import client.Character;
import server.maps.MapItem;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** OPQ-only complete session indexes plus global loot boundary hooks. */
public final class AgentOpqSessionRegistry {
    private static final Map<String, AgentOpqSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<Integer, String> BY_MEMBER = new ConcurrentHashMap<>();
    private static final Map<Integer, String> BY_OPERATOR = new ConcurrentHashMap<>();
    private AgentOpqSessionRegistry() { }

    public static synchronized void registerComplete(AgentOpqSession session) {
        if (session == null || session.memberCount() != AgentOpqDefinition.PARTY_SIZE) {
            throw new IllegalArgumentException("complete six-member OPQ session required");
        }
        if (BY_OPERATOR.containsKey(session.operatorId())) throw new IllegalStateException("operator already owns OPQ");
        for (AgentOpqMemberState member : session.members()) if (BY_MEMBER.containsKey(member.characterId())) {
            throw new IllegalStateException("OPQ member already reserved");
        }
        SESSIONS.put(session.sessionId(), session);
        BY_OPERATOR.put(session.operatorId(), session.sessionId());
        session.members().forEach(member -> BY_MEMBER.put(member.characterId(), session.sessionId()));
    }

    public static AgentOpqSession forMember(int id) {
        String sessionId = BY_MEMBER.get(id);
        return sessionId == null ? null : SESSIONS.get(sessionId);
    }
    public static AgentOpqSession forOperator(int id) {
        String sessionId = BY_OPERATOR.get(id);
        return sessionId == null ? null : SESSIONS.get(sessionId);
    }
    public static boolean active(int id) { return forMember(id) != null; }
    public static Collection<AgentOpqSession> sessions() { return List.copyOf(SESSIONS.values()); }
    public static synchronized void remove(AgentOpqSession session) {
        if (session == null || !SESSIONS.remove(session.sessionId(), session)) return;
        BY_OPERATOR.remove(session.operatorId(), session.sessionId());
        session.members().forEach(member -> BY_MEMBER.remove(member.characterId(), session.sessionId()));
    }

    public static boolean canLootExclusive(Character character, int itemId) {
        if (character == null || !AgentOpqDefinition.EXCLUSIVE_ITEMS.contains(itemId)) return false;
        AgentOpqSession session = forMember(character.getId());
        if (session == null || session.eventInstance() == null
                || character.getEventInstance() != session.eventInstance()) return false;
        AgentOpqMemberState member = session.member(character.getId());
        if (member == null) return false;
        if (itemId == AgentOpqDefinition.CLOUD_PIECE) {
            return character.getMapId() == AgentOpqDefinition.ENTRANCE_MAP
                    && member.role() == AgentOpqMemberState.Role.ENTRANCE_COLLECTOR;
        }
        if (itemId == AgentOpqDefinition.WALKWAY_FRAGMENT) {
            return character.getMapId() == AgentOpqDefinition.WALKWAY_MAP
                    && member.assignedRoom() == AgentOpqDefinition.Room.WALKWAY;
        }
        if (itemId == AgentOpqDefinition.LOUNGE_FRAGMENT) {
            return AgentOpqDefinition.roomForMap(character.getMapId()) == AgentOpqDefinition.Room.LOUNGE
                    && member.assignedRoom() == AgentOpqDefinition.Room.LOUNGE;
        }
        return session.loot().canLoot(itemId, character.getId(), character.getMapId());
    }

    public static boolean preservesMarker(Character character, MapItem drop) {
        if (character == null || drop == null || drop.getMeso() != 10) return false;
        AgentOpqSession session = forMember(character.getId());
        return session != null && session.phase() == AgentOpqSession.Phase.SPLIT_ROOMS
                && character.getEventInstance() == session.eventInstance();
    }
}
