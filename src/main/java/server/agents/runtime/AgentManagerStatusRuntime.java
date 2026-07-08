package server.agents.runtime;


import client.Character;
import server.agents.capabilities.dialogue.AgentChatStatusOrchestrator;
import server.agents.capabilities.social.airshow.AgentAirshowStateRuntime;

/**
 * Agent-owned runtime orchestration for status callbacks from tick and
 * lifecycle services.
 */
public final class AgentManagerStatusRuntime {
    private AgentManagerStatusRuntime() {
    }

    public static void scheduleSpawnStatusCheck(AgentRuntimeEntry entry, Character bot, long delayMs) {
        AgentSchedulerRuntime.afterDelay(delayMs, () -> checkManagerStatus(entry, bot));
    }

    public static void checkManagerStatus(AgentRuntimeEntry entry, Character bot) {
        AgentChatStatusOrchestrator.checkBotStatus(entry, bot);
    }

    public static void announceOwnerReturnedFromOffline(AgentRuntimeEntry entry) {
        AgentChatStatusOrchestrator.announceOwnerReturnedFromOffline(entry);
    }

    public static void tickAfkCheck(AgentRuntimeEntry entry, Character owner) {
        AgentChatStatusOrchestrator.tickAfkCheck(entry, owner);
    }

    public static boolean airshowActive(AgentRuntimeEntry entry) {
        return AgentAirshowStateRuntime.active(entry);
    }
}
