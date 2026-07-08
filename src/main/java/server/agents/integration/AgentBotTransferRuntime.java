package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatPendingAction;
import server.agents.capabilities.dialogue.AgentChatTransferFlow;
import server.agents.capabilities.dialogue.AgentTradeDialogueClassifier;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy;
import server.agents.capabilities.trade.AgentInventoryTransferService;
import server.agents.capabilities.trade.AgentTradeCommandProfiler;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent-owned transfer chat facade over temporary bot-side inventory/trade side
 * effects.
 */
public final class AgentBotTransferRuntime {
    private static final ExecutorService TRADE_COMMAND_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread thread = new Thread(r, "bot-trade-command");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<Integer, AtomicInteger> PENDING_TRANSFER_REQUESTS = new ConcurrentHashMap<>();
    private static final long TRADE_COMMAND_PROFILE_WARN_NS = 50_000_000L;

    private record TransferCommandResult(boolean hasItems, int count) {}
    private record ItemQueryResult(int count) {}

    private AgentBotTransferRuntime() {
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
            AgentSchedulerRuntime.afterRandomDelay(500, 700,
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
        CompletableFuture
                .supplyAsync(() -> evaluateTransferCommand(entry, transferCommand, category, bot), TRADE_COMMAND_EXECUTOR)
                .thenAccept(result -> {
                    long elapsedMs = (System.nanoTime() - requestedAt) / 1_000_000L;
                    long remainingDelay = Math.max(0L, replyDelay - elapsedMs);
                    AgentSchedulerRuntime.afterDelay(remainingDelay, () ->
                            applyTransferCommandResult(entry, transferCommand, category, bot, requestId, result));
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
                org.slf4j.LoggerFactory.getLogger(AgentBotTransferRuntime.class));
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
        CompletableFuture
                .supplyAsync(() -> new ItemQueryResult(
                        AgentInventoryTransferService.countTransferableItems(category, entry, bot)), TRADE_COMMAND_EXECUTOR)
                .thenAccept(result -> {
                    long elapsedMs = (System.nanoTime() - requestedAt) / 1_000_000L;
                    long remainingDelay = Math.max(0L, replyDelay - elapsedMs);
                    AgentSchedulerRuntime.afterDelay(remainingDelay, () ->
                            applyItemQueryResult(entry, category, bot, requestId, result));
                });
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
                AgentBotPendingActionStateRuntime.setPendingAction(entry, AgentChatPendingAction.ITEM_CHOICE);
                AgentBotPendingActionStateRuntime.setPendingDropCategory(entry, decision.category());
                AgentReplyRuntime.replyNow(entry, decision.reply());
            }
        }
    }
}
