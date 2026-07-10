package server.agents.runtime;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatStatusOrchestrator;

/**
 * Coordinates delayed lifecycle status checks through the runtime scheduler and
 * dialogue capability.
 */
public final class AgentLifecycleStatusCoordinator {
    private AgentLifecycleStatusCoordinator() {
    }

    public static void scheduleSpawnStatusCheck(AgentRuntimeEntry entry,
                                                Character agent,
                                                long delayMs) {
        AgentSchedulerRuntime.afterDelay(
                entry,
                delayMs,
                () -> AgentChatStatusOrchestrator.checkBotStatus(entry, agent));
    }
}
