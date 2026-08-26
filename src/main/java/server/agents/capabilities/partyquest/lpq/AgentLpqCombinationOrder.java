package server.agents.capabilities.partyquest.lpq;

import config.AgentTuning;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Authored GMS and JMS attempt orders for LPQ Stage 8's five-of-nine puzzle. */
public final class AgentLpqCombinationOrder {
    enum Method { GMS, JMS }

    private static final String STAGE_8_ATTEMPT_ORDER = AgentTuning.stringValue(
            "server.agents.capabilities.partyquest.lpq.AgentLpqCombinationOrder.STAGE_8_ATTEMPT_ORDER");
    private static final List<Integer> GMS_PLATFORM_ORDER =
            List.of(1, 2, 3, 4, 5, 6, 7, 8, 9);
    private static final List<Integer> JMS_PLATFORM_ORDER =
            List.of(1, 3, 6, 7, 4, 8, 2, 5, 9);

    private AgentLpqCombinationOrder() {
    }

    public static List<List<Integer>> fiveOfNine() {
        return fiveOfNine(parseMethod(STAGE_8_ATTEMPT_ORDER));
    }

    static List<List<Integer>> fiveOfNine(Method method) {
        if (method == null) throw new IllegalArgumentException("LPQ Stage 8 method is required");
        return combinations(method == Method.JMS ? JMS_PLATFORM_ORDER : GMS_PLATFORM_ORDER, 5);
    }

    static Method parseMethod(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("LPQ Stage 8 attempt order is required");
        }
        try {
            return Method.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(
                    "LPQ Stage 8 attempt order must be 'gms' or 'jms'", failure);
        }
    }

    static List<List<Integer>> combinations(List<Integer> platformOrder, int occupied) {
        if (platformOrder == null || platformOrder.isEmpty()
                || occupied < 1 || occupied > platformOrder.size()) {
            throw new IllegalArgumentException("valid LPQ combination dimensions are required");
        }
        List<List<Integer>> output = new ArrayList<>();
        appendCombinations(platformOrder, occupied, 0, new ArrayList<>(), output);
        return List.copyOf(output);
    }

    private static void appendCombinations(
            List<Integer> platformOrder, int occupied, int start,
            List<Integer> current, List<List<Integer>> output) {
        if (current.size() == occupied) {
            output.add(List.copyOf(current));
            return;
        }
        int needed = occupied - current.size();
        for (int index = start; index <= platformOrder.size() - needed; index++) {
            current.add(platformOrder.get(index));
            appendCombinations(platformOrder, occupied, index + 1, current, output);
            current.removeLast();
        }
    }
}
