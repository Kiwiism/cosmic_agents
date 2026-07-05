package server.agents.capabilities.trade;

import client.Character;
import server.agents.integration.AgentBotCommandParser;
import server.agents.integration.AgentBotOfferStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;

import java.util.Collection;
import java.util.List;

public final class AgentPendingOfferChatRouteService {
    private AgentPendingOfferChatRouteService() {
    }

    public static boolean handlePendingOfferResponse(Collection<List<BotEntry>> entryGroups,
                                                     Character speaker,
                                                     String message) {
        return AgentPendingOfferResponseService.handlePendingOfferResponse(
                entryGroups,
                speaker,
                message,
                new AgentPendingOfferResponseService.Hooks<BotEntry>(
                        AgentOfferService::expirePendingOffer,
                        AgentPendingOfferChatRouteService::isPendingOfferTarget,
                        AgentBotCommandParser::resolveTargetedBot,
                        AgentOfferService::handlePendingOfferResponse,
                        (target, feedback) -> target.dropMessage(5, feedback)));
    }

    static boolean isPendingOfferTarget(BotEntry entry, Character speaker) {
        return entry != null
                && AgentOfferService.hasPendingOffer(entry)
                && AgentBotOfferStateRuntime.pendingOfferRecipientIs(entry, speaker)
                && AgentBotRuntimeIdentityRuntime.botMapId(entry) == speaker.getMapId();
    }
}
