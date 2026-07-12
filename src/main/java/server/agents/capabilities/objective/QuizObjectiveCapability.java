package server.agents.capabilities.objective;

import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.integration.PrimitiveCapabilityGateway;

public final class QuizObjectiveCapability
        implements AgentExecutableCapability<QuizObjectiveCapability.Command> {
    public record Command(String objectiveId, int mapId, int questId, int npcId)
            implements AgentCapabilityCommand {
        public Command {
            if (objectiveId == null || objectiveId.isBlank() || mapId <= 0 || questId <= 0 || npcId <= 0) {
                throw new IllegalArgumentException("quiz objective parameters are required");
            }
        }

        @Override
        public String type() {
            return "quiz-objective";
        }
    }

    private final AmherstObjectiveCapabilitySupport support;

    public QuizObjectiveCapability() {
        support = new AmherstObjectiveCapabilitySupport();
    }

    public QuizObjectiveCapability(PrimitiveCapabilityGateway gateway) {
        support = new AmherstObjectiveCapabilitySupport(gateway);
    }

    @Override
    public String id() {
        return "quiz-objective";
    }

    @Override
    public AgentCapabilityStep tick(AgentCapabilityContext context, Command command) {
        AgentCapabilityStep failure = support.propagateChildFailure(context);
        if (failure != null) {
            return failure;
        }
        int status = support.gateway().questStatus(context.agent(), command.questId());
        if (status == 2) {
            return AgentCapabilityStep.terminal(AmherstObjectiveCapabilitySupport.success(
                    command.objectiveId(), "quiz quest verified"));
        }
        int phase = context.memory().intValue("phase", 0);
        if (phase == 0) {
            AgentCapabilityStep approach = support.approachNpc(context, command.mapId(), command.npcId());
            if (approach != null) {
                return approach;
            }
            context.memory().putInt("phase", 1);
            return AgentCapabilityStep.handoff(support.talk(command.mapId(), command.npcId(), command.questId()),
                    "quiz objective requests deterministic dialogue");
        }
        if (phase == 1) {
            int targetStatus = status == 0 ? 1 : 2;
            context.memory().putInt("targetStatus", targetStatus);
            context.memory().putInt("phase", 2);
            return AgentCapabilityStep.handoff(status == 0
                            ? support.questStart(command.questId(), command.npcId())
                            : support.questComplete(command.questId(), command.npcId()),
                    "quiz objective requests normal quest transition");
        }
        if (phase == 2) {
            int expected = context.memory().intValue("targetStatus", 1);
            context.memory().putInt("phase", expected == 2 ? 3 : 0);
            return AgentCapabilityStep.handoff(support.questState(command.questId(), expected),
                    "quiz objective verifies live quest state");
        }
        return AgentCapabilityStep.terminal(AmherstObjectiveCapabilitySupport.success(
                command.objectiveId(), "quiz quest verified"));
    }
}
