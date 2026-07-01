package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotFarmAnchorStateRuntime;
import server.agents.integration.AgentBotFormationStateRuntime;
import server.agents.integration.AgentBotGrindTargetStateRuntime;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.integration.AgentBotShopStateRuntime;
import server.bots.BotEntry;
import server.life.Monster;
import server.maps.MapleMap;

import java.awt.Point;

public final class AgentTargetSnapshotService {
    @FunctionalInterface
    public interface FollowTargetResolver {
        Point resolve(Point followBase, Character followAnchor, Point followAnchorPos, int snapRange, MapleMap map);
    }

    private AgentTargetSnapshotService() {
    }

    public static AgentTargetSnapshot capture(BotEntry entry,
                                              Character followAnchor,
                                              AgentFormationService.FormationState formation,
                                              FollowTargetResolver followTargetResolver) {
        Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        Point fallbackPos = bot.getPosition();
        Point rawOwnerPos = owner != null ? owner.getPosition() : fallbackPos;
        Point rawFollowAnchorPos = followAnchor != null ? followAnchor.getPosition() : rawOwnerPos;
        String followAnchorName = followAnchor != null ? followAnchor.getName() : "owner";
        Point followBasePos = new Point(
                rawFollowAnchorPos.x + AgentBotFormationStateRuntime.followOffsetX(entry),
                rawFollowAnchorPos.y);
        Point followTargetPos = followTargetResolver.resolve(
                followBasePos, followAnchor, rawFollowAnchorPos, formation.snapRange(), bot.getMap());
        Point rawShopTargetPos = AgentBotShopStateRuntime.shopVisitPending(entry)
                ? AgentBotShopStateRuntime.activeShopTargetPosition(entry)
                : null;
        Point shopTargetPos = rawShopTargetPos == null ? null : new Point(rawShopTargetPos);
        Point moveTargetPos = AgentBotMoveTargetStateRuntime.moveTarget(entry);
        Point farmAnchorPos = AgentBotFarmAnchorStateRuntime.farmAnchorInMap(entry, bot.getMapId());
        Monster activeGrindTarget = AgentBotGrindTargetStateRuntime.activeTargetInMap(entry, bot.getMap());
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
        } else if (AgentBotModeStateRuntime.grinding(entry)) {
            primaryTargetPos = fallbackPos;
            primaryTargetSource = "grind-idle";
        } else if (AgentBotModeStateRuntime.following(entry)) {
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
