package server.agents.capabilities.trade;

import client.Character;
import server.agents.integration.AgentBotCommandParser;
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
                new AgentPendingOfferResponseService.Hooks(
                        AgentOfferService::expirePendingOffer,
                        AgentPendingOfferResponseService::isPendingOfferTarget,
                        AgentBotCommandParser::resolveTargetedBot,
                        AgentOfferService::handlePendingOfferResponse,
                        (target, feedback) -> target.dropMessage(5, feedback)));
    }
}
