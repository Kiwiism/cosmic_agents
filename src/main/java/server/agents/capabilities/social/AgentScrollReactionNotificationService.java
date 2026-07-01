package server.agents.capabilities.social;

import client.Character;
import client.inventory.Equip;
import server.agents.integration.AgentBotManagerSchedulerRuntime;
import server.agents.runtime.AgentRuntimeRegistry;

public final class AgentScrollReactionNotificationService {
    private AgentScrollReactionNotificationService() {
    }

    public static void notifyNearbyAgentsOfScroll(Character source,
                                                  Equip.ScrollResult result,
                                                  int scrollItemId,
                                                  long delayMs) {
        AgentBotManagerSchedulerRuntime.afterDelay(Math.max(0L, delayMs), () ->
                AgentScrollReactionService.handleScrollEvent(
                        source, result, scrollItemId, AgentRuntimeRegistry.entriesByLeaderId().values()));
    }
}
