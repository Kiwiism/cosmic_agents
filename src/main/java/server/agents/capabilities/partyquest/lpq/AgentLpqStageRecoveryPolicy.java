package server.agents.capabilities.partyquest.lpq;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LPQ-local recovery thresholds, owned independently by each authored stage.
 *
 * <p>The coordinator selects exactly one profile from the active session stage. This keeps
 * detection clocks from one stage from silently controlling another and makes every LPQ stage
 * independently tunable without changing KPQ, HPQ, OPQ, or general Agent navigation.</p>
 */
record AgentLpqStageRecoveryPolicy(
        int stage,
        long submissionMs,
        long missingPassMs,
        long portalMs,
        long reactorMs,
        long traversalMs,
        long roomExitPlacementMs,
        long rallyRetryMs,
        long rallyRecoveryMs) {

    private static final Map<Integer, AgentLpqStageRecoveryPolicy> BY_STAGE = load();

    AgentLpqStageRecoveryPolicy {
        if (stage < 1 || stage > 9) throw new IllegalArgumentException("LPQ stage must be 1-9");
        if (submissionMs <= 0L || missingPassMs <= 0L || portalMs <= 0L
                || reactorMs <= 0L || traversalMs <= 0L || roomExitPlacementMs <= 0L
                || rallyRetryMs <= 0L || rallyRecoveryMs <= 0L) {
            throw new IllegalArgumentException("LPQ recovery thresholds must be positive");
        }
        if (roomExitPlacementMs >= portalMs) {
            throw new IllegalArgumentException(
                    "LPQ room portal placement must precede hard portal recovery");
        }
        if (rallyRetryMs >= rallyRecoveryMs) {
            throw new IllegalArgumentException(
                    "LPQ rally retry must precede rally recovery");
        }
    }

    static AgentLpqStageRecoveryPolicy forStage(int stage) {
        AgentLpqStageRecoveryPolicy policy = BY_STAGE.get(stage);
        if (policy == null) throw new IllegalArgumentException("invalid LPQ stage: " + stage);
        return policy;
    }

    static AgentLpqStageRecoveryPolicy parse(int stage, String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("LPQ stage recovery profile is required");
        }
        Map<String, Long> values = new LinkedHashMap<>();
        for (String component : encoded.split(";")) {
            String[] pair = component.trim().split("=", 2);
            if (pair.length != 2 || pair[0].isBlank() || pair[1].isBlank()) {
                throw new IllegalArgumentException(
                        "invalid LPQ Stage " + stage + " recovery component: " + component);
            }
            try {
                if (values.put(pair[0].trim(), Long.parseLong(pair[1].trim())) != null) {
                    throw new IllegalArgumentException(
                            "duplicate LPQ Stage " + stage + " recovery field: " + pair[0]);
                }
            } catch (NumberFormatException failure) {
                throw new IllegalArgumentException(
                        "invalid LPQ Stage " + stage + " recovery value: " + component, failure);
            }
        }
        AgentLpqStageRecoveryPolicy policy = new AgentLpqStageRecoveryPolicy(stage,
                required(values, stage, "submission"),
                required(values, stage, "missingPass"),
                required(values, stage, "portal"),
                required(values, stage, "reactor"),
                required(values, stage, "traversal"),
                required(values, stage, "roomExitPlacement"),
                required(values, stage, "rallyRetry"),
                required(values, stage, "rallyRecovery"));
        if (!values.isEmpty()) {
            throw new IllegalArgumentException(
                    "unknown LPQ Stage " + stage + " recovery fields: " + values.keySet());
        }
        return policy;
    }

    private static Map<Integer, AgentLpqStageRecoveryPolicy> load() {
        Map<Integer, AgentLpqStageRecoveryPolicy> policies = new LinkedHashMap<>();
        policies.put(1, parse(1, config.AgentTuning.stringValue(
                "server.agents.capabilities.partyquest.lpq.AgentLpqStageRecoveryPolicy.STAGE_1_PROFILE")));
        policies.put(2, parse(2, config.AgentTuning.stringValue(
                "server.agents.capabilities.partyquest.lpq.AgentLpqStageRecoveryPolicy.STAGE_2_PROFILE")));
        policies.put(3, parse(3, config.AgentTuning.stringValue(
                "server.agents.capabilities.partyquest.lpq.AgentLpqStageRecoveryPolicy.STAGE_3_PROFILE")));
        policies.put(4, parse(4, config.AgentTuning.stringValue(
                "server.agents.capabilities.partyquest.lpq.AgentLpqStageRecoveryPolicy.STAGE_4_PROFILE")));
        policies.put(5, parse(5, config.AgentTuning.stringValue(
                "server.agents.capabilities.partyquest.lpq.AgentLpqStageRecoveryPolicy.STAGE_5_PROFILE")));
        policies.put(6, parse(6, config.AgentTuning.stringValue(
                "server.agents.capabilities.partyquest.lpq.AgentLpqStageRecoveryPolicy.STAGE_6_PROFILE")));
        policies.put(7, parse(7, config.AgentTuning.stringValue(
                "server.agents.capabilities.partyquest.lpq.AgentLpqStageRecoveryPolicy.STAGE_7_PROFILE")));
        policies.put(8, parse(8, config.AgentTuning.stringValue(
                "server.agents.capabilities.partyquest.lpq.AgentLpqStageRecoveryPolicy.STAGE_8_PROFILE")));
        policies.put(9, parse(9, config.AgentTuning.stringValue(
                "server.agents.capabilities.partyquest.lpq.AgentLpqStageRecoveryPolicy.STAGE_9_PROFILE")));
        return Map.copyOf(policies);
    }

    private static long required(Map<String, Long> values, int stage, String name) {
        Long value = values.remove(name);
        if (value == null) {
            throw new IllegalArgumentException(
                    "missing LPQ Stage " + stage + " recovery field: " + name);
        }
        return value;
    }
}
