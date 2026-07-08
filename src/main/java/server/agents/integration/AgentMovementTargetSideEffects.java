package server.agents.integration;

import server.agents.capabilities.movement.AgentMovementPhysicsConfig;

import server.agents.capabilities.movement.AgentMovementTargetSnapshot;
import server.agents.runtime.AgentFollowTargetPositionService;
import server.agents.runtime.AgentFormationService;
import server.agents.runtime.AgentRuntimeConfig;
import server.agents.runtime.AgentTargetSnapshot;
import server.agents.runtime.AgentTargetSnapshotService;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.List;

/**
 * Gateway for AgentTargetSnapshot reads while target resolution continues to be
 * split into Agent runtime services.
 */
public final class AgentMovementTargetSideEffects {
    private static final int PLATFORM_EDGE_INSET_PX = 12;

    private AgentMovementTargetSideEffects() {
    }

    public static AgentMovementTargetSnapshot captureTargetSnapshot(AgentRuntimeEntry entry) {
        return from(entry, captureAgentTargetSnapshot(entry));
    }

    public static AgentMovementTargetSnapshot captureTargetSnapshot(AgentRuntimeEntry entry, Point rawTargetPos) {
        AgentTargetSnapshot snapshot = captureAgentTargetSnapshot(entry);
        if (rawTargetPos == null || rawTargetPos.equals(snapshot.primaryTargetPos())) {
            return from(entry, snapshot);
        }
        return from(entry, new AgentTargetSnapshot(
                snapshot.formation(),
                snapshot.rawOwnerPos(),
                snapshot.followAnchorPos(),
                snapshot.followAnchorName(),
                snapshot.followBasePos(),
                snapshot.followTargetPos(),
                snapshot.moveTargetPos(),
                snapshot.farmAnchorPos(),
                snapshot.grindTargetPos(),
                new Point(rawTargetPos),
                "nav-input"));
    }

    public static AgentMovementTargetSnapshot from(AgentRuntimeEntry entry, AgentTargetSnapshot snapshot) {
        return new AgentMovementTargetSnapshot(
                snapshot.formation().type().name(),
                snapshot.formation().px(),
                snapshot.formation().snapRange(),
                snapshot.rawOwnerPos(),
                snapshot.followAnchorPos(),
                snapshot.followAnchorName(),
                snapshot.followBasePos(),
                snapshot.followTargetPos(),
                snapshot.moveTargetPos(),
                snapshot.farmAnchorPos(),
                snapshot.grindTargetPos(),
                snapshot.primaryTargetPos(),
                snapshot.primaryTargetSource(),
                snapshot.steeringTargetPos(entry),
                snapshot.steeringTargetSource(entry));
    }

    private static AgentTargetSnapshot captureAgentTargetSnapshot(AgentRuntimeEntry entry) {
        client.Character leader = AgentRuntimeIdentityRuntime.owner(entry);
        List<? extends AgentRuntimeEntry> siblingEntries = leader == null
                ? List.of()
                : AgentBotSessionLifecycleSideEffects.getBotEntries(leader.getId());
        return AgentTargetSnapshotService.capture(
                entry,
                siblingEntries,
                AgentFormationService.formationsByLeaderId(),
                defaultFormationState(),
                (followBase, followAnchor, followAnchorPos, snapRange, map) ->
                        AgentFollowTargetPositionService.resolve(
                                followBase, followAnchor, followAnchorPos, snapRange, map, PLATFORM_EDGE_INSET_PX));
    }

    private static AgentFormationService.FormationState defaultFormationState() {
        return AgentFormationService.defaultStagger(
                AgentRuntimeConfig.cfg.FOLLOW_STAGGER,
                AgentMovementPhysicsConfig.configuredFollowYCap());
    }
}
