package server.agents.integration;

import server.agents.capabilities.movement.AgentMovementTargetSnapshot;
import server.agents.runtime.AgentTargetSnapshot;
import server.bots.BotEntry;
import server.bots.BotManager;

import java.awt.Point;

/**
 * Temporary bot-side gateway for AgentTargetSnapshot reads while BotManager
 * still assembles target resolution.
 */
public final class AgentBotMovementTargetSideEffects {
    private AgentBotMovementTargetSideEffects() {
    }

    public static AgentMovementTargetSnapshot captureTargetSnapshot(BotEntry entry) {
        return from(entry, BotManager.getInstance().captureTargetSnapshot(entry));
    }

    public static AgentMovementTargetSnapshot captureTargetSnapshot(BotEntry entry, Point rawTargetPos) {
        AgentTargetSnapshot snapshot = BotManager.getInstance().captureTargetSnapshot(entry);
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

    public static AgentMovementTargetSnapshot from(BotEntry entry, AgentTargetSnapshot snapshot) {
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
}
