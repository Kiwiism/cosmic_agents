package server.agents.capabilities.partyquest.kpq;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministically assigns humans to puzzle movement ranks using configurable ratios. */
final class AgentKpqPuzzleParticipantOrder {
    private static final double[] HUMAN_ROLE_WEIGHTS = validateWeights(new double[]{
            config.AgentTuning.doubleValue(
                    "server.agents.capabilities.partyquest.kpq.AgentKpqPuzzleParticipantOrder.HUMAN_LEAST_MOVEMENT_WEIGHT"),
            config.AgentTuning.doubleValue(
                    "server.agents.capabilities.partyquest.kpq.AgentKpqPuzzleParticipantOrder.HUMAN_MIDDLE_MOVEMENT_WEIGHT"),
            config.AgentTuning.doubleValue(
                    "server.agents.capabilities.partyquest.kpq.AgentKpqPuzzleParticipantOrder.HUMAN_MOST_MOVEMENT_WEIGHT")});

    private AgentKpqPuzzleParticipantOrder() {
    }

    static List<AgentKpqMemberState> order(List<AgentKpqMemberState> participants, long seed) {
        if (participants == null || participants.size() < 3) return List.of();
        List<AgentKpqMemberState> firstThree = participants.stream()
                .sorted(Comparator.comparingInt(AgentKpqMemberState::partyNumber))
                .limit(3).toList();
        List<AgentKpqMemberState> humans = firstThree.stream()
                .filter(member -> member.memberType() == AgentKpqMemberState.MemberType.HUMAN)
                .toList();
        if (humans.isEmpty()) return firstThree;

        AgentKpqMemberState[] slots = new AgentKpqMemberState[3];
        List<Integer> available = new ArrayList<>(List.of(0, 1, 2));
        for (int index = 0; index < humans.size(); index++) {
            int rank = chooseRank(available, seed, humans.get(index).characterId(), index, HUMAN_ROLE_WEIGHTS);
            slots[rank] = humans.get(index);
            available.remove(Integer.valueOf(rank));
        }
        List<AgentKpqMemberState> agents = firstThree.stream()
                .filter(member -> member.memberType() == AgentKpqMemberState.MemberType.AGENT)
                .toList();
        for (int index = 0; index < agents.size(); index++) slots[available.get(index)] = agents.get(index);
        return List.of(slots);
    }

    static int chooseRank(
            List<Integer> available, long seed, int characterId, int humanIndex, double[] weights) {
        if (available == null || available.isEmpty()) throw new IllegalArgumentException("available rank is required");
        double total = available.stream().mapToDouble(rank -> weights[rank]).sum();
        if (total <= 0.0d) return available.getFirst();
        long mixed = mix(seed ^ ((long) characterId << 21) ^ humanIndex * 0x9E3779B97F4A7C15L);
        double draw = ((mixed >>> 11) * 0x1.0p-53) * total;
        double cursor = 0.0d;
        for (int rank : available) {
            cursor += weights[rank];
            if (draw < cursor) return rank;
        }
        return available.getLast();
    }

    static double[] parseWeights(String value) {
        String[] tokens = value == null ? new String[0] : value.split(",");
        if (tokens.length != 3) {
            throw new IllegalStateException("KPQ human puzzle role weights require three ratios");
        }
        double[] weights = new double[3];
        for (int index = 0; index < tokens.length; index++) {
            try {
                weights[index] = Double.parseDouble(tokens[index].trim());
            } catch (NumberFormatException failure) {
                throw new IllegalStateException("KPQ human puzzle role weight is not numeric", failure);
            }
        }
        return validateWeights(weights);
    }

    private static double[] validateWeights(double[] weights) {
        double total = 0.0d;
        for (double weight : weights) {
            if (!Double.isFinite(weight) || weight < 0.0d) {
                throw new IllegalStateException("KPQ human puzzle role weights must be finite and non-negative");
            }
            total += weight;
        }
        if (total <= 0.0d) throw new IllegalStateException("At least one KPQ human puzzle role weight must be positive");
        return weights;
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53l;
        return value ^ value >>> 33;
    }
}
