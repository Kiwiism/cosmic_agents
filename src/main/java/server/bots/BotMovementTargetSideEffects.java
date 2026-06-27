package server.bots;

import server.agents.capabilities.movement.AgentMovementTargetSnapshot;

/**
 * Temporary bot-side gateway for TargetSnapshot reads while BotManager owns
 * target resolution.
 */
public final class BotMovementTargetSideEffects {
    private BotMovementTargetSideEffects() {
    }

    public static AgentMovementTargetSnapshot captureTargetSnapshot(BotEntry entry) {
        return from(entry, BotManager.getInstance().captureTargetSnapshot(entry));
    }

    static AgentMovementTargetSnapshot from(BotEntry entry, BotManager.TargetSnapshot snapshot) {
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
