package server.agents.capabilities.trade;


import server.agents.runtime.AgentSchedulerRuntime;
import client.Character;
import server.agents.capabilities.dialogue.AgentChatPendingAction;
import server.agents.capabilities.dialogue.AgentChatTransferFlow;
import server.agents.capabilities.dialogue.AgentTradeDialogueClassifier;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy;
import server.agents.integration.AgentReplyRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.capabilities.dialogue.AgentPendingActionStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.async.AgentAsyncTaskGateway;
import server.agents.runtime.async.AgentAsyncWorkKind;


/**
 * Agent-owned transfer chat facade over temporary bot-side inventory/trade side
 * effects.
 */
public final class AgentTransferRuntime {
    private static final String TRANSFER_REQUEST_KEY = "transfer-query";
    private static final long TRADE_COMMAND_PROFILE_WARN_NS = 50_000_000L;

    private record TransferCommandResult(boolean hasItems, int count, long completedAtNs) {}
    private record ItemQueryResult(int count, long completedAtNs) {}

    private AgentTransferRuntime() {
    }

    public static void clearAgentRuntimeState(int agentId) {
        AgentAsyncTaskGateway.runtime().clearSession(agentId);
    }

    public static AgentChatTransferFlow.ItemQueryCallbacks itemQueryCallbacks(AgentRuntimeEntry entry) {
        return itemName -> handleItemQuery(entry, itemName);
    }

    public static void handleTransferCommand(AgentRuntimeEntry entry,
                                             AgentChatTransferFlow.TransferCommand transferCommand,
                                             String message) {
        String category = transferCommand.category();
        if (AgentChatTransferFlow.shouldReplyWithWeirdTransfer(transferCommand, message)) {
            AgentReplyRuntime.replyNow(entry, AgentChatTransferFlow.weirdTransferReply());
        }
        if (transferCommand.mode() == AgentChatTransferFlow.TransferMode.TRADE
                && AgentInventoryTradePolicy.isMesoCategory(category)) {
            Character bot = AgentRuntimeIdentityRuntime.bot(entry);
            AgentSchedulerRuntime.afterRandomDelay(entry, 500, 700,
                    () -> AgentInventoryTransferService.startTradeTransfer(category, entry, bot));
            return;
        }

        scheduleTransferCommandEvaluation(entry, transferCommand, category);
    }

    private static void scheduleTransferCommandEvaluation(AgentRuntimeEntry entry,
                                                          AgentChatTransferFlow.TransferCommand transferCommand,
                                                          String category) {
        Character bot = AgentRuntimeIdentityRuntime.bot(entry);
        if (bot == null) {
            return;
        }

        long replyDelay = AgentSchedulerRuntime.randomDelayMs(500, 700);
        long requestedAt = System.nanoTime();
        AgentAsyncTaskGateway.runtime().submit(
                entry,
                AgentAsyncWorkKind.ECONOMY_ANALYSIS,
                TRANSFER_REQUEST_KEY,
                () -> evaluateTransferCommand(entry, transferCommand, category, bot),
                (completionEntry, completion) -> {
                    if (!completion.succeeded()) {
                        return;
                    }
                    TransferCommandResult result = completion.result();
                    long elapsedMs = (result.completedAtNs() - requestedAt) / 1_000_000L;
                    long remainingDelay = Math.max(0L, replyDelay - elapsedMs);
                    AgentSchedulerRuntime.afterDelay(completionEntry, remainingDelay, () ->
                            applyTransferCommandResult(
                                    completionEntry, transferCommand, category, bot,
                                    completion.requestId(), result));
                });
    }

