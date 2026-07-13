package server.agents.capabilities.trade;

import client.Character;
import server.agents.commands.AgentCommandTargetResolver;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentMailboxRuntime;
import server.agents.runtime.mailbox.AgentMailboxOptions;

import java.util.Collection;
import java.util.List;

public final class AgentPendingOfferChatRouteService {
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
            return false;
        }
        AgentMailboxRuntime.dispatch(entry, ignored ->
                AgentOfferService.handlePendingOfferResponse(entry, speaker, message));
        return true;
    }

    static boolean isPendingOfferTarget(AgentRuntimeEntry entry, Character speaker) {
        return entry != null
                && AgentOfferService.hasPendingOffer(entry)
                && !AgentOfferStateRuntime.pendingOfferExpired(entry, System.currentTimeMillis())
                && AgentOfferStateRuntime.pendingOfferRecipientIs(entry, speaker)
                && AgentRuntimeIdentityRuntime.botMapId(entry) == speaker.getMapId();
    }
}
