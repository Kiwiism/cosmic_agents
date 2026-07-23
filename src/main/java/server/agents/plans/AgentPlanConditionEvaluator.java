package server.agents.plans;

import client.Character;
import client.QuestStatus;
import server.agents.progression.AgentCareerProgressionState;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.Map;

/** Shared fact vocabulary used by entry, exit, and future branch conditions for every plan. */
public final class AgentPlanConditionEvaluator {
    private AgentPlanConditionEvaluator() {
    }

    public static Evaluation evaluateAll(List<AgentPlanDefinition.Condition> conditions,
                                         AgentRuntimeEntry entry,
                                         Character agent,
                                         AgentPlanSessionState session) {
        for (AgentPlanDefinition.Condition condition : conditions) {
            Evaluation evaluation = evaluate(condition, entry, agent, session);
            if (!evaluation.satisfied()) {
                return evaluation;
            }
        }
        return new Evaluation(true, "");
    }

    private static Evaluation evaluate(AgentPlanDefinition.Condition condition,
                                       AgentRuntimeEntry entry,
                                       Character agent,
                                       AgentPlanSessionState session) {
        Object actual = fact(condition.fact(), entry, agent, session);
        boolean satisfied = compare(actual, condition.operator(), condition.value(), session.inputs());
        return new Evaluation(satisfied, satisfied ? "" : condition.fact() + ' '
                + condition.operator() + ' ' + condition.value() + " (actual=" + actual + ')');
    }

    private static Object fact(String fact,
                               AgentRuntimeEntry entry,
                               Character agent,
                               AgentPlanSessionState session) {
        if ("map.id".equals(fact)) return agent.getMapId();
        if ("region".equals(fact)) return agent.getMapId() < 100_000_000 ? "maple-island" : "victoria";
        if ("character.level".equals(fact)) return agent.getLevel();
        if ("character.firstJob".equals(fact)) return agent.getJob().getId() != 0;
        if ("career.bundle".equals(fact)) return entry.capabilityStates()
                .require(AgentCareerProgressionState.STATE_KEY).bundle() != null;
        if ("career.bundleId".equals(fact)) {
            var bundle = entry.capabilityStates()
                    .require(AgentCareerProgressionState.STATE_KEY).bundle();
            return bundle == null ? null : bundle.bundleId();
        }
        if ("career.stage".equals(fact)) return entry.capabilityStates()
                .require(AgentCareerProgressionState.STATE_KEY).stage().name();
        if ("navigation.lithTownSide".equals(fact)) {
            return agent.getMapId() == 104_000_000
                    && agent.getPosition() != null
                    && agent.getPosition().x <= 2_700;
        }
        if ("plan.steps".equals(fact)) {
            AgentPlanDefinition plan = AgentPlanRepository.defaultRepository().require(session.planId());
            return session.stepIndex() >= plan.steps().size();
        }
        if (fact.startsWith("input.")) return session.inputs().get(fact.substring("input.".length()));
        if (fact.startsWith("quest.")) {
            try {
                int questId = Integer.parseInt(fact.substring("quest.".length()));
                return agent.getQuestStatus(questId);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean compare(Object actual,
                                   String operator,
                                   Object expected,
                                   Map<String, Object> inputs) {
        if ("present".equals(operator)) {
            return Boolean.TRUE.equals(expected) == truthy(actual);
        }
        if ("eq".equals(operator)) {
            return actual != null && String.valueOf(actual).equals(String.valueOf(expected));
        }
        if ("gte".equals(operator)) {
            return number(actual) >= number(expected);
        }
        if ("gte-input".equals(operator)) {
            return number(actual) >= number(inputs.get(String.valueOf(expected)));
        }
        if ("in".equals(operator) && expected instanceof List<?> values) {
            return actual != null && values.stream()
                    .anyMatch(value -> String.valueOf(actual).equals(String.valueOf(value)));
        }
        if ("between".equals(operator) && expected instanceof List<?> bounds && bounds.size() == 2) {
            double value = number(actual);
            return value >= number(bounds.get(0)) && value <= number(bounds.get(1));
        }
        if ("active".equals(operator) && actual instanceof Number status) {
            return Boolean.TRUE.equals(expected)
                    == (status.intValue() == QuestStatus.Status.STARTED.getId());
        }
        if ("all-succeeded".equals(operator)) {
            return Boolean.TRUE.equals(expected) == truthy(actual);
        }
        return false;
    }

    private static boolean truthy(Object value) {
        return value instanceof Boolean flag ? flag : value != null;
    }

    private static double number(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return Double.NaN;
        }
    }

    public record Evaluation(boolean satisfied, String reason) {
    }
}
