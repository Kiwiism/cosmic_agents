package server.agents.economy.integration.cosmic;

import client.Character;
import server.agents.capabilities.primitive.AgentNavigationCapability;
import server.agents.capabilities.primitive.AgentPortalTravelCapability;
import server.agents.capabilities.runtime.AgentCapabilityInvocation;
import server.agents.capabilities.runtime.AgentCapabilityRuntime;
import server.agents.economy.market.FreeMarketPhysicalGateway;
import server.agents.economy.market.PrivateMarketKnowledge;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.maps.MapObjectType;
import server.maps.PlayerShop;
import server.maps.Portal;

import java.awt.Point;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Uses the existing autonomous-agent capability stack; it never teleports between FM maps. */
public final class CosmicFreeMarketPhysicalGateway implements FreeMarketPhysicalGateway {
    private final CosmicMarketObservationService observations;
    private final int entranceMapId;
    private final int firstRoomMapId;
    private final int lastRoomMapId;
    private final long portalTimeoutMs;
    private final long approachTimeoutMs;
    private final int approachRangePixels;

    public CosmicFreeMarketPhysicalGateway(CosmicMarketObservationService observations,
                                           int entranceMapId, int firstRoomMapId, int lastRoomMapId,
                                           long portalTimeoutMs, long approachTimeoutMs,
                                           int approachRangePixels) {
        this.observations = Objects.requireNonNull(observations);
        if (entranceMapId <= 0 || firstRoomMapId <= entranceMapId || lastRoomMapId < firstRoomMapId)
            throw new IllegalArgumentException("invalid Free Market map manifest");
        if (portalTimeoutMs <= 0 || approachTimeoutMs <= 0 || approachRangePixels <= 0)
            throw new IllegalArgumentException("physical market action bounds must be positive");
        this.entranceMapId = entranceMapId; this.firstRoomMapId = firstRoomMapId;
        this.lastRoomMapId = lastRoomMapId;
        this.portalTimeoutMs = portalTimeoutMs; this.approachTimeoutMs = approachTimeoutMs;
        this.approachRangePixels = approachRangePixels;
    }

    @Override
    public ActionStatus requestRoom(Character agent, int roomMapId) {
        requireRoom(roomMapId);
        if (agent.getMapId() == roomMapId) return ActionStatus.ARRIVED;
        if (!inFreeMarket(agent.getMapId())) return ActionStatus.UNAVAILABLE;
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByCharacterInstance(agent);
        if (entry == null) return ActionStatus.UNAVAILABLE;
        if (entry.capabilityRuntimeState().hasActiveCapability()) return ActionStatus.IN_PROGRESS;
        int nextMap = agent.getMapId() == entranceMapId ? roomMapId : entranceMapId;
        Portal portal = agent.getMap().getPortals().stream()
                .filter(value -> value.getTargetMapId() == nextMap && value.getPortalStatus())
                .min(Comparator.comparingInt(Portal::getId)).orElse(null);
        if (portal == null) return ActionStatus.FAILED;
        boolean assigned = AgentCapabilityRuntime.assign(entry, new AgentCapabilityInvocation<>(
                new AgentPortalTravelCapability(),
                new AgentPortalTravelCapability.Command(agent.getMapId(), portal.getId(), nextMap, false),
                portalTimeoutMs, 2));
        return assigned ? ActionStatus.ASSIGNED : ActionStatus.IN_PROGRESS;
    }

    @Override
    public ActionStatus requestApproach(Character agent, StallTarget stall) {
        if (agent.getMapId() != stall.roomMapId()) return ActionStatus.UNAVAILABLE;
        if (!(agent.getMap().getMapObject(stall.objectId()) instanceof PlayerShop shop) || !shop.isOpen())
            return ActionStatus.UNAVAILABLE;
        Point target = shop.getPosition();
        if (agent.getPosition().distanceSq(target) <= (long) approachRangePixels * approachRangePixels)
            return ActionStatus.ARRIVED;
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByCharacterInstance(agent);
        if (entry == null) return ActionStatus.UNAVAILABLE;
        if (entry.capabilityRuntimeState().hasActiveCapability()) return ActionStatus.IN_PROGRESS;
        boolean assigned = AgentCapabilityRuntime.assign(entry, new AgentCapabilityInvocation<>(
                new AgentNavigationCapability(),
                new AgentNavigationCapability.Command(agent.getMapId(), target, approachRangePixels, true),
                approachTimeoutMs, 2));
        return assigned ? ActionStatus.ASSIGNED : ActionStatus.IN_PROGRESS;
    }

    @Override
    public List<StallTarget> visibleStalls(Character agent) {
        if (agent == null || agent.getMap() == null || !isRoom(agent.getMapId())) return List.of();
        List<StallTarget> result = new ArrayList<>();
        for (var object : agent.getMap().getMapObjects()) {
            if (object.getType() == MapObjectType.SHOP && object instanceof PlayerShop shop
                    && shop.isOpen() && !shop.isOwner(agent)) {
                result.add(new StallTarget(shop.getObjectId(), shop.getOwnerId(), agent.getMapId(),
                        shop.getPosition().x, shop.getPosition().y));
            }
        }
        result.sort(Comparator.comparingInt(StallTarget::objectId));
        return List.copyOf(result);
    }

    @Override
    public List<CosmicMarketObservationService.ObservedOffer> inspectNearby(
            Character agent, String logicalAgentId, Instant logicalAt, PrivateMarketKnowledge knowledge) {
        return observations.inspectNearby(agent, logicalAgentId, logicalAt, knowledge);
    }

    @Override
    public PurchaseStatus buyObserved(Character agent, String logicalAgentId,
                                      CosmicMarketObservationService.ObservedOffer offer, short bundles,
                                      Instant logicalAt, PrivateMarketKnowledge knowledge) {
        var result = observations.buyObserved(agent, logicalAgentId, offer, bundles, logicalAt, knowledge);
        return new PurchaseStatus(result.success(), result.result(), result.itemId(), result.quantity(),
                result.buyerMesoDelta());
    }

    private boolean inFreeMarket(int map) { return map == entranceMapId || isRoom(map); }
    private boolean isRoom(int map) { return map >= firstRoomMapId && map <= lastRoomMapId; }
    private void requireRoom(int map) {
        if (!isRoom(map)) throw new IllegalArgumentException("target is not a Free Market room");
    }
}
