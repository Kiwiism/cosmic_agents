package server.agents.capabilities.expedition;

import client.Character;
import scripting.event.EventInstanceManager;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

/** Unique preparation and battle behavior plugged into the shared expedition lobby. */
public interface AgentExpeditionScenario {
    AgentExpeditionSpec spec();

    AgentExpeditionPreparedMember prepareMember(
            AgentRuntimeEntry entry, int ordinal, long memberSeed, long nowMs) throws Exception;

    void tickCombat(List<Character> members, EventInstanceManager event, long nowMs);

    /** Called once after the event signals clear and before the shared lobby exits it. */
    default void beginPostClear(
            List<Character> members, EventInstanceManager event, long nowMs) {
    }

    /**
     * Advances an expedition-specific reward/clear-room flow. Returning true lets
     * the shared lobby perform its normal event cleanup and return verification.
     */
    default boolean tickPostClear(
            List<Character> members, EventInstanceManager event, long nowMs) {
        return true;
    }

    List<String> battleStatus(Character leader);

    default List<String> rosterSummary() {
        return List.of();
    }

    /** Entrance portal used only by fixture quick mode; ordinary travel remains map-authored. */
    default int quickEntryPortalId() {
        return 0;
    }

    /** Compact spawn spacing around the selected quick-mode entry portal. */
    default int quickEntrySpacingPx() {
        return 34;
    }

    /** Horizontal spacing around the registration NPC after members arrive naturally. */
    default int lobbyRallySpacingPx() {
        return 34;
    }
}
