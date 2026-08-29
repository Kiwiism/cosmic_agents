package server.agents.capabilities.partyquest.lpq;

import client.BuffStat;
import client.Character;
import server.maps.MapItem;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** LPQ-only complete session and member indexes. */
public final class AgentLpqSessionRegistry {
    private static final Map<String, AgentLpqSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<Integer, String> BY_MEMBER = new ConcurrentHashMap<>();
    private static final Map<Integer, String> BY_OPERATOR = new ConcurrentHashMap<>();

    private AgentLpqSessionRegistry() { }

    public static synchronized void registerComplete(AgentLpqSession session) {
        if (session == null) throw new IllegalArgumentException("LPQ session is required");
        if (BY_OPERATOR.containsKey(session.operatorId())) throw new IllegalStateException("operator already owns LPQ");
        for (AgentLpqMemberState member : session.members()) {
            if (BY_MEMBER.containsKey(member.characterId())) throw new IllegalStateException("LPQ member already reserved");
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

    public static AgentLpqSession forMember(int id) {
        String sessionId = BY_MEMBER.get(id);
        return sessionId == null ? null : SESSIONS.get(sessionId);
    }

    public static AgentLpqSession forOperator(int id) {
        String sessionId = BY_OPERATOR.get(id);
        return sessionId == null ? null : SESSIONS.get(sessionId);
    }

    public static Collection<AgentLpqSession> sessions() { return java.util.List.copyOf(SESSIONS.values()); }
    public static boolean active(int id) { return forMember(id) != null; }

    public static synchronized void remove(AgentLpqSession session) {
        if (session == null) return;
        if (!SESSIONS.remove(session.sessionId(), session)) return;
        BY_OPERATOR.remove(session.operatorId(), session.sessionId());
        session.members().forEach(member -> BY_MEMBER.remove(member.characterId(), session.sessionId()));
    }

    public static boolean canLootExclusive(Character character, int itemId) {
        if (character == null || (itemId != AgentLpqDefinition.PASS && itemId != AgentLpqDefinition.BOSS_KEY)) return false;
        AgentLpqSession session = forMember(character.getId());
        if (session == null || session.eventInstance() == null
                || character.getEventInstance() != session.eventInstance()
                || !AgentLpqDefinition.isEventMap(character.getMapId())) return false;
        int stage = AgentLpqDefinition.stageNumber(character.getMapId());
        if (itemId == AgentLpqDefinition.PASS && session.couponRegrouping(stage)) {
            return character.getId() == session.eventLeaderId();
        }
        return true;
    }

    /** Keeps the junk-item or 10-meso door claims visible during Stage 4/5 room fan-out. */
    public static boolean preservesRoomDoorMarker(Character character, MapItem drop) {
        if (character == null || drop == null
                || (drop.getMeso() != AgentLpqDefinition.ROOM_MARKER_MESOS
                && !AgentLpqDefinition.ROOM_MARKER_ITEMS.contains(drop.getItemId()))) return false;
        AgentLpqSession session = forMember(character.getId());
        if (session == null || session.eventInstance() == null
                || character.getEventInstance() != session.eventInstance()) return false;
        return switch (session.phase()) {
            case STAGE_4 -> character.getMapId() == AgentLpqDefinition.stage(4).mapId();
            case STAGE_5 -> character.getMapId() == AgentLpqDefinition.stage(5).mapId();
            default -> false;
        };
    }

    /** Dark Sight makes the authored Stage 5 hazard fully non-interactive for LPQ Agents. */
    public static boolean suppressesDarkSightRoomTouch(Character character) {
        AgentLpqSession session = character == null ? null : forMember(character.getId());
        AgentLpqMemberState member = session == null ? null : session.member(character.getId());
        return character != null
                && session != null
                && session.phase() == AgentLpqSession.Phase.STAGE_5
                && session.eventInstance() != null
                && character.getEventInstance() == session.eventInstance()
                && character.getMapId() == AgentLpqDefinition.STAGE_5_DARK_SIGHT_ROOM
                && character.getBuffedValue(BuffStat.DARKSIGHT) != null
                && member != null
                && member.memberType() == AgentLpqMemberState.MemberType.AGENT;
    }

    public static boolean isManagedEvent(Character character) {
        return managedSession(character) != null;
    }

    public static boolean isRegisteredParticipant(Character character) {
        AgentLpqSession session = managedSession(character);
        return character != null && session != null && session.member(character.getId()) != null;
    }

    public static boolean beginRewardClaim(Character character) {
        AgentLpqSession session = managedSession(character);
        if (character != null && session != null && !session.rewardEligibilityFrozen()
                && character.getEventInstance().isEventCleared()) {
            session.freezeRewardEligibility();
        }
        return character != null && session != null && session.beginRewardClaim(character.getId());
    }

    public static boolean completeRewardClaim(Character character) {
        AgentLpqSession session = managedSession(character);
        return character != null && session != null && session.completeRewardClaim(character.getId());
    }

    public static void cancelRewardClaim(Character character) {
        AgentLpqSession session = managedSession(character);
        if (character != null && session != null) session.cancelRewardClaim(character.getId());
    }

    public static void forfeitUnclaimedReward(Character character) {
        AgentLpqSession session = character == null ? null : forMember(character.getId());
        if (session != null && session.eventInstance() == character.getEventInstance()) {
            session.forfeitReward(character.getId());
        }
    }

    private static AgentLpqSession managedSession(Character character) {
        if (character == null || character.getEventInstance() == null) return null;
        return SESSIONS.values().stream()
                .filter(session -> session.eventInstance() == character.getEventInstance())
                .findFirst().orElse(null);
    }
}
