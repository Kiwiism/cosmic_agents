package server.agents.capabilities.primitive;

import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityResult;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;

import java.util.Map;
import java.util.Set;

public final class AgentFinalStateVerificationCapability
        implements AgentExecutableCapability<AgentFinalStateVerificationCapability.Command> {
    public record Command(int expectedMapId,
                          Map<Integer, Integer> expectedQuestStatuses,
                          Map<Integer, Integer> requiredItemCounts,
                          boolean requireAlive,
                          Integer expectedJobId,
                          Integer expectedLevel,
                          Set<Integer> forbiddenCompletedQuestIds) implements AgentCapabilityCommand {
        public Command(int expectedMapId,
                       Map<Integer, Integer> expectedQuestStatuses,
                       Map<Integer, Integer> requiredItemCounts,
                       boolean requireAlive) {
            this(expectedMapId, expectedQuestStatuses, requiredItemCounts, requireAlive,
                    null, null, Set.of());
        }

        public Command {
            expectedQuestStatuses = expectedQuestStatuses == null ? Map.of() : Map.copyOf(expectedQuestStatuses);
            requiredItemCounts = requiredItemCounts == null ? Map.of() : Map.copyOf(requiredItemCounts);
            forbiddenCompletedQuestIds = forbiddenCompletedQuestIds == null
                    ? Set.of() : Set.copyOf(forbiddenCompletedQuestIds);
            if (expectedMapId <= 0 || expectedQuestStatuses.entrySet().stream()
                    .anyMatch(entry -> entry.getKey() <= 0 || entry.getValue() < 0 || entry.getValue() > 2)
                    || requiredItemCounts.entrySet().stream()
                    .anyMatch(entry -> entry.getKey() <= 0 || entry.getValue() < 0)
                    || forbiddenCompletedQuestIds.stream().anyMatch(questId -> questId <= 0)) {
                throw new IllegalArgumentException("final-state expectations are invalid");
            }
        }

        @Override
        public String type() {
            return "final-state-verification";
        }
    }

    private final PrimitiveCapabilityGateway gateway;

    public AgentFinalStateVerificationCapability() {
        this(AgentPrimitiveCapabilityGatewayRuntime.gateway());
    }

    public AgentFinalStateVerificationCapability(PrimitiveCapabilityGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public String id() {
        return "final-state-verification";
    }

    @Override
    public AgentCapabilityStep tick(AgentCapabilityContext context, Command command) {
        if (gateway.mapId(context.agent()) != command.expectedMapId()) {
            return AgentPrimitiveResults.mismatch("final map does not match");
        }
        if (command.requireAlive() && !gateway.alive(context.agent())) {
            return AgentPrimitiveResults.mismatch("agent is not alive");
        }
        var character = gateway.characterState(context.agent());
        if (command.expectedJobId() != null && character.jobId() != command.expectedJobId()) {
            return AgentPrimitiveResults.mismatch("final job does not match");
        }
        if (command.expectedLevel() != null && character.level() != command.expectedLevel()) {
            return AgentPrimitiveResults.mismatch("final level does not match");
        }
        for (var entry : command.expectedQuestStatuses().entrySet()) {
            if (gateway.questStatus(context.agent(), entry.getKey()) != entry.getValue()) {
                return AgentPrimitiveResults.mismatch("final quest state does not match");
            }
        }
        for (var entry : command.requiredItemCounts().entrySet()) {
            if (gateway.itemCount(context.agent(), entry.getKey()) < entry.getValue()) {
                return AgentPrimitiveResults.mismatch("final inventory state does not match");
            }
        }
        for (Integer questId : command.forbiddenCompletedQuestIds()) {
            if (gateway.questStatus(context.agent(), questId) == 2) {
                return AgentPrimitiveResults.blocked(
                        server.agents.capabilities.AgentCapabilityStatus.BLOCKED_FORBIDDEN_QUEST,
                        "forbidden quest is completed");
            }
        }
        return AgentCapabilityStep.terminal(AgentCapabilityResult.success("final live state verified"));
    }
}
