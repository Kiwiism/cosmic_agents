package server.agents.capabilities.trade;

import client.Character;
import server.agents.integration.AgentPendingTradeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

public final class AgentTradeResetService {
    private AgentTradeResetService() {
    }

    public static void reset(AgentRuntimeEntry entry,
                             Character agent,
                             Runnable restoreTemporarilyUnequippedItems,
                             Runnable clearManualTradeState,
                             Runnable refillEquipmentSlots) {
        boolean hadRestores = AgentPendingTradeStateRuntime.hasRestoreSlots(entry);
        restoreTemporarilyUnequippedItems.run();
        clearManualTradeState.run();
        AgentTradeStateService.clearSequence(entry);
        if (hadRestores && agent != null) {
            refillEquipmentSlots.run();
        }
    }
}
