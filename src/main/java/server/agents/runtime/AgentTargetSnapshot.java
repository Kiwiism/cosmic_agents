package server.agents.runtime;

import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.bots.BotEntry;

import java.awt.Point;

/**
 * Agent-owned read-only target snapshot used by movement, navigation, and tick
 * orchestration while target assembly is still being migrated out of BotManager.
 */
public record AgentTargetSnapshot(AgentFormationService.FormationState formation,
                                  Point rawOwnerPos,
                                  Point followAnchorPos,
                                  String followAnchorName,
                                  Point followBasePos,
                                  Point followTargetPos,
                                  Point moveTargetPos,
                                  Point farmAnchorPos,
                                  Point grindTargetPos,
                                  Point primaryTargetPos,
                                  String primaryTargetSource) {
    public Point steeringTargetPos(BotEntry entry) {
        Point navTargetPos = AgentBotNavigationDebugStateRuntime.navTargetPosition(entry);
        return navTargetPos != null ? navTargetPos : new Point(primaryTargetPos);
    }

    public String steeringTargetSource(BotEntry entry) {
        return AgentBotNavigationDebugStateRuntime.hasNavTargetPosition(entry) ? "nav-waypoint" : primaryTargetSource;
    }
}
