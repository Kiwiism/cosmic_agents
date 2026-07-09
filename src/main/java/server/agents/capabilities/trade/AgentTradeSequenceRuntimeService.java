package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import server.agents.capabilities.inventory.AgentInventoryRuntime;
import server.agents.integration.cosmic.CosmicAgentServerAdapter;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

public final class AgentTradeSequenceRuntimeService {
    private AgentTradeSequenceRuntimeService() {
    }

    public static void startTradeSequence(String category,
                                          Character recipient,
                                          List<Item> items,
                                          int mesos,
                                          boolean singleBatch,
                                          AgentRuntimeEntry entry,
                                          Character agent,
                                          Runnable cancelUnavailableTrade) {
        AgentTradeSequenceOrchestrator.startTradeSequence(
                category,
                recipient,
                items,
                mesos,
                singleBatch,
                entry,
                agent,
                sequenceCallbacks(entry, agent, cancelUnavailableTrade));
    }

    public static void openTradeBatch(AgentRuntimeEntry entry,
                                      Character agent,
                                      List<Item> items,
                                      int mesos,
                                      Runnable cancelUnavailableTrade) {
        AgentTradeSequenceOrchestrator.openTradeBatch(
                entry,
                agent,
                items,
                mesos,
                sequenceCallbacks(entry, agent, cancelUnavailableTrade));
    }

    static AgentTradeSequenceOrchestrator.SequenceCallbacks sequenceCallbacks(AgentRuntimeEntry entry,
                                                                              Character agent,
                                                                              Runnable cancelUnavailableTrade) {
        return AgentTradeSequenceCallbackService.sequenceCallbacks(
                () -> AgentTradeRecipientService.resolveTradeRecipient(entry, agent),
                cancelUnavailableTrade,
                () -> CosmicAgentServerAdapter.INSTANCE.trade().startTrade(agent),
                CosmicAgentServerAdapter.INSTANCE.trade()::inviteTrade,
                AgentTradeDialogueService::invitationReply,
                message -> AgentInventoryRuntime.replyNow(entry, message));
    }
}
