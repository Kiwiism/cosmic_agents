package server.agents.capabilities.partyquest.lpq;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Deterministic one-mover Gray order for LPQ Stage 8's five-of-nine puzzle. */
public final class AgentLpqCombinationOrder {
    private AgentLpqCombinationOrder() {
    }

    public static List<List<Integer>> fiveOfNine() {
        return combinations(9, 5);
    }

    static List<List<Integer>> combinations(int positions, int occupied) {
        if (positions < 1 || occupied < 1 || occupied > positions) {
            throw new IllegalArgumentException("valid LPQ combination dimensions are required");
        }
        List<List<Integer>> output = reflected(positions, occupied);
        return output.stream().map(values -> values.stream().sorted().toList()).toList();
    }

    private static List<List<Integer>> reflected(int n, int k) {
        if (k == 0) return List.of(List.of());
        if (n == k) {
            List<Integer> all = new ArrayList<>();
            for (int value = 1; value <= n; value++) all.add(value);
            return List.of(List.copyOf(all));
        }
        List<List<Integer>> output = new ArrayList<>(reflected(n - 1, k));
        List<List<Integer>> withLast = reflected(n - 1, k - 1);
        for (int index = withLast.size() - 1; index >= 0; index--) {
            List<Integer> combination = new ArrayList<>(withLast.get(index));
            combination.add(n);
            output.add(List.copyOf(combination));
        }
        return List.copyOf(output);
    }

    public static boolean oneMover(List<Integer> first, List<Integer> second) {
        if (first == null || second == null || first.size() != second.size()) return false;
        Set<Integer> intersection = new HashSet<>(first);
        intersection.retainAll(second);
        return intersection.size() == first.size() - 1;
    }
}
