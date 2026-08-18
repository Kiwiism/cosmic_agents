package server.agents.capabilities.partyquest.kpq;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Deterministic one-mover ordering for the three KPQ combination stages. */
public final class AgentKpqCombinationOrder {
    private AgentKpqCombinationOrder() {
    }

    public static List<List<Integer>> forPositionCount(int positionCount) {
        if (positionCount < 4 || positionCount > 6) {
            throw new IllegalArgumentException("KPQ position count must be 4-6");
        }
        List<List<Integer>> combinations = new ArrayList<>();
        collect(positionCount, 1, new int[3], 0, combinations);
        List<List<Integer>> path = new ArrayList<>();
        boolean[] used = new boolean[combinations.size()];
        if (!findPath(combinations, used, 0, path)) {
            throw new IllegalStateException("No deterministic KPQ combination path");
        }
        return List.copyOf(path);
    }

    private static void collect(int n, int next, int[] selected, int depth,
                                List<List<Integer>> output) {
        if (depth == selected.length) {
            output.add(Arrays.stream(selected).boxed().toList());
            return;
        }
        for (int value = next; value <= n - (selected.length - depth) + 1; value++) {
            selected[depth] = value;
            collect(n, value + 1, selected, depth + 1, output);
        }
    }

    private static boolean findPath(List<List<Integer>> combinations,
                                    boolean[] used,
                                    int index,
                                    List<List<Integer>> path) {
        used[index] = true;
        path.add(combinations.get(index));
        if (path.size() == combinations.size()) {
            return true;
        }
        for (int candidate = 0; candidate < combinations.size(); candidate++) {
            if (!used[candidate] && oneMover(combinations.get(index), combinations.get(candidate))
                    && findPath(combinations, used, candidate, path)) {
                return true;
            }
        }
        path.removeLast();
        used[index] = false;
        return false;
    }

    public static boolean oneMover(List<Integer> first, List<Integer> second) {
        return first.stream().filter(second::contains).count() == 2L;
    }
}
