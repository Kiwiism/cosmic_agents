package server.agents.progression;

import java.util.List;

/** Minimal live matchmaking state shared by story and post-story Yeti plans. */
interface AgentMushroomKingdomYetiLobbyState {
    void beginYetiLobbyVisit(long nowMs);
    boolean yetiAgentScanExpired(long nowMs, long scanMs);
    void markYetiHumanInvites(List<Integer> inviteeIds, long nowMs);
    List<Integer> yetiHumanInviteeIds();
    boolean yetiHumanInviteResponseExpired(long nowMs, long responseMs);
    void clearYetiHumanInvites();
    boolean yetiMatchmakingComplete();
    void completeYetiMatchmaking();
    void restartYetiLobbyVisit(long nowMs);
    void clearYetiLobbyVisit();
}
