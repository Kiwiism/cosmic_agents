package server.agents.economy.market;

import client.Character;
import server.agents.economy.integration.cosmic.CosmicMarketObservationService;

import java.time.Instant;
import java.time.Duration;
import java.util.*;

/** Stateful trip that visits real rooms and approaches every observed physical PlayerShop. */
public final class PhysicalMarketTrip {
    private final List<Integer> rooms;
    private final Duration inspectionDurationPerListing;
    private final Set<String> inspected = new HashSet<>();
    private int roomIndex;
    private Integer approachingObjectId;
    private FreeMarketPhysicalGateway.StallTarget inspectingStall;
    private Instant inspectionCompletesAt;
    private int inspectionListingCount;

    public PhysicalMarketTrip(List<Integer> rooms) {
        this(rooms, Duration.ZERO);
    }

    public PhysicalMarketTrip(List<Integer> rooms, Duration inspectionDurationPerListing) {
        if (rooms == null || rooms.isEmpty() || rooms.stream().anyMatch(r -> r < 910000001 || r > 910000022))
            throw new IllegalArgumentException("a physical trip requires valid FM rooms");
        if (inspectionDurationPerListing == null || inspectionDurationPerListing.isNegative())
            throw new IllegalArgumentException("stall inspection duration cannot be negative");
        this.rooms = List.copyOf(rooms);
        this.inspectionDurationPerListing = inspectionDurationPerListing;
    }

    private PhysicalMarketTrip(List<Integer> rooms, Duration inspectionDurationPerListing,
                               Set<String> inspected, int roomIndex, Integer approachingObjectId,
                               FreeMarketPhysicalGateway.StallTarget inspectingStall,
                               Instant inspectionCompletesAt, int inspectionListingCount) {
        this(rooms, inspectionDurationPerListing);
        if (roomIndex < 0 || roomIndex > rooms.size())
            throw new IllegalArgumentException("invalid restored room index");
        this.inspected.addAll(inspected);
        this.roomIndex = roomIndex;
        this.approachingObjectId = approachingObjectId;
        this.inspectingStall = inspectingStall;
        this.inspectionCompletesAt = inspectionCompletesAt;
        this.inspectionListingCount = inspectionListingCount;
    }

    public Snapshot snapshot() {
        return new Snapshot(rooms, inspected.stream().sorted().toList(), roomIndex, approachingObjectId,
                inspectionDurationPerListing.toMillis(), inspectingStall, inspectionCompletesAt,
                inspectionListingCount);
    }

    public static PhysicalMarketTrip restore(Snapshot snapshot) {
        return new PhysicalMarketTrip(snapshot.rooms(), Duration.ofMillis(snapshot.inspectionMillisPerListing()),
                Set.copyOf(snapshot.inspected()), snapshot.roomIndex(), snapshot.approachingObjectId(),
                snapshot.inspectingStall(), snapshot.inspectionCompletesAt(), snapshot.inspectionListingCount());
    }

