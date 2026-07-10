package server.agents.runtime;

import client.Character;
import server.agents.capabilities.follow.AgentFollowAnchorService;
import server.agents.capabilities.follow.AgentFollowTargetPositionService;
import server.agents.capabilities.movement.AgentFormationRuntime;
import server.agents.capabilities.movement.AgentFormationService;
import server.agents.capabilities.movement.AgentTargetSnapshot;
import server.agents.capabilities.movement.AgentTargetSnapshotService;
import server.agents.integration.AgentRuntimeIdentityRuntime;

import java.util.List;

/**
 * Coordinates target snapshots and follow-anchor lookup over the active runtime
 * registry, formation state, and follow capability.
 */
public final class AgentTargetSnapshotCoordinator {
    private static final int PLATFORM_EDGE_INSET_PX = 12;

    private AgentTargetSnapshotCoordinator() {
    }

    public static Character resolveFollowAnchor(AgentRuntimeEntry entry, Character leader) {
        List<? extends AgentRuntimeEntry> siblingEntries = leader == null
                ? List.of()
                : AgentRuntimeRegistry.agentEntriesForLeader(leader.getId());
        return AgentFollowAnchorService.resolve(entry, leader, siblingEntries);
    }

    public static AgentTargetSnapshot captureTargetSnapshot(AgentRuntimeEntry entry) {
        Character leader = AgentRuntimeIdentityRuntime.owner(entry);
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
