package server.agents.economy.market;

import server.agents.economy.scenario.NamedRandomStreams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Produces a finite physical browsing itinerary; it never exposes a global listing search. */
public final class RoomVisitPlanner {
    public List<Integer> plan(int minimumRooms, int maximumRooms, NamedRandomStreams random) {
        if (minimumRooms <= 0 || maximumRooms < minimumRooms || maximumRooms > 22)
            throw new IllegalArgumentException("invalid room visit bounds");
        var stream = random.stream("market.room-visits");
        int count = minimumRooms + stream.nextInt(maximumRooms - minimumRooms + 1);
        List<Integer> rooms = new ArrayList<>();
        for (int room = 1; room <= 22; room++) rooms.add(910000000 + room);
        for (int i = rooms.size() - 1; i > 0; i--) Collections.swap(rooms, i, stream.nextInt(i + 1));
        return List.copyOf(rooms.subList(0, count));
    }
}
