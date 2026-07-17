package server.agents.capabilities.objective;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.OptionalInt;

public interface AgentPlanCompletionPolicy {
    AgentPlanCompletionPolicy SIT = new AgentPlanCompletionPolicy() {
    };

    default AgentPlanCompletionMode selectMode(AgentRuntimeEntry entry, int mapId) {
        return AgentPlanCompletionMode.SIT;
    }

    default boolean startWander(AgentRuntimeEntry entry, Character agent) {
        return false;
    }

    default String locationName(AgentRuntimeEntry entry, int mapId) {
        return "the destination";
    }

    default OptionalInt selectRestSpotIndex(AgentRuntimeEntry entry, int mapId, int candidateCount) {
        return OptionalInt.empty();
    }

    default OptionalInt selectFacingDirection(AgentRuntimeEntry entry, int mapId) {
        return OptionalInt.empty();
    }
}
