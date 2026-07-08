package server.agents.capabilities.trade;

import client.Character;
import server.agents.integration.AgentBotCommandParser;
import server.agents.integration.AgentBotOfferStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;

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
                        AgentOfferService::expirePendingOffer,
                        AgentPendingOfferChatRouteService::isPendingOfferTarget,
                        AgentBotCommandParser::resolveTargetedBot,
                        AgentOfferService::handlePendingOfferResponse,
                        (target, feedback) -> target.dropMessage(5, feedback)));
    }

    static boolean isPendingOfferTarget(AgentRuntimeEntry entry, Character speaker) {
        return entry != null
                && AgentOfferService.hasPendingOffer(entry)
                && AgentBotOfferStateRuntime.pendingOfferRecipientIs(entry, speaker)
                && AgentRuntimeIdentityRuntime.botMapId(entry) == speaker.getMapId();
    }
}
