package server.agents.economy.decision;

import server.agents.economy.market.EconomicReason;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Converts actual quest eligibility and population cohorts into explainable demand waves. */
public final class QuestDemandForecaster {
    public List<DemandSignal> forecast(List<AgentCohort> cohorts, List<QuestRequirement> requirements) {
        List<DemandSignal> result = new ArrayList<>();
        for (QuestRequirement requirement : requirements) {
            int agents = 0;
            for (AgentCohort cohort : cohorts) {
                if (cohort.level() >= requirement.minimumLevel()
                        && cohort.level() <= requirement.maximumLevel()
                        && (requirement.jobFamilies().isEmpty()
                        || requirement.jobFamilies().contains(cohort.jobFamily()))) {
                    agents = Math.addExact(agents, cohort.agentCount());
                }
            }
            if (agents > 0) {
                result.add(new DemandSignal(requirement.itemId(),
                        Math.multiplyExact((long) agents, requirement.quantity()), agents,
                        requirement.urgency(), EconomicReason.QUEST_REQUIREMENT,
                        "quest=" + requirement.questId() + " eligibleAgents=" + agents));
            }
        }
        return List.copyOf(result);
    }

    public record AgentCohort(int level, String jobFamily, int agentCount) {
        public AgentCohort { if (level <= 0 || jobFamily == null || agentCount < 0) throw new IllegalArgumentException(); }
    }

    public record QuestRequirement(int questId, int itemId, int quantity, int minimumLevel,
                                   int maximumLevel, Set<String> jobFamilies, double urgency) {
        public QuestRequirement {
            if (questId <= 0 || itemId <= 0 || quantity <= 0 || minimumLevel < 0
                    || maximumLevel < minimumLevel || urgency < 0 || urgency > 1) throw new IllegalArgumentException();
            jobFamilies = jobFamilies == null ? Set.of() : Set.copyOf(jobFamilies);
        }
    }
}
