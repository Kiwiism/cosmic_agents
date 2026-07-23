package server.agents.capabilities.movement;

import client.Character;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.AgentRelationshipRuntime;
import server.agents.capabilities.follow.AgentFollowTargetPositionService;
import server.agents.runtime.AgentRuntimeConfig;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.awt.Point;
import java.util.List;

/**
 * Agent-owned movement target snapshot service.
 */
public final class AgentMovementTargetRuntime {
    private static final int PLATFORM_EDGE_INSET_PX = config.AgentTuning.intValue("server.agents.capabilities.movement.AgentMovementTargetRuntime.PLATFORM_EDGE_INSET_PX");

    private AgentMovementTargetRuntime() {
    }

    public static AgentMovementTargetSnapshot snapshot(AgentRuntimeEntry entry) {
        return captureTargetSnapshot(entry);
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
        List<? extends AgentRuntimeEntry> siblingEntries =
                AgentRuntimeRegistry.entriesForCohort(AgentRelationshipRuntime.cohortId(entry));
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
