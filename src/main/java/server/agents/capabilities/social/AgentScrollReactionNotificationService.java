package server.agents.capabilities.social;

import client.Character;
import client.inventory.Equip;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.runtime.AgentRuntimeRegistry;

public final class AgentScrollReactionNotificationService {
    private AgentScrollReactionNotificationService() {
    }

    public static void notifyNearbyAgentsOfScroll(Character source,
                                                  Equip.ScrollResult result,
                                                  int scrollItemId,
                                                  long delayMs) {
        AgentSchedulerRuntime.afterDelay(Math.max(0L, delayMs), () ->
                AgentScrollReactionService.handleScrollEvent(
                        source,
                        result,
                        scrollItemId,
                        AgentRuntimeRegistry.entriesByLeaderId().values(),
                        AgentInventoryGatewayRuntime.inventory()));
    }
}
