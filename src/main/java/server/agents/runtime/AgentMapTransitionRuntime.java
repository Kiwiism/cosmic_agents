package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementStateResetService;

import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentFootholdIndexService;
import server.agents.capabilities.movement.AgentGroundingService;
import server.agents.capabilities.movement.AgentMovementPoseService;

import client.Character;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.partyquest.AgentPartyQuestHooks;
import server.agents.capabilities.shop.AgentShopService;
import server.agents.integration.AgentBotManagerStatusRuntime;
import server.agents.integration.AgentBotPqRuntime;
import server.bots.BotEntry;

import java.util.function.Consumer;

public final class AgentMapTransitionRuntime {
    private AgentMapTransitionRuntime() {
    }

    public static boolean groundAfterMapChange(BotEntry entry, Character agent) {
        return AgentMapTransitionService.groundAfterMapChange(entry, agent, groundingHooks());
    }

    public static boolean handleTrackedMapChange(BotEntry entry,
                                                 Character agent,
                                                 Consumer<BotEntry> issueGrind,
                                                 Consumer<BotEntry> issueFollow) {
        return AgentMapTransitionService.handleTrackedMapChange(
                entry,
                agent,
                new AgentMapTransitionService.MapChangeHooks(
                        groundingHooks(),
                        (hookEntry, hookAgent) -> AgentPartyQuestHooks.requiresGrind(asBotEntry(hookEntry), hookAgent),
                        hookEntry -> issueGrind.accept(asBotEntry(hookEntry)),
                        (hookEntry, hookAgent) -> AgentPartyQuestHooks.requiresFollow(asBotEntry(hookEntry), hookAgent),
                        hookEntry -> issueFollow.accept(asBotEntry(hookEntry)),
                        hookEntry -> AgentBotPqRuntime.resetKpqStage5Claimed(asBotEntry(hookEntry)),
                        (hookEntry, hookAgent) -> AgentShopService.onMapChange(asBotEntry(hookEntry), hookAgent),
                        (hookEntry, hookAgent) -> AgentBotManagerStatusRuntime.checkManagerStatus(asBotEntry(hookEntry), hookAgent)));
    }

    private static AgentMapTransitionService.GroundingHooks groundingHooks() {
        return new AgentMapTransitionService.GroundingHooks(
                AgentFootholdIndexService::buildFhIndex,
                AgentGroundingService::findGroundPoint,
                (entry, agent, position) -> AgentMovementPoseService.teleportTo(asBotEntry(entry), agent, position),
                entry -> AgentMovementStateResetService.resetEntryStateAfterTeleport(asBotEntry(entry)),
                AgentNavigationGraphService::warmGraphAsync,
                entry -> AgentMovementBroadcastService.broadcastMovement(asBotEntry(entry)));
    }

    private static BotEntry asBotEntry(AgentRuntimeEntry entry) {
        return (BotEntry) entry;
    }
}
