package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import config.AgentYamlConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ItemInformationProvider;
import server.agents.capabilities.trade.jev.AgentOfferReplyJudge;
import server.agents.commands.AgentCommandTargetResolver;
import server.agents.integration.typesafe.JevClient;
import server.agents.integration.typesafe.JevJudgmentMode;
import server.agents.runtime.async.AgentAsyncTaskGateway;
import server.agents.runtime.async.AgentAsyncWorkKind;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentMailboxRuntime;
import server.agents.runtime.mailbox.AgentMailboxOptions;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AgentPendingOfferChatRouteService {
    private static final Logger log = LoggerFactory.getLogger(AgentPendingOfferChatRouteService.class);

    private AgentPendingOfferChatRouteService() {
    }

    public static <E extends AgentRuntimeEntry> boolean handlePendingOfferResponse(Collection<List<E>> entryGroups,
                                                                                  Character speaker,
                                                                                  String message) {
        return AgentPendingOfferResponseService.handlePendingOfferResponse(
                entryGroups,
                speaker,
                message,
                new AgentPendingOfferResponseService.Hooks<E>(
                        AgentPendingOfferChatRouteService::expirePendingOffer,
                        AgentPendingOfferChatRouteService::isPendingOfferTarget,
                        AgentCommandTargetResolver::resolveTargetedAgent,
                        AgentPendingOfferChatRouteService::handlePendingOfferResponse,
                        (target, feedback) -> target.dropMessage(5, feedback)));
    }

    private static void expirePendingOffer(AgentRuntimeEntry entry) {
        AgentMailboxRuntime.dispatch(
                entry,
                ignored -> {
                    AgentOfferService.expirePendingOffer(entry);
                    return null;
                },
                AgentMailboxOptions.coalesceLatest("pending-offer-expiry"));
    }

    private static boolean handlePendingOfferResponse(
            AgentRuntimeEntry entry,
            Character speaker,
            String message) {
        if (!AgentOfferService.isPendingOfferResponse(message)) {
            judgeFreeFormReply(entry, speaker, message);
            return false;
        }
        AgentMailboxRuntime.dispatch(entry, ignored ->
                AgentOfferService.handlePendingOfferResponse(entry, speaker, message));
        return true;
    }

    /**
     * The yes/no regexes missed while an offer to this speaker is pending: optionally let
     * TypeSafe classify the reply and, in LIVE mode, replay it as the canonical yes or no.
     * The message keeps flowing through the other chat routes meanwhile.
     */
    private static void judgeFreeFormReply(AgentRuntimeEntry entry, Character speaker, String message) {
        JevJudgmentMode mode = JevJudgmentMode.parse(
                AgentYamlConfig.config.agent.AGENT_TYPESAFE_TRADE_REPLY_MODE);
        if (!mode.asks() || speaker == null || message == null || message.isBlank()) {
            return;
        }
        JevClient client = JevClient.runtime();
        if (!client.available()) {
            return;
        }
        Item item = AgentOfferStateRuntime.pendingLootOfferItem(entry);
        long expiresAt = AgentOfferStateRuntime.pendingLootOfferExpiresAt(entry);
        boolean botRequesting = AgentOfferStateRuntime.pendingLootOfferBotRequesting(entry);
        String itemName = item == null ? "an item" : ItemInformationProvider.getInstance().getName(item.getItemId());
        Map<String, Object> state = AgentOfferReplyJudge.state(
                message, speaker.getName(), AgentRuntimeIdentityRuntime.botName(entry),
                itemName == null ? "an item" : itemName,
                botRequesting);
        AgentOfferReplyJudge judge = new AgentOfferReplyJudge(client);
        AgentAsyncTaskGateway.runtime().submit(
                entry,
                AgentAsyncWorkKind.SYSTEM_ONE_NETWORK,
                "typesafe-offer-reply",
                () -> judge.judge(state),
                (completionEntry, completion) -> {
                    if (!completion.succeeded() || !(completion.result() instanceof Optional<?> result)
                            || result.isEmpty() || !(result.get() instanceof AgentOfferReplyJudge.Decision decision)) {
                        return;
                    }
                    log.info("[typesafe-offer] mode={} speaker={} message=\"{}\" {}",
                            mode, speaker.getName(), message, decision.summary());
                    if (!mode.acts() || !decision.actionable() || !isPendingOfferTarget(completionEntry, speaker)) {
                        return;
                    }
                    String canonical = decision.reply().canonicalReply();
                    AgentMailboxRuntime.dispatch(completionEntry, ignored -> {
                        // Revalidate inside the mailbox: the original offer may have been replaced
                        // while the network judgment or this callback was queued.
                        if (!isPendingOfferTarget(completionEntry, speaker)
                                || !matchesJudgedOffer(completionEntry, item, expiresAt, botRequesting)) {
                            return false;
                        }
                        return AgentOfferService.handlePendingOfferResponse(completionEntry, speaker, canonical);
                    });
                });
    }

    static boolean matchesJudgedOffer(AgentRuntimeEntry entry, Item item, long expiresAt, boolean botRequesting) {
        return item != null
                && AgentOfferStateRuntime.pendingLootOfferItem(entry) == item
                && AgentOfferStateRuntime.pendingLootOfferExpiresAt(entry) == expiresAt
                && AgentOfferStateRuntime.pendingLootOfferBotRequesting(entry) == botRequesting;
    }

    static boolean isPendingOfferTarget(AgentRuntimeEntry entry, Character speaker) {
        return entry != null
                && AgentOfferService.hasPendingOffer(entry)
                && !AgentOfferStateRuntime.pendingOfferExpired(entry, System.currentTimeMillis())
                && AgentOfferStateRuntime.pendingOfferRecipientIs(entry, speaker)
                && AgentRuntimeIdentityRuntime.botMapId(entry) == speaker.getMapId();
    }
}
