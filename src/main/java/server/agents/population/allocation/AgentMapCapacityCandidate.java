package server.agents.population.allocation;

/** Immutable occupancy view used by population placement policy. */
public record AgentMapCapacityCandidate(
        int mapId,
        int rank,
        int occupancy,
        int recommendedCapacity,
        int maximumCapacity) {

    public AgentMapCapacityCandidate {
        if (mapId < 0 || rank < 0 || occupancy < 0 || recommendedCapacity <= 0
                || maximumCapacity < recommendedCapacity) {
            throw new IllegalArgumentException("Valid ranked map capacity evidence is required");
        }
    }

    public boolean belowSoftCapacity() {
        return occupancy < recommendedCapacity;
    }

    public boolean belowHardCapacity() {
        return occupancy < maximumCapacity;
    }
}
