package server.agents.runtime.activity.world;

import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.ArrayList;
import java.util.List;

/** Conservative shadow-only policy used to validate lifecycle decisions before rollout. */
public final class AgentWorldBaselineProposalProvider implements AgentWorldProposalProvider {
    @Override
    public List<AgentWorldActivityIntent> propose(
            AgentWorldContext context, AgentWorldMilestoneSnapshot milestones) {
        if (context == null || milestones == null) {
            throw new IllegalArgumentException("context and milestones are required");
        }
        List<AgentWorldActivityIntent> intents = new ArrayList<>();
        if (!milestones.achieved(AgentWorldMilestone.VICTORIA_REACHED)) {
            boolean islandComplete = milestones.achieved(
                    AgentWorldMilestone.MAPLE_ISLAND_COMPLETE);
            intents.add(intent(islandComplete ? "milestone:lith-handoff"
                            : "milestone:maple-island",
                    AgentActivityKind.QUESTING, 900, 100L, true,
                    islandComplete ? "Maple Island is complete; Victoria handoff remains"
                            : "Maple Island completion is not yet observed",
                    AgentWorldActivityRequestType.AUTHORED_PLAN,
                    islandComplete ? "southperry-to-lith-harbor"
                            : "maple-island-full-mvp"));
            return List.copyOf(intents);
        }
        if (!milestones.achieved(AgentWorldMilestone.FIRST_JOB_COMPLETE)) {
            intents.add(intent("milestone:first-job", AgentActivityKind.QUESTING,
                    900, 100L, true, "first job is incomplete",
                    AgentWorldActivityRequestType.AUTHORED_PLAN, "victoria-level15-mvp"));
            return List.copyOf(intents);
        }
        if (!milestones.achieved(AgentWorldMilestone.LEVEL_15_FOUNDATION_COMPLETE)) {
            boolean knownIncomplete = milestones.status(
                    AgentWorldMilestone.LEVEL_15_FOUNDATION_COMPLETE)
                    == AgentWorldMilestoneStatus.NOT_ACHIEVED;
            intents.add(intent("milestone:level15-foundation", AgentActivityKind.QUESTING,
                    knownIncomplete ? 850 : 700, 100L, true,
                    knownIncomplete ? "career checkpoint has not completed"
                            : "foundation completion is unknown; retain existing plan if present",
                    AgentWorldActivityRequestType.AUTHORED_PLAN,
                    "victoria-level15-mvp"));
        }
        if (context.level() < 30) {
            intents.add(intent("progression:individual-quest", AgentActivityKind.QUESTING,
                    500, 70L, context.level() >= 15,
                    "individual Victoria quests are exposed as concrete Director catalog actions",
                    AgentWorldActivityRequestType.INDIVIDUAL_QUEST, "catalog:auto"));
            intents.add(intent("progression:hunting", AgentActivityKind.HUNTING,
                    500, 60L, context.level() >= 15,
                    "level-appropriate field visit candidate",
                    AgentWorldActivityRequestType.FIELD_VISIT, "field:auto"));
            boolean kpqLevel = milestones.achieved(AgentWorldMilestone.KPQ_LEVEL_ELIGIBLE);
            intents.add(intent("progression:kpq", AgentActivityKind.PARTY_QUEST,
                    520, context.ownsSquishyShoes() ? 25L : 75L, kpqLevel,
                    kpqLevel ? "level gate passed; accuracy and party readiness are not yet proven"
                            : "outside the KPQ level gate",
                    AgentWorldActivityRequestType.PARTY_QUEST_VISIT, "kpq:auto"));
        } else if (!milestones.achieved(AgentWorldMilestone.SECOND_JOB_COMPLETE)) {
            intents.add(intent("milestone:second-job", AgentActivityKind.QUESTING,
                    900, 100L, true, "level 30 reached and second job is incomplete",
                    AgentWorldActivityRequestType.AUTHORED_PLAN, "victoria-second-job"));
        }
        intents.add(intent("optional:town-life", AgentActivityKind.TOWN_LIFE,
                100, 20L, true, "bounded rest and town activity are available",
                AgentWorldActivityRequestType.TOWN_LIFE_VISIT, "town-life:auto"));
        intents.add(intent("optional:commerce", AgentActivityKind.COMMERCE,
                200, 10L, false, "economic need evidence is not captured yet",
                AgentWorldActivityRequestType.COMMERCE_VISIT, "commerce:auto"));
        return List.copyOf(intents);
    }

    private static AgentWorldActivityIntent intent(
            String id, AgentActivityKind kind, int priority, long utility, boolean eligible,
            String evidence, AgentWorldActivityRequestType requestType, String requestId) {
        return new AgentWorldActivityIntent(new AgentWorldActivityProposal(
                id, kind, priority, utility, eligible, evidence), requestType, requestId);
    }
}
