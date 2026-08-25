package server.agents.economy.integration.cosmic;

import client.Character;
import server.agents.economy.market.FreeMarketPhysicalGateway;

import java.util.Objects;

/** Temporary entrance service; a physical storage NPC can replace this without changing callers. */
public final class CosmicFreeMarketStorageAccess {
    private final FreeMarketPhysicalGateway physical;
    private final int entranceMapId;
    private final int storageNpcId;

    public CosmicFreeMarketStorageAccess(FreeMarketPhysicalGateway physical, int entranceMapId,
                                         int storageNpcId) {
        this.physical = Objects.requireNonNull(physical);
        if (entranceMapId <= 0 || storageNpcId <= 0)
            throw new IllegalArgumentException("FM entrance and storage NPC are required");
        this.entranceMapId = entranceMapId;
        this.storageNpcId = storageNpcId;
    }

    public Result request(Character agent) {
        if (agent == null || agent.getClient() == null) return new Result(Status.UNAVAILABLE, "AGENT_NOT_LIVE");
        FreeMarketPhysicalGateway.ActionStatus travel = physical.requestEntrance(agent);
        if (travel != FreeMarketPhysicalGateway.ActionStatus.ARRIVED)
            return new Result(switch (travel) {
                case ASSIGNED, IN_PROGRESS -> Status.MOVING_TO_ENTRANCE;
                case UNAVAILABLE, FAILED -> Status.UNAVAILABLE;
                case ARRIVED -> throw new IllegalStateException();
            }, travel.name());
        if (agent.getMapId() != entranceMapId) return new Result(Status.UNAVAILABLE, "NOT_AT_ENTRANCE");
        agent.getStorage().sendStorage(agent.getClient(), storageNpcId);
        return new Result(agent.getStorage().isOpen() ? Status.OPENED : Status.DENIED,
                agent.getStorage().isOpen() ? "OPENED" : "COSMIC_RESTRICTION");
    }

    public enum Status { MOVING_TO_ENTRANCE, OPENED, DENIED, UNAVAILABLE }
    public record Result(Status status, String reason) {
        public Result { Objects.requireNonNull(status); reason = reason == null ? "" : reason; }
    }
}
