package server.agents.capabilities.navigation;

import client.Character;
import constants.game.CharacterStance;
import server.agents.capabilities.movement.AgentGroundCollisionService;
import server.agents.integration.AgentClimbStateRuntime;
import server.agents.integration.AgentFarmAnchorStateRuntime;
import server.agents.integration.AgentModeStateRuntime;
import server.agents.integration.AgentMoveTargetStateRuntime;
import server.agents.integration.AgentMovementStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.AgentBotSessionLifecycleSideEffects;
import server.agents.integration.AgentShopStateRuntime;
import server.agents.runtime.AgentFollowAnchorService;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;
import server.maps.Rope;

import java.awt.Point;
import java.util.List;

/**
 * Agent-owned seam for navigation region classification while path internals migrate.
 */
public final class AgentNavigationRegionService {
    private AgentNavigationRegionService() {
    }

    public static int resolveCurrentRegionId(AgentNavigationGraph graph,
                                             AgentRuntimeEntry entry,
                                             MapleMap map,
                                             Point botPos) {
        if (AgentClimbStateRuntime.climbing(entry) || (AgentRuntimeIdentityRuntime.hasBot(entry) && CharacterStance.isClimbing(AgentRuntimeIdentityRuntime.bot(entry).getStance()))) {
            // Rope climbing state is authoritative. Ground lookup below a rope often resolves to
            // the nearby platform instead of the rope region, which can replan from the wrong side
            // of the rope and bounce between entry/exit climb edges.
            Rope climbRope = AgentClimbStateRuntime.climbRope(entry);
            int ropeX = climbRope != null ? climbRope.x() : botPos.x;
            int ropeRegionId = graph.findRopeRegionId(new Point(ropeX, botPos.y));
            if (ropeRegionId >= 0) {
                return ropeRegionId;
            }
        }
        if (AgentMovementStateRuntime.inAir(entry)) {
            // Airborne points do not have a meaningful "current region". A ground lookup from an
            // in-flight point resolves to whatever foothold is below the arc, which can be an
            // unrelated upper platform. That makes runtime navigation discard the committed jump
            // edge even though the authored graph and ballistic landing simulation still agree.
            return -1;
        }
        return graph.findRegionId(map, botPos);
    }

    public static int resolveTargetRegionId(AgentNavigationGraph graph,
                                            AgentRuntimeEntry entry,
                                            MapleMap map,
                                            Point targetPos) {
        if (targetPos == null) {
            return -1;
        }

        Character owner = AgentRuntimeIdentityRuntime.owner(entry);
        List<? extends AgentRuntimeEntry> siblingEntries = owner == null
                ? List.of()
                : AgentBotSessionLifecycleSideEffects.getBotEntries(owner.getId());
        Character followAnchor = AgentFollowAnchorService.resolve(entry, owner, siblingEntries);
        if (AgentModeStateRuntime.following(entry)
                && !AgentMoveTargetStateRuntime.hasMoveTarget(entry)
                && !AgentFarmAnchorStateRuntime.hasFarmAnchor(entry)
                && !AgentShopStateRuntime.shopVisitPending(entry)
                && !AgentModeStateRuntime.grinding(entry)
                && followAnchor != null
                && followAnchor.getMap() == map) {
            // Follow mode + owner climbing: prioritise a rope target. The follow
            // resolver may have already snapped targetPos to a rope's X, so the
            // exact equality check below would miss - explicitly look for a rope
            // at targetPos, and fall back to the follow anchor's own rope region if none
            // is found there. This keeps the bot climbing onto rope alongside
            // the anchor instead of clamping to the platform below the rope.
            if (CharacterStance.isClimbing(followAnchor.getStance())) {
                int ropeRegionId = graph.findRopeRegionId(targetPos);
                if (ropeRegionId >= 0) {
                    return ropeRegionId;
                }
                return resolveCharacterRegionId(graph, map, followAnchor);
            }
            if (targetPos.equals(followAnchor.getPosition())) {
                return resolveCharacterRegionId(graph, map, followAnchor);
            }
        }

        return resolvePointTargetRegionId(graph, map, targetPos);
    }

    public static int resolveCharacterRegionId(AgentNavigationGraph graph,
                                               MapleMap map,
                                               Character character) {
        if (character == null) {
            return -1;
        }

        Point position = character.getPosition();
        if (position == null) {
            return -1;
        }

        if (CharacterStance.isClimbing(character.getStance())) {
            int ropeRegionId = graph.findRopeRegionId(position);
            if (ropeRegionId >= 0) {
                return ropeRegionId;
            }
        }

        return resolvePointTargetRegionId(graph, map, position);
    }

    public static int resolvePointTargetRegionId(AgentNavigationGraph graph,
                                                 MapleMap map,
                                                 Point position) {
        int ropeRegionId = graph.findRopeRegionId(position);
        if (ropeRegionId >= 0 && shouldPreferRopeRegion(map, position)) {
            return ropeRegionId;
        }
        return graph.findRegionId(map, position);
    }

    private static boolean shouldPreferRopeRegion(MapleMap map, Point position) {
        return AgentGroundCollisionService.isGroundFarBelow(map, position);
    }
}