    public Step tick(Character agent, String logicalAgentId, Instant logicalAt,
                     PrivateMarketKnowledge knowledge, FreeMarketPhysicalGateway gateway) {
        if (complete()) return Step.of(Status.COMPLETE, List.of(), null);
        int room = rooms.get(roomIndex);
        FreeMarketPhysicalGateway.ActionStatus travel = gateway.requestRoom(agent, room);
        if (travel != FreeMarketPhysicalGateway.ActionStatus.ARRIVED)
            return Step.of(status(travel), List.of(), room);

        List<FreeMarketPhysicalGateway.StallTarget> stalls = gateway.visibleStalls(agent);
        if (inspectingStall != null) {
            FreeMarketPhysicalGateway.StallTarget live = stalls.stream()
                    .filter(value -> value.objectId() == inspectingStall.objectId()).findFirst().orElse(null);
            if (live == null) {
                gateway.cancelStallVisit(agent, inspectingStall);
                clearInspection();
                return Step.of(Status.BLOCKED, List.of(), room);
            }
            if (logicalAt.isBefore(inspectionCompletesAt))
                return new Step(Status.INSPECTING, List.of(), room, Optional.of(inspectionCompletesAt),
                        live.objectId(), inspectionListingCount, false);
            List<CosmicMarketObservationService.ObservedOffer> offers = gateway.inspectAndExit(
                    agent, logicalAgentId, live, logicalAt, knowledge);
            int objectId = live.objectId();
            int listingCount = inspectionListingCount;
            clearInspection();
            return new Step(Status.OBSERVED, offers, room, Optional.empty(), objectId,
                    listingCount, false);
        }
        FreeMarketPhysicalGateway.StallTarget target = approachingObjectId == null ? null : stalls.stream()
                .filter(value -> value.objectId() == approachingObjectId).findFirst().orElse(null);
        if (target == null) {
            approachingObjectId = null;
            target = stalls.stream().filter(value -> inspected.add(key(value))).findFirst().orElse(null);
        }
        if (target == null) {
            roomIndex++; inspected.clear(); approachingObjectId = null;
            return Step.of(complete() ? Status.COMPLETE : Status.ROOM_COMPLETE, List.of(), room);
        }
        approachingObjectId = target.objectId();
        FreeMarketPhysicalGateway.ActionStatus approach = gateway.requestApproach(agent, target);
        if (approach != FreeMarketPhysicalGateway.ActionStatus.ARRIVED)
            return Step.of(status(approach), List.of(), room);
        FreeMarketPhysicalGateway.InspectionStatus entered = gateway.enterStall(agent, target);
        if (entered.status() != FreeMarketPhysicalGateway.ActionStatus.ARRIVED)
            return Step.of(status(entered.status()), List.of(), room);
        approachingObjectId = null;
        inspectingStall = target;
        inspectionListingCount = entered.listingCount();
        inspectionCompletesAt = logicalAt.plus(inspectionDurationPerListing.multipliedBy(
                inspectionListingCount));
        if (logicalAt.isBefore(inspectionCompletesAt))
            return new Step(Status.INSPECTING, List.of(), room, Optional.of(inspectionCompletesAt),
                    target.objectId(), inspectionListingCount, true);
        List<CosmicMarketObservationService.ObservedOffer> offers = gateway.inspectAndExit(
                agent, logicalAgentId, target, logicalAt, knowledge);
        int listingCount = inspectionListingCount;
        clearInspection();
        return new Step(Status.OBSERVED, offers, room, Optional.empty(), target.objectId(),
                listingCount, true);
    }

    public boolean complete() { return roomIndex >= rooms.size(); }
    public List<Integer> rooms() { return rooms; }
    public int roomIndex() { return roomIndex; }
    /** Ends this itinerary without fabricating observations; the next tick reports COMPLETE. */
    public void stop() {
        if (inspectingStall != null)
            throw new IllegalStateException("cannot stop a physical trip while inside a stall");
        roomIndex = rooms.size(); approachingObjectId = null;
    }

    public void cancel(Character agent, FreeMarketPhysicalGateway gateway) {
        if (inspectingStall != null) gateway.cancelStallVisit(agent, inspectingStall);
        clearInspection();
        approachingObjectId = null;
    }

    private void clearInspection() {
        inspectingStall = null;
        inspectionCompletesAt = null;
        inspectionListingCount = 0;
    }

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

    public enum Status { PHYSICAL_ACTION_PENDING, INSPECTING, OBSERVED, ROOM_COMPLETE, COMPLETE, BLOCKED }
    public record Snapshot(List<Integer> rooms, List<String> inspected, int roomIndex,
                           Integer approachingObjectId, long inspectionMillisPerListing,
                           FreeMarketPhysicalGateway.StallTarget inspectingStall,
                           Instant inspectionCompletesAt, int inspectionListingCount) {
        public Snapshot {
            rooms = List.copyOf(rooms); inspected = List.copyOf(inspected);
            if (inspectionMillisPerListing < 0 || inspectionListingCount < 0)
                throw new IllegalArgumentException("invalid inspection snapshot");
        }

        public Snapshot(List<Integer> rooms, List<String> inspected, int roomIndex,
                        Integer approachingObjectId) {
            this(rooms, inspected, roomIndex, approachingObjectId, 0, null, null, 0);
        }
    }
    public record Step(Status status, List<CosmicMarketObservationService.ObservedOffer> offers,
                       Integer roomMapId, Optional<Instant> revisitAt, Integer stallObjectId,
                       int listingCount, boolean inspectionStarted) {
        public Step {
            offers = List.copyOf(offers);
            revisitAt = revisitAt == null ? Optional.empty() : revisitAt;
        }
        private static Step of(Status status, List<CosmicMarketObservationService.ObservedOffer> offers,
                               Integer roomMapId) {
            return new Step(status, offers, roomMapId, Optional.empty(), null, 0, false);
        }
    }
}
