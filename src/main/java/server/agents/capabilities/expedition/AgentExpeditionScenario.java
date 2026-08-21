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

    List<String> battleStatus(Character leader);

    default List<String> rosterSummary() {
        return List.of();
    }
}
