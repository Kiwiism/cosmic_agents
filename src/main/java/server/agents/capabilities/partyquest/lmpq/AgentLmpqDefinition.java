package server.agents.capabilities.partyquest.lmpq;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Authoritative local Ludibrium Maze PQ content and directed portal graph. */
public final class AgentLmpqDefinition {
    public static final int RECRUIT_MAP = 220_000_000;
    public static final int ENTRY_NPC = 9_103_001;
    public static final int FIRST_MAZE_MAP = 809_050_000;
    public static final int LAST_FARM_MAP = 809_050_014;
    public static final int CLEAR_NPC_MAP = 809_050_015;
    public static final int REWARD_MAP = 809_050_016;
    public static final int EXIT_MAP = 809_050_017;
    public static final int CLEAR_NPC = 9_103_000;
    public static final int REWARD_NPC = 9_103_002;
    public static final int COUPON = 4_001_106;
    public static final int MIN_LEVEL = 51;
    public static final int MAX_LEVEL = 70;
    public static final int MIN_PARTY_SIZE = 3;
    public static final int MAX_PARTY_SIZE = 6;
    public static final int REQUIRED_COUPONS = 30;
    public static final int SAFE_COUPON_TARGET = 36;
    public static final int RENDEZVOUS_ROOM = 9;
    public static final int CLEAR_ROOM = 16;
    public static final int ROOM_MARKER_MESOS = 10;
    public static final Set<Integer> BOX_REACTORS = Set.of(
            8_091_000, 8_091_001, 8_091_002, 8_091_003, 8_091_004, 8_098_000);
    public static final Set<Integer> COUPON_MOBS = Set.of(
            9_400_209, 9_400_210, 9_400_211, 9_400_212, 9_400_213,
            9_400_214, 9_400_215, 9_400_216, 9_400_217, 9_400_218);

    private static final Map<Integer, List<Edge>> GRAPH = buildGraph();

    private AgentLmpqDefinition() { }

    public static boolean isFarmMap(int mapId) {
        return mapId >= FIRST_MAZE_MAP && mapId <= LAST_FARM_MAP;
    }

    public static boolean isEventMap(int mapId) {
        return isFarmMap(mapId) || mapId == CLEAR_NPC_MAP || mapId == REWARD_MAP;
    }

    public static int roomForMap(int mapId) {
        return mapId >= FIRST_MAZE_MAP && mapId <= CLEAR_NPC_MAP
                ? mapId - FIRST_MAZE_MAP + 1 : 0;
    }

    public static int mapForRoom(int room) {
        if (room < 1 || room > CLEAR_ROOM) throw new IllegalArgumentException("LMPQ room must be 1-16");
        return FIRST_MAZE_MAP + room - 1;
    }

    public static List<Edge> edges(int room) {
        List<Edge> edges = GRAPH.get(room);
        if (edges == null) throw new IllegalArgumentException("LMPQ room must be 1-16");
        return edges;
    }

    /** Returns the authored portal id for the first hop of a shortest directed route. */
    public static int nextPortalId(int fromRoom, int targetRoom) {
        if (fromRoom == targetRoom) return -1;
        record Step(int room, int firstPortal) { }
        ArrayDeque<Step> queue = new ArrayDeque<>();
        Set<Integer> seen = new java.util.HashSet<>();
        seen.add(fromRoom);
        for (Edge edge : edges(fromRoom)) {
            queue.add(new Step(edge.destinationRoom(), edge.portalId()));
            seen.add(edge.destinationRoom());
        }
        while (!queue.isEmpty()) {
            Step step = queue.removeFirst();
            if (step.room() == targetRoom) return step.firstPortal();
            for (Edge edge : edges(step.room())) {
                if (seen.add(edge.destinationRoom())) {
                    queue.addLast(new Step(edge.destinationRoom(), step.firstPortal()));
                }
            }
        }
        return -1;
    }

    public static int distance(int fromRoom, int targetRoom) {
        if (fromRoom == targetRoom) return 0;
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        Set<Integer> seen = new java.util.HashSet<>();
        queue.add(new int[]{fromRoom, 0});
        seen.add(fromRoom);
        while (!queue.isEmpty()) {
            int[] current = queue.removeFirst();
            for (Edge edge : edges(current[0])) {
                if (edge.destinationRoom() == targetRoom) return current[1] + 1;
                if (seen.add(edge.destinationRoom())) queue.addLast(new int[]{edge.destinationRoom(), current[1] + 1});
            }
        }
        return Integer.MAX_VALUE;
    }

    /** Coarse authored yield priority; live reactors and drops remain authoritative. */
    public static int yieldPriority(int room) {
        return switch (room) {
            case 6 -> 100;
            case 7 -> 80;
            case 1, 11, 15 -> 30;
            case 2, 3, 4, 5, 8, 9, 10 -> 24;
            case 12 -> 14;
            case 13, 14 -> 12;
            default -> 0;
        };
    }

    private static Map<Integer, List<Edge>> buildGraph() {
        Map<Integer, List<Edge>> graph = new LinkedHashMap<>();
        add(graph, 1, 5, 8, 13);
        add(graph, 2, 6, 9, 14);
        add(graph, 3, 7, 10, 15);
        add(graph, 4, 8, 11, 1);
        add(graph, 5, 9, 12, 2);
        add(graph, 6, 10, 13, 3);
        add(graph, 7, 11, 14, 4);
        add(graph, 8, 12, 15, 5);
        add(graph, 9, 13, 16, 6);
        add(graph, 10, 14, 2, 7);
        add(graph, 11, 15, 3, 8);
        add(graph, 12, 1, 4, 9);
        add(graph, 13, 2, 5, 10);
        add(graph, 14, 3, 6, 11);
        add(graph, 15, 4, 7, 12);
        graph.put(16, List.of(new Edge(2, 9)));
        return Map.copyOf(graph);
    }

    private static void add(Map<Integer, List<Edge>> graph, int room, int left, int middle, int right) {
        List<Edge> edges = new ArrayList<>(3);
        edges.add(new Edge(2, left));
        edges.add(new Edge(3, middle));
        edges.add(new Edge(4, right));
        graph.put(room, List.copyOf(edges));
    }

    public record Edge(int portalId, int destinationRoom) { }
}
