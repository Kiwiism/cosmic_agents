package server.agents.capabilities.movement;

import client.Character;
import constants.game.CharacterStance;
import server.agents.capabilities.social.airshow.AgentAirshowStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/** Common tick tail that clears a stale client-visible moving pose once. */
public final class AgentMovementSettleService {
    private AgentMovementSettleService() {
    }

    public static void beginTick(AgentRuntimeEntry entry) {
        if (entry != null) {
            AgentMovementBroadcastStateRuntime.beginTick(entry);
        }
    }

    public static void settleIfNeeded(AgentRuntimeEntry entry) {
        if (!shouldSettle(entry)) {
            return;
        }
        AgentGroundMovementRuntimeService.tickGrounded(entry, null);
    }

    static boolean shouldSettle(AgentRuntimeEntry entry) {
        if (entry == null
                || AgentMovementBroadcastStateRuntime.reconciledThisTick(entry)
                || !AgentMovementBroadcastStateRuntime.valid(entry)
                || (AgentMovementBroadcastStateRuntime.lastVelocityX(entry) == 0
                && AgentMovementBroadcastStateRuntime.lastVelocityY(entry) == 0
                && !CharacterStance.isWalking(AgentMovementBroadcastStateRuntime.lastStance(entry)))
                || AgentMovementStateRuntime.inAir(entry)
                || AgentMovementStateRuntime.climbing(entry)
                || AgentAirshowStateRuntime.active(entry)) {
            return false;
        }
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        return agent != null && agent.getMap() != null && agent.getHp() > 0;
    }
}
