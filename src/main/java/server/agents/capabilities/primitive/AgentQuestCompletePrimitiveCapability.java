package server.agents.capabilities.primitive;

import server.agents.capabilities.quest.AmherstScopePolicy;
import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityResult;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;

public final class AgentQuestCompletePrimitiveCapability
        implements AgentExecutableCapability<AgentQuestCompletePrimitiveCapability.Command> {
    public record Command(int questId, int npcId, boolean requireAmherstScope)
            implements AgentCapabilityCommand {
        public Command {
            if (questId <= 0 || npcId <= 0) {
                throw new IllegalArgumentException("quest and NPC ids are required");
            }
        }

        @Override
        public String type() {
            return "quest-complete";
        }
    }

    private final PrimitiveCapabilityGateway gateway;
    private final AmherstScopePolicy scopePolicy;

    public AgentQuestCompletePrimitiveCapability() {
        this(AgentPrimitiveCapabilityGatewayRuntime.gateway(), new AmherstScopePolicy());
    }

    public AgentQuestCompletePrimitiveCapability(PrimitiveCapabilityGateway gateway,
                                                   AmherstScopePolicy scopePolicy) {
        this.gateway = gateway;
        this.scopePolicy = scopePolicy;
    }

    @Override
    public String id() {
        return "quest-complete";
    }

    @Override
    public AgentCapabilityStep tick(AgentCapabilityContext context, Command command) {
        int status = gateway.questStatus(context.agent(), command.questId());
        if (status == 2) {
            return AgentCapabilityStep.terminal(AgentCapabilityResult.success("quest completion verified"));
        }
        if (status != 1) {
            return AgentPrimitiveResults.mismatch("quest is not started");
        }
        if (command.requireAmherstScope()) {
            var scope = scopePolicy.checkQuest(command.questId());
            if (!scope.allowed()) {
                return AgentPrimitiveResults.blocked(scope.status(), scope.reason());
            }
        }
        if (!gateway.canCompleteQuest(context.agent(), command.questId(), command.npcId())) {
            return AgentPrimitiveResults.missing("quest completion requirements are not satisfied");
        }
        if (!gateway.completeQuest(context.agent(), command.questId(), command.npcId())) {
            return AgentCapabilityStep.retry("normal quest completion was not accepted");
        }
        return AgentCapabilityStep.running("quest completed; verifying live quest state");
    }
}
