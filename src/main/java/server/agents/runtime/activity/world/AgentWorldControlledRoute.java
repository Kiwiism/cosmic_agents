package server.agents.runtime.activity.world;

import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.List;

/** Declarative validation fixture for the future controlled rollout. */
public record AgentWorldControlledRoute(
        String routeId,
        List<Stage> stages) {

    public AgentWorldControlledRoute {
        routeId = routeId == null ? "" : routeId.trim();
        stages = List.copyOf(stages == null ? List.of() : stages);
        if (routeId.isEmpty() || stages.isEmpty()) {
            throw new IllegalArgumentException("controlled route id and stages are required");
        }
    }

    public static AgentWorldControlledRoute level15() {
        return new AgentWorldControlledRoute("controlled-lv15", List.of(
                stage("maple-island", AgentActivityKind.QUESTING,
                        AgentWorldActivityRequestType.AUTHORED_PLAN, "maple-island-full-mvp",
                        null, AgentWorldMilestone.MAPLE_ISLAND_COMPLETE),
                stage("southperry-lith-handoff", AgentActivityKind.QUESTING,
                        AgentWorldActivityRequestType.AUTHORED_PLAN,
                        "southperry-to-lith-harbor",
                        AgentWorldMilestone.MAPLE_ISLAND_COMPLETE,
                        AgentWorldMilestone.VICTORIA_REACHED),
                stage("lith-town-life", AgentActivityKind.TOWN_LIFE,
                        AgentWorldActivityRequestType.TOWN_LIFE_VISIT, "lith-harbor:5m",
                        AgentWorldMilestone.VICTORIA_REACHED,
                        AgentWorldMilestone.VICTORIA_REACHED),
                stage("level15-foundation", AgentActivityKind.QUESTING,
                        AgentWorldActivityRequestType.AUTHORED_PLAN,
                        "victoria-level15-mvp", AgentWorldMilestone.VICTORIA_REACHED,
                        AgentWorldMilestone.LEVEL_15_FOUNDATION_COMPLETE)));
    }

    public static AgentWorldControlledRoute level30() {
        List<Stage> stages = new java.util.ArrayList<>(level15().stages());
        stages.add(stage("quest-or-hunt-to-25", AgentActivityKind.QUESTING,
                AgentWorldActivityRequestType.INDIVIDUAL_QUEST, "catalog:to-level25",
                AgentWorldMilestone.LEVEL_15_FOUNDATION_COMPLETE,
                AgentWorldMilestone.LEVEL_25_REACHED));
        stages.add(stage("kpq-to-30", AgentActivityKind.PARTY_QUEST,
                AgentWorldActivityRequestType.PARTY_QUEST_VISIT, "kpq:to-level30",
                AgentWorldMilestone.LEVEL_25_REACHED,
                AgentWorldMilestone.LEVEL_30_REACHED));
        stages.add(stage("second-job", AgentActivityKind.QUESTING,
                AgentWorldActivityRequestType.AUTHORED_PLAN, "victoria-second-job",
                AgentWorldMilestone.LEVEL_30_REACHED,
                AgentWorldMilestone.SECOND_JOB_COMPLETE));
        return new AgentWorldControlledRoute("controlled-lv30", stages);
    }

    private static Stage stage(
            String id, AgentActivityKind kind, AgentWorldActivityRequestType requestType,
            String requestId, AgentWorldMilestone required, AgentWorldMilestone terminal) {
        return new Stage(id, kind, requestType, requestId, required, terminal);
    }

    public record Stage(
            String stageId,
            AgentActivityKind kind,
            AgentWorldActivityRequestType requestType,
            String requestId,
            AgentWorldMilestone requiredMilestone,
            AgentWorldMilestone terminalMilestone) {
        public Stage {
            stageId = stageId == null ? "" : stageId.trim();
            requestId = requestId == null ? "" : requestId.trim();
            if (stageId.isEmpty() || kind == null || requestType == null || requestId.isEmpty()
                    || terminalMilestone == null) {
                throw new IllegalArgumentException("complete controlled route stage is required");
            }
        }
    }
}
