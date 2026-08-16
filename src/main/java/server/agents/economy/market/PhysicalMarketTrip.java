package server.agents.economy.market;

import client.Character;
import server.agents.economy.integration.cosmic.CosmicMarketObservationService;

import java.time.Instant;
import java.util.*;

/** Stateful trip that visits real rooms and approaches every observed physical PlayerShop. */
public final class PhysicalMarketTrip {
    private final List<Integer> rooms;
    private final Set<String> inspected = new HashSet<>();
    private int roomIndex;
    private Integer approachingObjectId;

    public PhysicalMarketTrip(List<Integer> rooms) {
        if (rooms == null || rooms.isEmpty() || rooms.stream().anyMatch(r -> r < 910000001 || r > 910000022))
            throw new IllegalArgumentException("a physical trip requires valid FM rooms");
        this.rooms = List.copyOf(rooms);
    }

    private PhysicalMarketTrip(List<Integer> rooms, Set<String> inspected,
                               int roomIndex, Integer approachingObjectId) {
        this(rooms);
        if (roomIndex < 0 || roomIndex > rooms.size())
            throw new IllegalArgumentException("invalid restored room index");
        this.inspected.addAll(inspected);
        this.roomIndex = roomIndex;
        this.approachingObjectId = approachingObjectId;
    }

    public Snapshot snapshot() {
        return new Snapshot(rooms, inspected.stream().sorted().toList(), roomIndex, approachingObjectId);
    }

    public static PhysicalMarketTrip restore(Snapshot snapshot) {
        return new PhysicalMarketTrip(snapshot.rooms(), Set.copyOf(snapshot.inspected()),
                snapshot.roomIndex(), snapshot.approachingObjectId());
    }

    public Step tick(Character agent, String logicalAgentId, Instant logicalAt,
                     PrivateMarketKnowledge knowledge, FreeMarketPhysicalGateway gateway) {
        if (complete()) return new Step(Status.COMPLETE, List.of(), null);
        int room = rooms.get(roomIndex);
        FreeMarketPhysicalGateway.ActionStatus travel = gateway.requestRoom(agent, room);
        if (travel != FreeMarketPhysicalGateway.ActionStatus.ARRIVED)
            return new Step(status(travel), List.of(), room);

        List<FreeMarketPhysicalGateway.StallTarget> stalls = gateway.visibleStalls(agent);
        FreeMarketPhysicalGateway.StallTarget target = approachingObjectId == null ? null : stalls.stream()
                .filter(value -> value.objectId() == approachingObjectId).findFirst().orElse(null);
        if (target == null) {
            approachingObjectId = null;
            target = stalls.stream().filter(value -> inspected.add(key(value))).findFirst().orElse(null);
        }
        if (target == null) {
            roomIndex++; inspected.clear(); approachingObjectId = null;
            return new Step(complete() ? Status.COMPLETE : Status.ROOM_COMPLETE, List.of(), room);
        }
        approachingObjectId = target.objectId();
        FreeMarketPhysicalGateway.ActionStatus approach = gateway.requestApproach(agent, target);
        if (approach != FreeMarketPhysicalGateway.ActionStatus.ARRIVED)
            return new Step(status(approach), List.of(), room);
        List<CosmicMarketObservationService.ObservedOffer> offers = gateway.inspectNearby(
                agent, logicalAgentId, logicalAt, knowledge);
        approachingObjectId = null;
        return new Step(Status.OBSERVED, offers, room);
    }

    public boolean complete() { return roomIndex >= rooms.size(); }
    public List<Integer> rooms() { return rooms; }
    public int roomIndex() { return roomIndex; }
    /** Ends this itinerary without fabricating observations; the next tick reports COMPLETE. */
    public void stop() { roomIndex = rooms.size(); approachingObjectId = null; }

    private static String key(FreeMarketPhysicalGateway.StallTarget target) {
        return target.roomMapId() + ":" + target.objectId();
    }
    private static Status status(FreeMarketPhysicalGateway.ActionStatus value) {
        return switch (value) {
            case ASSIGNED, IN_PROGRESS -> Status.PHYSICAL_ACTION_PENDING;
            case UNAVAILABLE, FAILED -> Status.BLOCKED;
            case ARRIVED -> throw new IllegalStateException();
        };
    }

    public enum Status { PHYSICAL_ACTION_PENDING, OBSERVED, ROOM_COMPLETE, COMPLETE, BLOCKED }
    public record Snapshot(List<Integer> rooms, List<String> inspected, int roomIndex,
                           Integer approachingObjectId) {
        public Snapshot {
            rooms = List.copyOf(rooms); inspected = List.copyOf(inspected);
        }
    }
    public record Step(Status status, List<CosmicMarketObservationService.ObservedOffer> offers,
                       Integer roomMapId) {
        public Step { offers = List.copyOf(offers); }
    }
}
