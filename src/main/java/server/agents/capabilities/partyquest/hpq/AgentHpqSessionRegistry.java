package server.agents.capabilities.partyquest.hpq;

import client.Character;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** HPQ-only session and member indexes. */
public final class AgentHpqSessionRegistry {
    private static final Map<String, AgentHpqSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<Integer, String> SESSION_BY_MEMBER = new ConcurrentHashMap<>();
    private static final Map<Integer, String> SESSION_BY_OPERATOR = new ConcurrentHashMap<>();

    private AgentHpqSessionRegistry() {
    }

    public static synchronized void registerComplete(AgentHpqSession session) {
        if (session == null) throw new IllegalArgumentException("HPQ session is required");
        if (SESSION_BY_OPERATOR.containsKey(session.operatorId())) {
            throw new IllegalStateException("operator already owns an HPQ session");
        }
        for (AgentHpqMemberState member : session.members()) {
            if (SESSION_BY_MEMBER.containsKey(member.characterId())) {
                throw new IllegalStateException("party member already belongs to an HPQ session");
            }
        }
        SESSIONS.put(session.sessionId(), session);
        SESSION_BY_OPERATOR.put(session.operatorId(), session.sessionId());
        session.members().forEach(member -> SESSION_BY_MEMBER.put(member.characterId(), session.sessionId()));
    }

    public static synchronized String registrationBlocker(int operatorId, Collection<Integer> characterIds) {
        if (SESSION_BY_OPERATOR.containsKey(operatorId)) return "operator-session";
        if (characterIds != null) {
            for (int characterId : characterIds) {
                if (SESSION_BY_MEMBER.containsKey(characterId)) return "member-" + characterId;
            }
        }
        return "";
    }

    public static AgentHpqSession forMember(int characterId) {
        String sessionId = SESSION_BY_MEMBER.get(characterId);
        return sessionId == null ? null : SESSIONS.get(sessionId);
    }

    public static Collection<AgentHpqSession> sessions() {
        return java.util.List.copyOf(SESSIONS.values());
    }

    public static synchronized void remove(AgentHpqSession session) {
        if (session == null) return;
        SESSIONS.remove(session.sessionId(), session);
        SESSION_BY_OPERATOR.remove(session.operatorId(), session.sessionId());
        session.members().forEach(member ->
                SESSION_BY_MEMBER.remove(member.characterId(), session.sessionId()));
    }

    public static boolean active(int characterId) {
        return forMember(characterId) != null;
    }

    public static boolean canLootRiceCake(int characterId) {
        AgentHpqSession session = forMember(characterId);
        AgentHpqMemberState member = session == null ? null : session.member(characterId);
        return session != null && member != null
                && session.phase() == AgentHpqSession.Phase.DEFENDING_BUNNY
                && (member.role() == AgentHpqMemberState.Role.CAKE_COLLECTOR
                    || member.role() == AgentHpqMemberState.Role.EVENT_LEADER);
    }

    public static boolean canLootRiceCake(Character character) {
        if (character == null || !canLootRiceCake(character.getId())) return false;
        AgentHpqSession session = forMember(character.getId());
        return session != null && session.eventInstance() != null
                && character.getEventInstance() == session.eventInstance()
                && character.getItemQuantity(AgentHpqDefinition.RICE_CAKE, false)
                    < AgentHpqDefinition.REQUIRED_RICE_CAKES;
    }

    public static boolean isManagedEvent(Character character) {
        return managedSession(character) != null;
    }

    public static boolean isRegisteredParticipant(Character character) {
        AgentHpqSession session = managedSession(character);
        return character != null && session != null && session.member(character.getId()) != null;
    }

    public static boolean beginRewardClaim(Character character) {
        AgentHpqSession session = managedSession(character);
        if (character != null && session != null && !session.rewardEligibilityFrozen()
                && character.getEventInstance().isEventCleared()) {
            session.freezeRewardEligibility();
        }
        return character != null && session != null && session.beginRewardClaim(character.getId());
    }

    public static boolean completeRewardClaim(Character character) {
        AgentHpqSession session = managedSession(character);
        return character != null && session != null && session.completeRewardClaim(character.getId());
    }

    public static void cancelRewardClaim(Character character) {
        AgentHpqSession session = managedSession(character);
        if (character != null && session != null) session.cancelRewardClaim(character.getId());
    }

    public static void forfeitUnclaimedReward(Character character) {
        AgentHpqSession session = character == null ? null : forMember(character.getId());
        if (session != null && session.eventInstance() == character.getEventInstance()) {
            session.forfeitReward(character.getId());
        }
    }

    private static AgentHpqSession managedSession(Character character) {
        if (character == null || character.getEventInstance() == null) return null;
        return SESSIONS.values().stream()
                .filter(session -> session.eventInstance() == character.getEventInstance())
                .findFirst().orElse(null);
    }
}
