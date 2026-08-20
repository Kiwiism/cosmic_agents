package server.agents.capabilities.partyquest.lobby;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Indexes live lobby sessions independently from active party-quest event sessions. */
public final class AgentPartyQuestLobbyRegistry {
    private static final Map<String, AgentPartyQuestLobbySession> lobbies = new ConcurrentHashMap<>();
    private static final Map<Integer, String> lobbyByMember = new ConcurrentHashMap<>();

    private AgentPartyQuestLobbyRegistry() {
    }

    public static synchronized void register(AgentPartyQuestLobbySession lobby) {
        if (lobby == null) throw new IllegalArgumentException("lobby is required");
        for (int memberId : lobby.memberIds()) {
            String old = lobbyByMember.get(memberId);
            if (old != null && !old.equals(lobby.lobbyId())) {
                throw new IllegalStateException("member already belongs to another party-quest lobby");
            }
        }
        lobbies.put(lobby.lobbyId(), lobby);
        lobby.memberIds().forEach(id -> lobbyByMember.put(id, lobby.lobbyId()));
    }

    public static synchronized void indexMember(AgentPartyQuestLobbySession lobby, int characterId) {
        if (lobby == null || !lobby.contains(characterId)) {
            throw new IllegalArgumentException("lobby member must exist before indexing");
        }
        String old = lobbyByMember.putIfAbsent(characterId, lobby.lobbyId());
        if (old != null && !old.equals(lobby.lobbyId())) {
            throw new IllegalStateException("member already belongs to another party-quest lobby");
        }
    }

    public static synchronized void addAndIndexMember(
            AgentPartyQuestLobbySession lobby,
            int characterId,
            AgentPartyQuestLobbySession.MemberType type,
            AgentPartyQuestLobbySession.MemberRole role,
            long nowMs) {
        if (lobby == null || lobbies.get(lobby.lobbyId()) != lobby) {
            throw new IllegalArgumentException("registered lobby is required");
        }
        String old = lobbyByMember.get(characterId);
        if (old != null && !old.equals(lobby.lobbyId())) {
            throw new IllegalStateException("member already belongs to another party-quest lobby");
        }
        lobby.addMember(characterId, type, role, nowMs);
        lobbyByMember.put(characterId, lobby.lobbyId());
    }

    public static synchronized void unindexMember(AgentPartyQuestLobbySession lobby, int characterId) {
        if (lobby != null) lobbyByMember.remove(characterId, lobby.lobbyId());
    }

    public static synchronized void removeAndUnindexMember(
            AgentPartyQuestLobbySession lobby, int characterId, long nowMs) {
        if (lobby == null) return;
        lobbyByMember.remove(characterId, lobby.lobbyId());
        lobby.removeMember(characterId, nowMs);
    }

    public static AgentPartyQuestLobbySession forMember(int characterId) {
        String id = lobbyByMember.get(characterId);
        return id == null ? null : lobbies.get(id);
    }

    public static AgentPartyQuestLobbySession byId(String lobbyId) {
        return lobbyId == null ? null : lobbies.get(lobbyId);
    }

    public static Collection<AgentPartyQuestLobbySession> lobbies() {
        return java.util.List.copyOf(lobbies.values());
    }

    public static synchronized void remove(AgentPartyQuestLobbySession lobby) {
        if (lobby == null) return;
        lobbies.remove(lobby.lobbyId(), lobby);
        lobby.memberIds().forEach(id -> lobbyByMember.remove(id, lobby.lobbyId()));
    }
}
