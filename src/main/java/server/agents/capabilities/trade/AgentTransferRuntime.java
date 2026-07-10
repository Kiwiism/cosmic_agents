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
import server.agents.runtime.AgentBoundedExecutorFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent-owned transfer chat facade over temporary bot-side inventory/trade side
 * effects.
 */
public final class AgentTransferRuntime {
    private static final ExecutorService TRADE_COMMAND_EXECUTOR = AgentBoundedExecutorFactory.fixed(
            "bot-trade-command",
            2,
            AgentBoundedExecutorFactory.positiveIntegerProperty("agents.async.trade.queueCapacity", 128));
    private static final Map<Integer, AtomicInteger> PENDING_TRANSFER_REQUESTS = new ConcurrentHashMap<>();
    private static final long TRADE_COMMAND_PROFILE_WARN_NS = 50_000_000L;

    private record TransferCommandResult(boolean hasItems, int count) {}
    private record ItemQueryResult(int count) {}

    private AgentTransferRuntime() {
    }

    public static void clearAgentRuntimeState(int agentId) {
        PENDING_TRANSFER_REQUESTS.remove(agentId);
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

        int requestId = nextTransferRequestId(bot);
        long replyDelay = AgentSchedulerRuntime.randomDelayMs(500, 700);
        long requestedAt = System.nanoTime();
        try {
            CompletableFuture
                    .supplyAsync(() -> evaluateTransferCommand(entry, transferCommand, category, bot), TRADE_COMMAND_EXECUTOR)
                    .thenAccept(result -> {
                        long elapsedMs = (System.nanoTime() - requestedAt) / 1_000_000L;
                        long remainingDelay = Math.max(0L, replyDelay - elapsedMs);
                        AgentSchedulerRuntime.afterDelay(entry, remainingDelay, () ->
                                applyTransferCommandResult(entry, transferCommand, category, bot, requestId, result));
                    });
        } catch (RejectedExecutionException ignored) {
            // A later command remains able to retry once bounded background work drains.
        }
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
        return new TransferCommandResult(hasItems, count);
    }

    private static void applyTransferCommandResult(AgentRuntimeEntry entry,
                                                   AgentChatTransferFlow.TransferCommand transferCommand,
                                                   String category,
                                                   Character bot,
                                                   int requestId,
                                                   TransferCommandResult result) {
        if (!isLatestTransferRequest(bot, requestId)) {
            return;
        }
        applyTransferResultDecision(entry, bot, category, AgentChatTransferFlow.transferResult(
                transferCommand, result.hasItems(), result.count()));
    }

    private static int nextTransferRequestId(Character bot) {
        return PENDING_TRANSFER_REQUESTS
                .computeIfAbsent(bot.getId(), ignored -> new AtomicInteger())
                .incrementAndGet();
    }

    private static boolean isLatestTransferRequest(Character bot, int requestId) {
        AtomicInteger current = PENDING_TRANSFER_REQUESTS.get(bot.getId());
        return current != null && current.get() == requestId;
    }

    private static void handleItemQuery(AgentRuntimeEntry entry, String itemName) {
        String category = AgentTradeDialogueClassifier.namedItemCategory(itemName);
        Character bot = AgentRuntimeIdentityRuntime.bot(entry);
        if (bot == null) {
            return;
        }

        int requestId = nextTransferRequestId(bot);
        long replyDelay = AgentSchedulerRuntime.randomDelayMs(500, 700);
        long requestedAt = System.nanoTime();
        try {
            CompletableFuture
                    .supplyAsync(() -> new ItemQueryResult(
                            AgentInventoryTransferService.countTransferableItems(category, entry, bot)), TRADE_COMMAND_EXECUTOR)
                    .thenAccept(result -> {
                        long elapsedMs = (System.nanoTime() - requestedAt) / 1_000_000L;
                        long remainingDelay = Math.max(0L, replyDelay - elapsedMs);
                        AgentSchedulerRuntime.afterDelay(entry, remainingDelay, () ->
                                applyItemQueryResult(entry, category, bot, requestId, result));
                    });
        } catch (RejectedExecutionException ignored) {
            // A later query remains able to retry once bounded background work drains.
        }
    }

    private static void applyItemQueryResult(AgentRuntimeEntry entry,
                                             String category,
                                             Character bot,
                                             int requestId,
                                             ItemQueryResult result) {
        if (!isLatestTransferRequest(bot, requestId)) {
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
