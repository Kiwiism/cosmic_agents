package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;

import java.util.List;

/**
 * Runtime wiring for target snapshot and follow-anchor lookup over the current
 * BotEntry-backed registry.
 */
public final class AgentTargetSnapshotRuntime {
    private static final int PLATFORM_EDGE_INSET_PX = 12;

    private AgentTargetSnapshotRuntime() {
    }

    public static Character resolveFollowAnchor(AgentRuntimeEntry entry, Character leader) {
        List<? extends AgentRuntimeEntry> siblingEntries = leader == null
                ? List.of()
                : AgentRuntimeRegistry.agentEntriesForLeader(leader.getId());
        return AgentFollowAnchorService.resolve(entry, leader, siblingEntries);
    }

    public static AgentTargetSnapshot captureTargetSnapshot(AgentRuntimeEntry entry) {
        Character leader = AgentBotRuntimeIdentityRuntime.owner(entry);
        List<? extends AgentRuntimeEntry> siblingEntries = leader == null
                ? List.of()
                : AgentRuntimeRegistry.agentEntriesForLeader(leader.getId());
        return AgentTargetSnapshotService.capture(
                entry,
                siblingEntries,
                AgentFormationService.formationsByLeaderId(),
                AgentFormationRuntime.defaultFormationState(),
                (followBase, followAnchor, followAnchorPos, snapRange, map) ->
                        AgentFollowTargetPositionService.resolve(
                                followBase, followAnchor, followAnchorPos, snapRange, map, PLATFORM_EDGE_INSET_PX));
    }
}
