package server.agents.capabilities.trade;

import client.Character;
import server.Trade;
import server.agents.integration.AgentBotPendingTradeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.function.IntSupplier;

public final class AgentTradeMesoAddService {
    private AgentTradeMesoAddService() {
    }

    public static boolean handlePendingMeso(AgentRuntimeEntry entry,
                                            Character agent,
                                            Trade trade,
                                            Runnable insufficientMesoCancel,
                                            IntSupplier addDelayMs) {
        if (!AgentBotPendingTradeStateRuntime.hasMesoToAdd(entry)) {
            return false;
        }
        int mesos = AgentBotPendingTradeStateRuntime.meso(entry);
        if (agent.getMeso() < mesos) {
            insufficientMesoCancel.run();
            return true;
        }

        trade.setMeso(mesos);
        AgentBotPendingTradeStateRuntime.markMesoAdded(entry);
        AgentBotPendingTradeStateRuntime.setTimerMs(entry, addDelayMs.getAsInt());
        return true;
    }
}
