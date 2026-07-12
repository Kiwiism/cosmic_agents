package server.agents.plans.amherst;

import server.agents.capabilities.quest.AmherstQuestCatalog;
import server.agents.capabilities.quest.AmherstScopePolicy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AmherstPlanValidator {
    private final AmherstScopePolicy scopePolicy;

    public AmherstPlanValidator() {
        this(new AmherstScopePolicy());
    }

    public AmherstPlanValidator(AmherstScopePolicy scopePolicy) {
        this.scopePolicy = scopePolicy;
    }

    public List<AmherstPlanValidationIssue> validate(AmherstPlanCard card) {
        List<AmherstPlanValidationIssue> issues = new ArrayList<>();
        if (card.schemaVersion() != 1) {
            issue(issues, AmherstPlanValidationCode.UNSUPPORTED_SCHEMA, "schemaVersion", "schemaVersion must be 1");
        }
        requireText(issues, "planId", card.planId());
        if (!"ordered-with-live-quest-validation".equals(card.objectiveMode())) {
            issue(issues, AmherstPlanValidationCode.INVALID_VALUE, "objectiveMode",
                    "objectiveMode must require ordered live-state validation");
        }
        validateFocus(card.focusPolicy(), issues);
        validateMap(card.entryCriteria().requiredStartMapId(), "entryCriteria.requiredStartMapId", issues);
        validateMap(card.exitCriteria().finalMapId(), "exitCriteria.finalMapId", issues);
        validateQuestPolicy(card, issues);
        if (!card.exitCriteria().forbiddenMapIds().contains(1010000)) {
            issue(issues, AmherstPlanValidationCode.MISSING_VALUE, "exitCriteria.forbiddenActions",
                    "Training Center map 1010000 must be forbidden");
        }
        if (!card.exitCriteria().forbiddenNpcIds().contains(AmherstQuestCatalog.SHANKS_NPC_ID)) {
            issue(issues, AmherstPlanValidationCode.MISSING_VALUE, "exitCriteria.forbiddenActions",
                    "Shanks NPC 22000 must be forbidden");
        }
        if (card.objectives().isEmpty()) {
            issue(issues, AmherstPlanValidationCode.MISSING_VALUE, "route",
                    "plan must declare at least one objective");
        }

        Set<String> objectiveIds = new HashSet<>();
        for (AmherstPlanObjective objective : card.objectives()) {
            String path = "route[" + objective.routeIndex() + "].objectives[" + objective.objectiveIndex() + "]";
            if (!objectiveIds.add(objective.objectiveId())) {
                issue(issues, AmherstPlanValidationCode.DUPLICATE_OBJECTIVE_ID, path + ".objectiveId",
                        "objective id must be unique: " + objective.objectiveId());
            }
            validateObjective(card, objective, path, issues);
        }
        return List.copyOf(issues);
    }

    private void validateFocus(AmherstPlanCard.FocusPolicy focus, List<AmherstPlanValidationIssue> issues) {
        if (focus == null || !"locked".equals(focus.focusLevel()) || focus.allowSidetracks()
                || !Set.of("emergency").equals(focus.allowedSidetrackTypes())
                || !"always".equals(focus.returnToPlan())) {
            issue(issues, AmherstPlanValidationCode.FOCUS_POLICY_MISMATCH, "focusPolicy",
                    "Amherst Phase 2 requires locked focus with emergency-only sidetracks");
        }
    }

    private void validateQuestPolicy(AmherstPlanCard card, List<AmherstPlanValidationIssue> issues) {
        if (!card.requiredQuestIds().equals(AmherstQuestCatalog.requiredQuestIdSet())) {
            issue(issues, AmherstPlanValidationCode.QUEST_POLICY_MISMATCH, "questPolicy.requiredQuestIds",
                    "required quests must match AmherstQuestCatalog");
        }
        Set<Integer> overlap = new HashSet<>(card.requiredQuestIds());
        overlap.retainAll(card.excludedQuestIds());
        if (!overlap.isEmpty()) {
            issue(issues, AmherstPlanValidationCode.QUEST_POLICY_MISMATCH, "questPolicy",
                    "required and excluded quest ids overlap");
        }
    }

    private void validateObjective(AmherstPlanCard card,
                                   AmherstPlanObjective objective,
                                   String path,
                                   List<AmherstPlanValidationIssue> issues) {
        if (objective.kind() == null) {
            issue(issues, AmherstPlanValidationCode.UNKNOWN_OBJECTIVE_KIND, path + ".kind",
                    "objective kind is unknown");
            return;
        }
        validateMap(objective.mapId(), path + ".mapId", issues);
        for (Integer questId : objective.allQuestIds()) {
            if (card.excludedQuestIds().contains(questId) || !card.requiredQuestIds().contains(questId)) {
                issue(issues, AmherstPlanValidationCode.FORBIDDEN_QUEST, path + ".questId",
                        "quest is outside the Amherst required scope: " + questId);
            }
        }
        if (objective.npcId() != null) {
            validateNpc(objective.npcId(), path + ".npcId", issues);
        }
        for (Integer npcId : objective.npcIds()) {
            validateNpc(npcId, path + ".npcIds", issues);
        }
        switch (objective.kind()) {
            case QUEST_START, QUEST_COMPLETE -> requirePositive(
                    issues, path + ".questId", objective.questId(), "quest id");
            case QUEST_CHAIN, QUEST_CHAIN_IF_AVAILABLE -> {
                if (objective.questIds().isEmpty()) {
                    issue(issues, AmherstPlanValidationCode.MISSING_VALUE, path + ".questIds",
                            "quest chain requires quest ids");
                }
            }
            case USE_ITEM -> {
                requirePositive(issues, path + ".itemId", objective.itemId(), "item id");
                requirePositive(issues, path + ".forQuestId", objective.questId(), "quest id");
            }
            case KILL_MOBS -> {
                requirePositive(issues, path + ".forQuestId", objective.questId(), "quest id");
                if (objective.mobIds().isEmpty() || objective.mobIds().size() != objective.counts().size()
                        || objective.mobIds().stream().anyMatch(id -> id <= 0)
                        || objective.counts().stream().anyMatch(count -> count <= 0)) {
                    issue(issues, AmherstPlanValidationCode.INVALID_VALUE, path,
                            "kill objective requires matching positive mob ids and counts");
                }
            }
            case REACTOR_HIT -> requirePositive(
                    issues, path + ".forQuestId", objective.questId(), "quest id");
            case REACTOR_BOX_ITEMS -> {
                requirePositive(issues, path + ".forQuestId", objective.questId(), "quest id");
                if (objective.itemIds().isEmpty() || objective.itemIds().stream().anyMatch(id -> id <= 0)) {
                    issue(issues, AmherstPlanValidationCode.INVALID_VALUE, path + ".itemIds",
                            "reactor item objective requires positive item ids");
                }
            }
            case STOP_PLAN -> requireText(issues, path + ".reason", objective.reason());
        }
    }

    private void validateMap(int mapId, String path, List<AmherstPlanValidationIssue> issues) {
        var scope = scopePolicy.checkMap(mapId);
        if (!scope.allowed()) {
            issue(issues, AmherstPlanValidationCode.FORBIDDEN_MAP, path, scope.reason());
        }
    }

    private void validateNpc(int npcId, String path, List<AmherstPlanValidationIssue> issues) {
        if (npcId <= 0) {
            issue(issues, AmherstPlanValidationCode.INVALID_VALUE, path, "NPC id must be positive");
            return;
        }
        var scope = scopePolicy.checkNpcTravel(npcId);
        if (!scope.allowed()) {
            issue(issues, AmherstPlanValidationCode.FORBIDDEN_NPC, path, scope.reason());
        }
    }

    private static void requirePositive(List<AmherstPlanValidationIssue> issues,
                                        String path,
                                        Integer value,
                                        String label) {
        if (value == null || value <= 0) {
            issue(issues, AmherstPlanValidationCode.MISSING_VALUE, path, label + " must be positive");
        }
    }

    private static void requireText(List<AmherstPlanValidationIssue> issues, String path, String value) {
        if (value == null || value.isBlank()) {
            issue(issues, AmherstPlanValidationCode.MISSING_VALUE, path, "value is required");
        }
    }

    private static void issue(List<AmherstPlanValidationIssue> issues,
                              AmherstPlanValidationCode code,
                              String path,
                              String message) {
        issues.add(new AmherstPlanValidationIssue(code, path, message));
    }
}
