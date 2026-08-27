package server.agents.progression;

import client.Character;
import server.agents.capabilities.objective.AgentNpcInteractionReachabilityService;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/** Shared real-NPC taxi interaction for Victoria systems already standing in a taxi town. */
final class AgentVictoriaTaxiRuntime {
    enum Result { ARRIVED, MOVING, UNAVAILABLE }

    private AgentVictoriaTaxiRuntime() {
    }

    static Result travelFromCurrentTown(AgentRuntimeEntry entry,
                                        Character agent,
                                        int destinationTownMapId,
                                        int interactionDistancePx,
                                        PrimitiveCapabilityGateway gateway) {
        int currentMapId = gateway.mapId(agent);
        if (currentMapId == destinationTownMapId) {
            return Result.ARRIVED;
        }
        AgentVictoriaSharedQuestPackCatalog.Town town =
                AgentVictoriaSharedQuestPackCatalog.town(currentMapId);
        if (town == null) {
            return Result.UNAVAILABLE;
        }
        int selection = town.selectionFor(destinationTownMapId);
        Point taxi = gateway.npcPosition(agent, town.taxiNpcId());
        if (selection < 0 || taxi == null) {
            return Result.UNAVAILABLE;
        }
        if (!gateway.grounded(agent)
                || !AgentNpcInteractionReachabilityService.canInteract(
                entry, agent, taxi, interactionDistancePx)) {
            gateway.navigate(entry, taxi, true);
            return Result.MOVING;
        }
        gateway.facePosition(agent, taxi);
        gateway.stop(entry);
        gateway.runNpcScript(agent, town.taxiNpcId(),
                AgentTaxiDialogueSequence.regularTownCab(selection));
        return Result.MOVING;
    }
}