    private static TransferCommandResult evaluateTransferCommand(AgentRuntimeEntry entry,
                                                                 AgentChatTransferFlow.TransferCommand transferCommand,
                                                                 String category,
                                                                 Character bot) {
        long hasItemsStartedAt = transferCommand.mode() == AgentChatTransferFlow.TransferMode.TRADE
                ? AgentTradeCommandProfiler.startIfProfiled(category)
                : 0L;
        boolean hasItems = AgentInventoryTransferService.hasTransferableItems(category, entry, bot);
        AgentTradeCommandProfiler.logSlowCommand(
                category,
                "hasTransferableItems",
                entry,
                bot,
                hasItemsStartedAt,
                TRADE_COMMAND_PROFILE_WARN_NS,
                org.slf4j.LoggerFactory.getLogger(AgentTransferRuntime.class));
        int count = hasItems && transferCommand.mode() == AgentChatTransferFlow.TransferMode.CHOICE
                ? AgentInventoryTransferService.countTransferableItems(category, entry, bot)
                : 0;
        return new TransferCommandResult(hasItems, count, System.nanoTime());
    }

    private static void applyTransferCommandResult(AgentRuntimeEntry entry,
                                                   AgentChatTransferFlow.TransferCommand transferCommand,
                                                   String category,
                                                   Character bot,
                                                   long requestId,
                                                   TransferCommandResult result) {
        if (!AgentAsyncTaskGateway.runtime().isLatest(
                entry, AgentAsyncWorkKind.ECONOMY_ANALYSIS, TRANSFER_REQUEST_KEY, requestId)) {
            return;
        }
        applyTransferResultDecision(entry, bot, category, AgentChatTransferFlow.transferResult(
                transferCommand, result.hasItems(), result.count()));
    }

    private static void handleItemQuery(AgentRuntimeEntry entry, String itemName) {
        String category = AgentTradeDialogueClassifier.namedItemCategory(itemName);
        Character bot = AgentRuntimeIdentityRuntime.bot(entry);
        if (bot == null) {
            return;
        }

        long replyDelay = AgentSchedulerRuntime.randomDelayMs(500, 700);
        long requestedAt = System.nanoTime();
        AgentAsyncTaskGateway.runtime().submit(
                entry,
                AgentAsyncWorkKind.ECONOMY_ANALYSIS,
                TRANSFER_REQUEST_KEY,
                () -> new ItemQueryResult(
                        AgentInventoryTransferService.countTransferableItems(category, entry, bot),
                        System.nanoTime()),
                (completionEntry, completion) -> {
                    if (!completion.succeeded()) {
                        return;
                    }
                    ItemQueryResult result = completion.result();
                    long elapsedMs = (result.completedAtNs() - requestedAt) / 1_000_000L;
                    long remainingDelay = Math.max(0L, replyDelay - elapsedMs);
                    AgentSchedulerRuntime.afterDelay(completionEntry, remainingDelay, () ->
                            applyItemQueryResult(
                                    completionEntry, category, bot, completion.requestId(), result));
                });
    }

    private static void applyItemQueryResult(AgentRuntimeEntry entry,
                                             String category,
                                             Character bot,
                                             long requestId,
                                             ItemQueryResult result) {
        if (!AgentAsyncTaskGateway.runtime().isLatest(
                entry, AgentAsyncWorkKind.ECONOMY_ANALYSIS, TRANSFER_REQUEST_KEY, requestId)) {
            return;
        }
        applyTransferResultDecision(entry, bot, category, AgentChatTransferFlow.itemQueryResult(category, result.count()));
    }

    private static void applyTransferResultDecision(AgentRuntimeEntry entry,
                                                    Character bot,
                                                    String category,
                                                    AgentChatTransferFlow.TransferResultDecision decision) {
        switch (decision.action()) {
            case REPLY -> AgentReplyRuntime.replyNow(entry, decision.reply());
            case START_TRADE -> AgentInventoryTransferService.startTradeTransfer(category, entry, bot);
            case PROMPT_ITEM_CHOICE -> {
                AgentPendingActionStateRuntime.setPendingAction(entry, AgentChatPendingAction.ITEM_CHOICE);
                AgentPendingActionStateRuntime.setPendingDropCategory(entry, decision.category());
                AgentReplyRuntime.replyNow(entry, decision.reply());
            }
        }
    }
}
