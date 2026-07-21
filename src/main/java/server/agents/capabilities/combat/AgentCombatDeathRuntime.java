package server.agents.capabilities.combat;

import server.agents.capabilities.movement.AgentMovementBroadcastService;

import client.Character;
import server.agents.capabilities.movement.AgentMovementPoseService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.events.AgentEventPriority;
import server.agents.operations.events.AgentLifeStateChangedEvent;
import server.agents.operations.events.AgentOperationalEventPublisher;

public final class AgentCombatDeathRuntime {
    private AgentCombatDeathRuntime() {
    }

    public static void enterDeadState(AgentRuntimeEntry entry, Character bot,
                                      boolean announceDeath,
                                      AgentCombatConfig.Config config) {
        AgentCombatActionStateRuntime.clearActionState(entry);
        AgentMovementPoseService.markDead(entry, bot);
        AgentMovementBroadcastService.broadcastMovement(entry);
        AgentDeathStateRuntime.enterDeadState(entry, System.currentTimeMillis(), config.BOT_DEAD_MS);
        AgentOperationalEventPublisher.publish(entry,
                objectiveId -> new AgentLifeStateChangedEvent(
                        bot.getId(), System.currentTimeMillis(), "ALIVE", "DEAD",
                        bot.getMapId(), announceDeath, objectiveId),
                AgentEventPriority.CRITICAL);
    }
}
