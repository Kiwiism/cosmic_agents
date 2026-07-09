package server.agents.capabilities.quest;

import server.agents.capabilities.AgentCapabilityStatus;
import server.agents.capabilities.npc.AgentNpcInteractionRequest;
import server.agents.capabilities.npc.AgentNpcInteractionType;
import server.agents.capabilities.npc.AgentNpcInteractionValidator;

import java.util.Map;

public final class AgentQuestRequirementValidator {
    private final AmherstScopePolicy scopePolicy;
    private final AgentNpcInteractionValidator npcValidator;

    public AgentQuestRequirementValidator() {
        this(new AmherstScopePolicy(), new AgentNpcInteractionValidator());
    }

    public AgentQuestRequirementValidator(AmherstScopePolicy scopePolicy, AgentNpcInteractionValidator npcValidator) {
        this.scopePolicy = scopePolicy;
        this.npcValidator = npcValidator;
    }

    public AgentQuestCapabilityResult validateStart(AgentQuestCapabilityRequest request) {
        AgentQuestCapabilityResult base = validateCommon(request, true);
        if (base.status() != AgentCapabilityStatus.SUCCESS) {
            return base;
        }
        if (request.snapshot().statusOf(request.questId()) != AgentQuestStatus.NOT_STARTED) {
            return AgentQuestCapabilityResult.blocked(AgentCapabilityStatus.MISSING_REQUIREMENT,
                    "quest is not in NOT_STARTED state", request);
        }
        if (request.npcId() != request.requirement().startNpcId()) {
            return AgentQuestCapabilityResult.blocked(AgentCapabilityStatus.BLOCKED_FORBIDDEN_NPC,
                    "quest start NPC does not match requirement", request);
        }
        return AgentQuestCapabilityResult.pending("quest start validated; live execution is not wired yet", request);
    }

    public AgentQuestCapabilityResult validateComplete(AgentQuestCapabilityRequest request) {
        AgentQuestCapabilityResult base = validateCommon(request, false);
        if (base.status() != AgentCapabilityStatus.SUCCESS) {
            return base;
        }
        if (request.snapshot().statusOf(request.questId()) != AgentQuestStatus.STARTED) {
            return AgentQuestCapabilityResult.blocked(AgentCapabilityStatus.MISSING_REQUIREMENT,
                    "quest is not in STARTED state", request);
        }
        if (!request.requirement().autoComplete() && request.npcId() != request.requirement().completeNpcId()) {
            return AgentQuestCapabilityResult.blocked(AgentCapabilityStatus.BLOCKED_FORBIDDEN_NPC,
                    "quest completion NPC does not match requirement", request);
        }
        for (Map.Entry<Integer, Integer> entry : request.requirement().requiredItems().entrySet()) {
            if (request.snapshot().itemCount(entry.getKey()) < entry.getValue()) {
                return AgentQuestCapabilityResult.blocked(AgentCapabilityStatus.MISSING_REQUIREMENT,
                        "required item count is not met", request);
            }
        }
        for (Map.Entry<Integer, Integer> entry : request.requirement().requiredMobKills().entrySet()) {
            if (request.snapshot().mobKillCount(entry.getKey()) < entry.getValue()) {
                return AgentQuestCapabilityResult.blocked(AgentCapabilityStatus.MISSING_REQUIREMENT,
                        "required mob kill count is not met", request);
            }
        }
        for (Map.Entry<Integer, Integer> entry : request.requirement().requiredProgressValues().entrySet()) {
            if (request.snapshot().questProgress(entry.getKey()) < entry.getValue()) {
                return AgentQuestCapabilityResult.blocked(AgentCapabilityStatus.MISSING_REQUIREMENT,
                        "required quest progress value is not met", request);
            }
        }
        return AgentQuestCapabilityResult.pending("quest completion validated; live execution is not wired yet",
                request);
    }

    private AgentQuestCapabilityResult validateCommon(AgentQuestCapabilityRequest request, boolean start) {
        if (request == null) {
            return AgentQuestCapabilityResult.blocked(AgentCapabilityStatus.MISSING_REQUIREMENT,
                    "quest capability request is required", null);
        }
        if (request.requireAmherstScope()) {
            AmherstScopeDecision questScope = scopePolicy.checkQuest(request.questId());
            if (!questScope.allowed()) {
                return AgentQuestCapabilityResult.blocked(questScope.status(), questScope.reason(), request);
            }
            AmherstScopeDecision mapScope = scopePolicy.checkMap(request.mapId());
            if (!mapScope.allowed()) {
                return AgentQuestCapabilityResult.blocked(mapScope.status(), mapScope.reason(), request);
            }
        }
        if (request.requirement() == null) {
            return AgentQuestCapabilityResult.blocked(AgentCapabilityStatus.MISSING_REQUIREMENT,
                    "quest requirement metadata is required", request);
        }
        if (request.snapshot().level() < request.requirement().minLevel()) {
            return AgentQuestCapabilityResult.blocked(AgentCapabilityStatus.MISSING_REQUIREMENT,
                    "agent level is below quest requirement", request);
        }
        if (request.requirement().maxLevel() > 0 && request.snapshot().level() > request.requirement().maxLevel()) {
            return AgentQuestCapabilityResult.blocked(AgentCapabilityStatus.MISSING_REQUIREMENT,
                    "agent level is above quest requirement", request);
        }
        if (!request.snapshot().jobAllowed(request.requirement().allowedJobIds())) {
            return AgentQuestCapabilityResult.blocked(AgentCapabilityStatus.MISSING_REQUIREMENT,
                    "agent job does not match quest requirement", request);
        }
        for (Integer prerequisite : request.requirement().prerequisiteQuestIds()) {
            if (!request.snapshot().hasCompleted(prerequisite)) {
                return AgentQuestCapabilityResult.blocked(AgentCapabilityStatus.MISSING_REQUIREMENT,
                        "quest prerequisite is not complete", request);
            }
        }

        if (!start && request.requirement().autoComplete()) {
            return AgentQuestCapabilityResult.success("auto-complete quest does not require an NPC", request);
        }

        AgentNpcInteractionType type = start ? AgentNpcInteractionType.QUEST_START
                : AgentNpcInteractionType.QUEST_COMPLETE;
        var npc = npcValidator.validate(new AgentNpcInteractionRequest(request.mapId(), request.npcId(),
                request.questId(), type, request.agentPosition(), request.npcPosition(), request.maxRangePx(),
                false, null, request.questId()));
        if (npc.status() != AgentCapabilityStatus.NOT_READY && npc.status() != AgentCapabilityStatus.SUCCESS) {
            return AgentQuestCapabilityResult.blocked(npc.status(), npc.message(), request);
        }
        return AgentQuestCapabilityResult.success("quest common requirements are met", request);
    }
}
