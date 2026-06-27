package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatPendingAction;
import server.agents.capabilities.dialogue.AgentChatTransferFlow;
import server.agents.capabilities.dialogue.AgentTradeDialogueClassifier;
import server.bots.BotEntry;
import server.bots.BotInventoryManager;

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

    private record TransferCommandResult(boolean hasItems, int count) {}
    private record ItemQueryResult(int count) {}

    private AgentBotTransferRuntime() {
    }

    public static AgentChatTransferFlow.ItemQueryCallbacks itemQueryCallbacks(BotEntry entry) {
        return itemName -> handleItemQuery(entry, itemName);
    }

    public static void handleTransferCommand(BotEntry entry,
                                             AgentChatTransferFlow.TransferCommand transferCommand,
                                             String message) {
        String category = transferCommand.category();
        if (AgentChatTransferFlow.shouldReplyWithWeirdTransfer(transferCommand, message)) {
            AgentBotTransferReplyRuntime.replyNow(entry, AgentChatTransferFlow.weirdTransferReply());
        }
        if (transferCommand.mode() == AgentChatTransferFlow.TransferMode.TRADE
                && BotInventoryManager.isMesoCategory(category)) {
            AgentBotTransferSchedulerRuntime.afterRandomDelay(500, 700,
                    () -> BotInventoryManager.startTradeTransfer(category, entry, entry.bot()));
            return;
        }

        scheduleTransferCommandEvaluation(entry, transferCommand, category);
    }

    private static void scheduleTransferCommandEvaluation(BotEntry entry,
                                                          AgentChatTransferFlow.TransferCommand transferCommand,
                                                          String category) {
        Character bot = entry.bot();
        if (bot == null) {
            return;
        }

        int requestId = nextTransferRequestId(bot);
        long replyDelay = AgentBotTransferSchedulerRuntime.randomDelayMs(500, 700);
        long requestedAt = System.nanoTime();
        CompletableFuture
                .supplyAsync(() -> evaluateTransferCommand(entry, transferCommand, category, bot), TRADE_COMMAND_EXECUTOR)
                .thenAccept(result -> {
                    long elapsedMs = (System.nanoTime() - requestedAt) / 1_000_000L;
                    long remainingDelay = Math.max(0L, replyDelay - elapsedMs);
                    AgentBotTransferSchedulerRuntime.afterDelay(remainingDelay, () ->
                            applyTransferCommandResult(entry, transferCommand, category, bot, requestId, result));
                });
    }

    private static TransferCommandResult evaluateTransferCommand(BotEntry entry,
                                                                 AgentChatTransferFlow.TransferCommand transferCommand,
                                                                 String category,
                                                                 Character bot) {
        long hasItemsStartedAt = transferCommand.mode() == AgentChatTransferFlow.TransferMode.TRADE
                && BotInventoryManager.profileTradeCategory(category)
                ? System.nanoTime() : 0L;
        boolean hasItems = BotInventoryManager.hasTransferableItems(category, entry, bot);
        BotInventoryManager.logSlowTradeCommand(category, "hasTransferableItems", entry, bot, hasItemsStartedAt);
        int count = hasItems && transferCommand.mode() == AgentChatTransferFlow.TransferMode.CHOICE
                ? BotInventoryManager.countTransferableItems(category, entry, bot)
                : 0;
        return new TransferCommandResult(hasItems, count);
    }

    private static void applyTransferCommandResult(BotEntry entry,
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

    private static void handleItemQuery(BotEntry entry, String itemName) {
        String category = AgentTradeDialogueClassifier.namedItemCategory(itemName);
        Character bot = entry.bot();
        if (bot == null) {
            return;
        }

        int requestId = nextTransferRequestId(bot);
        long replyDelay = AgentBotTransferSchedulerRuntime.randomDelayMs(500, 700);
        long requestedAt = System.nanoTime();
        CompletableFuture
                .supplyAsync(() -> new ItemQueryResult(
                        BotInventoryManager.countTransferableItems(category, entry, bot)), TRADE_COMMAND_EXECUTOR)
                .thenAccept(result -> {
                    long elapsedMs = (System.nanoTime() - requestedAt) / 1_000_000L;
                    long remainingDelay = Math.max(0L, replyDelay - elapsedMs);
                    AgentBotTransferSchedulerRuntime.afterDelay(remainingDelay, () ->
                            applyItemQueryResult(entry, category, bot, requestId, result));
                });
    }

    private static void applyItemQueryResult(BotEntry entry,
                                             String category,
                                             Character bot,
                                             int requestId,
                                             ItemQueryResult result) {
        if (!isLatestTransferRequest(bot, requestId)) {
            return;
        }
        applyTransferResultDecision(entry, bot, category, AgentChatTransferFlow.itemQueryResult(category, result.count()));
    }

    private static void applyTransferResultDecision(BotEntry entry,
                                                    Character bot,
                                                    String category,
                                                    AgentChatTransferFlow.TransferResultDecision decision) {
        switch (decision.action()) {
            case REPLY -> AgentBotTransferReplyRuntime.replyNow(entry, decision.reply());
            case START_TRADE -> BotInventoryManager.startTradeTransfer(category, entry, bot);
            case PROMPT_ITEM_CHOICE -> {
                AgentBotPendingActionStateRuntime.setPendingAction(entry, AgentChatPendingAction.ITEM_CHOICE);
                AgentBotPendingActionStateRuntime.setPendingDropCategory(entry, decision.category());
                AgentBotTransferReplyRuntime.replyNow(entry, decision.reply());
            }
        }
    }
}
