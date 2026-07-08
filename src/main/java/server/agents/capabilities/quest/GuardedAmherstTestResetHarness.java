package server.agents.capabilities.quest;

import server.agents.capabilities.AgentCapabilityStatus;

import java.util.Set;

public final class GuardedAmherstTestResetHarness implements AmherstTestResetHarness {
    private final boolean enabled;
    private final Set<Integer> allowedCharacterIds;
    private final Set<String> allowedCharacterNames;

    public GuardedAmherstTestResetHarness(boolean enabled, Set<Integer> allowedCharacterIds,
            Set<String> allowedCharacterNames) {
        this.enabled = enabled;
        this.allowedCharacterIds = Set.copyOf(allowedCharacterIds);
        this.allowedCharacterNames = Set.copyOf(allowedCharacterNames);
    }

    @Override
    public AmherstTestResetResult reset(AmherstTestResetRequest request) {
        AmherstTestResetResult guard = validate(request);
        if (!guard.allowed()) {
            return guard;
        }
        return AmherstTestResetResult.blocked(AgentCapabilityStatus.NOT_READY,
                "Amherst reset harness guard passed; live character mutation is intentionally not wired yet");
    }

    AmherstTestResetResult validate(AmherstTestResetRequest request) {
        if (!enabled) {
            return AmherstTestResetResult.blocked(AgentCapabilityStatus.BLOCKED_BY_SCOPE,
                    "Amherst test reset harness is disabled");
        }
        if (request == null) {
            return AmherstTestResetResult.blocked(AgentCapabilityStatus.MISSING_REQUIREMENT,
                    "reset request is required");
        }
        if (request.mode() == null) {
            return AmherstTestResetResult.blocked(AgentCapabilityStatus.MISSING_REQUIREMENT,
                    "reset mode is required");
        }
        boolean allowedId = allowedCharacterIds.contains(request.characterId());
        boolean allowedName = request.characterName() != null && allowedCharacterNames.contains(request.characterName());
        if (!allowedId && !allowedName) {
            return AmherstTestResetResult.blocked(AgentCapabilityStatus.BLOCKED_BY_SCOPE,
                    "character is not allowlisted for Amherst test reset");
        }
        if (request.mode() == AmherstTestResetMode.QUEST_SCENARIO
                && !AmherstQuestCatalog.isRequiredQuest(request.questId())) {
            return AmherstTestResetResult.blocked(AgentCapabilityStatus.BLOCKED_BY_SCOPE,
                    "quest scenario reset requires a covered Amherst quest id");
        }
        return AmherstTestResetResult.allowed("reset guard accepted");
    }
}
