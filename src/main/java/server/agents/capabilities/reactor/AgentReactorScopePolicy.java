package server.agents.capabilities.reactor;

import server.agents.capabilities.AgentCapabilityStatus;
import server.agents.capabilities.quest.AmherstQuestCatalog;
import server.agents.capabilities.quest.AmherstScopeDecision;

import java.util.Set;

public final class AgentReactorScopePolicy {
    private static final int PIO_RECYCLING_QUEST_ID = 1008;
    private final Set<Integer> allowedMapIds;
    private final Set<Integer> allowedQuestIds;

    public AgentReactorScopePolicy() {
        this(Set.of(AmherstQuestCatalog.FINAL_MAP_ID), Set.of(PIO_RECYCLING_QUEST_ID));
    }

    public AgentReactorScopePolicy(Set<Integer> allowedMapIds, Set<Integer> allowedQuestIds) {
        this.allowedMapIds = Set.copyOf(allowedMapIds);
        this.allowedQuestIds = Set.copyOf(allowedQuestIds);
    }

    public AmherstScopeDecision check(AgentReactorInteractionRequest request) {
        if (request == null) {
            return AmherstScopeDecision.block(AgentCapabilityStatus.MISSING_REQUIREMENT,
                    "reactor interaction request is required");
        }
        if (!allowedQuestIds.contains(request.questId())) {
            return AmherstScopeDecision.block(AgentCapabilityStatus.BLOCKED_BY_SCOPE,
                    "reactor interaction is only allowed for covered reactor quests");
        }
        if (!allowedMapIds.contains(request.mapId())) {
            return AmherstScopeDecision.block(AgentCapabilityStatus.BLOCKED_FORBIDDEN_MAP,
                    "reactor interaction map is outside the allowed Amherst reactor scope");
        }
        return AmherstScopeDecision.allow();
    }
}
