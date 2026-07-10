package server.agents.capabilities.movement;

import server.agents.capabilities.follow.AgentFollowAnchorService;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.capabilities.combat.AgentGrindTargetStateRuntime;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.capabilities.shop.AgentShopStateRuntime;
import server.life.Monster;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;
import java.util.Map;

public final class AgentTargetSnapshotService {
    @FunctionalInterface
    public interface FollowTargetResolver {
        Point resolve(Point followBase, Character followAnchor, Point followAnchorPos, int snapRange, MapleMap map);
    }

    private AgentTargetSnapshotService() {
    }

    public static AgentTargetSnapshot capture(AgentRuntimeEntry entry,
                                              List<? extends AgentRuntimeEntry> siblingEntries,
                                              Map<Integer, AgentFormationService.FormationState> formationsByLeaderId,
                                              AgentFormationService.FormationState defaultFormation,
                                              FollowTargetResolver followTargetResolver) {
        Character owner = AgentRuntimeIdentityRuntime.owner(entry);
        Character followAnchor = AgentFollowAnchorService.resolve(entry, owner, siblingEntries);
        AgentFormationService.FormationState formation =
                AgentFormationService.stateForEntry(entry, formationsByLeaderId, defaultFormation);
        return capture(entry, followAnchor, formation, followTargetResolver);
    }

    public static AgentTargetSnapshot capture(AgentRuntimeEntry entry,
                                              Character followAnchor,
                                              AgentFormationService.FormationState formation,
                                              FollowTargetResolver followTargetResolver) {
        Character bot = AgentRuntimeIdentityRuntime.bot(entry);
        Character owner = AgentRuntimeIdentityRuntime.owner(entry);
        Point fallbackPos = bot.getPosition();
        Point rawOwnerPos = owner != null ? owner.getPosition() : fallbackPos;
        Point rawFollowAnchorPos = followAnchor != null ? followAnchor.getPosition() : rawOwnerPos;
        String followAnchorName = followAnchor != null ? followAnchor.getName() : "owner";
        Point followBasePos = new Point(
                rawFollowAnchorPos.x + AgentFormationStateRuntime.followOffsetX(entry),
                rawFollowAnchorPos.y);
        Point followTargetPos = followTargetResolver.resolve(
                followBasePos, followAnchor, rawFollowAnchorPos, formation.snapRange(), bot.getMap());
        Point rawShopTargetPos = AgentShopStateRuntime.shopVisitPending(entry)
                ? AgentShopStateRuntime.activeShopTargetPosition(entry)
                : null;
        Point shopTargetPos = rawShopTargetPos == null ? null : new Point(rawShopTargetPos);
        Point moveTargetPos = AgentMoveTargetStateRuntime.moveTarget(entry);
        Point farmAnchorPos = AgentFarmAnchorStateRuntime.farmAnchorInMap(entry, bot.getMapId());
        Monster activeGrindTarget = AgentGrindTargetStateRuntime.activeTargetInMap(entry, bot.getMap());
        Point grindTargetPos = activeGrindTarget == null ? null : new Point(activeGrindTarget.getPosition());
        Point primaryTargetPos;
        String primaryTargetSource;
        if (shopTargetPos != null) {
            primaryTargetPos = shopTargetPos;
            primaryTargetSource = "shop-target";
        } else if (moveTargetPos != null) {
            primaryTargetPos = moveTargetPos;
            primaryTargetSource = "move-target";
        } else if (farmAnchorPos != null) {
            primaryTargetPos = farmAnchorPos;
            primaryTargetSource = "farm-anchor";
        } else if (grindTargetPos != null) {
            primaryTargetPos = grindTargetPos;
            primaryTargetSource = "grind-target";
        } else if (AgentModeStateRuntime.grinding(entry)) {
            primaryTargetPos = fallbackPos;
            primaryTargetSource = "grind-idle";
        } else if (AgentModeStateRuntime.following(entry)) {
            primaryTargetPos = followTargetPos;
            primaryTargetSource = "follow-target";
        } else {
            primaryTargetPos = rawOwnerPos;
            primaryTargetSource = "owner-raw";
        }
        return new AgentTargetSnapshot(
                formation,
                new Point(rawOwnerPos),
                new Point(rawFollowAnchorPos),
                followAnchorName,
                new Point(followBasePos),
                new Point(followTargetPos),
                moveTargetPos,
                farmAnchorPos,
                grindTargetPos,
                new Point(primaryTargetPos),
                primaryTargetSource);
    }
}
