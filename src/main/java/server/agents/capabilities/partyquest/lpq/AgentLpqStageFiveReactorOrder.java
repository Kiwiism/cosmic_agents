package server.agents.capabilities.partyquest.lpq;

import server.maps.Reactor;

import java.awt.Point;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Stable authored box order for each LPQ Stage 5 room. */
final class AgentLpqStageFiveReactorOrder {
    private static final int DARK_SIGHT_ROOM = 922_010_506;
    private static final Point DARK_SIGHT_FINAL_BOX = new Point(-70, -3_535);
    /**
     * The final Dark Sight box sits above the long centre rope and the short top
     * rope. Asking the generic planner for the box platform in one operation can
     * leave the runner committed to rope 7 far below it. Keep the last ascent on
     * the authored rope endpoints instead.
     */
    private static final List<Point> DARK_SIGHT_FINAL_ASCENT = List.of(
            new Point(-8, -1_518), new Point(-8, -2_488),
            new Point(13, -2_562), new Point(13, -3_533));
    private static final Map<Integer, List<Point>> ORDER_BY_ROOM = Map.of(
            922_010_501, List.of(
                    new Point(-106, -3_379), new Point(-78, -2_350),
                    new Point(101, -2_132), new Point(-96, -1_914)),
            922_010_502, List.of(
                    new Point(208, -1_414), new Point(-199, -1_458),
                    new Point(221, -3_009), new Point(-204, -3_013)),
            922_010_503, List.of(
                    new Point(226, -921), new Point(-216, -1_493),
                    new Point(227, -2_014), new Point(215, -3_009)),
            922_010_504, List.of(
                    new Point(148, -1_299), new Point(-216, -1_493),
                    new Point(227, -2_014), new Point(215, -3_009)),
            922_010_505, List.of(
                    new Point(226, -921), new Point(-216, -1_493),
                    new Point(227, -2_014), new Point(215, -3_009)),
            922_010_506, List.of(
                    new Point(-83, -630), new Point(92, -1_064),
                    new Point(-81, -1_459), new Point(-70, -3_535)));

    private AgentLpqStageFiveReactorOrder() { }

    static Reactor select(int roomMapId, List<Reactor> active) {
        List<Point> order = ORDER_BY_ROOM.get(roomMapId);
        if (order == null || active == null || active.isEmpty()) return null;
        return active.stream()
                .filter(java.util.Objects::nonNull)
                .min(Comparator.comparingInt((Reactor reactor) ->
                                orderIndex(order, reactor.getPosition()))
                        .thenComparingInt(Reactor::getObjectId))
                .orElse(null);
    }

    static List<Point> positions(int roomMapId) {
        return ORDER_BY_ROOM.getOrDefault(roomMapId, List.of()).stream()
                .map(Point::new)
                .toList();
    }

    static boolean isDarkSightFinalBox(int roomMapId, Point reactorPosition) {
        return roomMapId == DARK_SIGHT_ROOM
                && DARK_SIGHT_FINAL_BOX.equals(reactorPosition);
    }

    static Point authoredApproachWaypoint(
            int roomMapId, Point currentPosition, Point reactorPosition) {
        if (!isDarkSightFinalBox(roomMapId, reactorPosition)
                || currentPosition == null) return null;
        for (Point waypoint : DARK_SIGHT_FINAL_ASCENT) {
            if (waypoint.y < currentPosition.y - 12) return new Point(waypoint);
        }
        return null;
    }

    private static int orderIndex(List<Point> order, Point position) {
        int index = order.indexOf(position);
        return index >= 0 ? index : Integer.MAX_VALUE;
    }
}
