package server.agents.economy.market;

import server.agents.economy.scenario.NamedRandomStreams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Produces a finite physical browsing itinerary; it never exposes a global listing search. */
public final class RoomVisitPlanner {
    private final int firstRoomMapId;
    private final int lastRoomMapId;

    public RoomVisitPlanner() {
        this(910000001, 910000022);
    }

    public RoomVisitPlanner(int firstRoomMapId, int lastRoomMapId) {
        if (firstRoomMapId < 910000001 || lastRoomMapId > 910000022
                || firstRoomMapId > lastRoomMapId) {
            throw new IllegalArgumentException("invalid configured FM room range");
        }
        this.firstRoomMapId = firstRoomMapId;
        this.lastRoomMapId = lastRoomMapId;
    }

    public List<Integer> plan(int minimumRooms, int maximumRooms, NamedRandomStreams random) {
        int availableRooms = lastRoomMapId - firstRoomMapId + 1;
        if (minimumRooms <= 0 || maximumRooms < minimumRooms || maximumRooms > availableRooms)
            throw new IllegalArgumentException("invalid room visit bounds");
        var stream = random.stream("market.room-visits");
        int count = minimumRooms + stream.nextInt(maximumRooms - minimumRooms + 1);
        List<Integer> rooms = new ArrayList<>();
        for (int room = firstRoomMapId; room <= lastRoomMapId; room++) rooms.add(room);
        for (int i = rooms.size() - 1; i > 0; i--) Collections.swap(rooms, i, stream.nextInt(i + 1));
        return List.copyOf(rooms.subList(0, count));
    }
}
