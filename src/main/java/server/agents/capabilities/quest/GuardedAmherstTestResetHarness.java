package server.agents.capabilities.quest;

import server.agents.plans.amherst.AmherstQuestCatalog;

import server.agents.capabilities.AgentCapabilityStatus;

import java.util.Set;

public final class GuardedAmherstTestResetHarness implements AmherstTestResetHarness {
    private final boolean enabled;
    private final Set<Integer> allowedCharacterIds;
    private final Set<String> allowedCharacterNames;
    private final AmherstTestResetPort resetPort;

    public GuardedAmherstTestResetHarness(boolean enabled, Set<Integer> allowedCharacterIds,
            Set<String> allowedCharacterNames) {
        this(enabled, allowedCharacterIds, allowedCharacterNames, null);
    }

    public GuardedAmherstTestResetHarness(boolean enabled,
            Set<Integer> allowedCharacterIds,
            Set<String> allowedCharacterNames,
            AmherstTestResetPort resetPort) {
        this.enabled = enabled;
        this.allowedCharacterIds = Set.copyOf(allowedCharacterIds);
        this.allowedCharacterNames = Set.copyOf(allowedCharacterNames);
        this.resetPort = resetPort;
    }

    @Override
    public AmherstTestResetResult reset(AmherstTestResetRequest request) {
        AmherstTestResetResult guard = validate(request);
        if (!guard.allowed()) {
            return guard;
        }
        if (resetPort == null) {
            return AmherstTestResetResult.blocked(AgentCapabilityStatus.NOT_READY,
                    "Amherst reset harness guard passed; live reset port is not configured");
        }
        AmherstTestResetResult result = resetPort.reset(request);
        return result == null
                ? AmherstTestResetResult.blocked(AgentCapabilityStatus.FAILED,
                        "Amherst reset port returned no result")
                : result;
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
