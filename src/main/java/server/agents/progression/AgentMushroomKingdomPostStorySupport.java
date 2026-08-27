package server.agents.progression;

import client.Character;
import server.agents.capabilities.navigation.AgentRouteOutcome;
import server.agents.capabilities.navigation.AgentRouteStatus;
import server.agents.capabilities.objective.AgentNpcInteractionReachabilityService;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/** Small shared movement/interaction adapter for post-story Mushroom Kingdom plans. */
final class AgentMushroomKingdomPostStorySupport {
    private static final int NPC_DISTANCE_PX = config.AgentTuning.intValue(
            "server.agents.progression.AgentMushroomKingdomPostStorySupport.NPC_DISTANCE_PX");
    private static final int PORTAL_DISTANCE_PX = config.AgentTuning.intValue(
            "server.agents.progression.AgentMushroomKingdomPostStorySupport.PORTAL_DISTANCE_PX");
    private static final long RETRY_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentMushroomKingdomPostStorySupport.RETRY_MS");

    private AgentMushroomKingdomPostStorySupport() { }

    static boolean travel(AgentRuntimeEntry entry, Character agent, int destinationMapId,
                          AgentMushroomKingdomPostStoryState state,
                          PrimitiveCapabilityGateway gateway, long nowMs) {
        if (gateway.mapId(agent) == destinationMapId) return true;
        AgentRouteOutcome outcome = gateway.travelTo(entry, agent, destinationMapId, nowMs);
        if (outcome.status() == AgentRouteStatus.ARRIVED
                || gateway.mapId(agent) == destinationMapId) {
            state.capabilityProgress(nowMs);
            return true;
        }
        if (outcome.status() == AgentRouteStatus.MOVING) {
            state.active("traveling to Mushroom Kingdom map " + destinationMapId);
            return false;
        }
        fail(entry, state, gateway, "no route to Mushroom Kingdom map " + destinationMapId, nowMs);
        return false;
    }

    static boolean interact(AgentRuntimeEntry entry, Character agent, int npcId,
                            AgentMushroomKingdomPostStoryState state,
                            PrimitiveCapabilityGateway gateway, long nowMs, Action action) {
        Point npc = gateway.npcPosition(agent, npcId);
        if (npc == null) {
            return fail(entry, state, gateway,
                    "NPC " + npcId + " is absent from map " + gateway.mapId(agent), nowMs);
        }
        if (!gateway.grounded(agent)
                || !AgentNpcInteractionReachabilityService.canInteract(
                entry, agent, npc, NPC_DISTANCE_PX)) {
            gateway.navigate(entry, npc, true);
            return true;
        }
        if (nowMs < state.nextActionAtMs()) return true;
        gateway.facePosition(agent, npc);
        gateway.stop(entry);
        boolean success = action.run();
        state.nextActionAtMs(nowMs + RETRY_MS);
        if (success) state.capabilityProgress(nowMs);
        else fail(entry, state, gateway, "NPC " + npcId + " did not advance the plan", nowMs);
        return true;
    }

    static boolean nearPortal(AgentRuntimeEntry entry, Character agent, int portalId,
                              PrimitiveCapabilityGateway gateway) {
        Point portal = gateway.portalPosition(agent, portalId);
        Point position = gateway.position(agent);
        if (portal == null || position == null || !gateway.grounded(agent)
                || position.distance(portal) > PORTAL_DISTANCE_PX) {
            if (portal != null) gateway.navigate(entry, portal, true);
            return false;
        }
        return true;
    }

    static boolean fail(AgentRuntimeEntry entry, AgentMushroomKingdomPostStoryState state,
                        PrimitiveCapabilityGateway gateway, String reason, long nowMs) {
        if (state.capabilityFailure() >= 8) {
            gateway.stop(entry);
            state.block(reason);
            return false;
        }
        state.active(reason + "; retrying");
        state.nextActionAtMs(nowMs + RETRY_MS);
        return true;
    }

    @FunctionalInterface interface Action { boolean run(); }
}
