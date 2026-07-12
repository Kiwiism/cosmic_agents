package server.agents.capabilities.objective;

import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.integration.PrimitiveCapabilityGateway;

public final class AutoCompleteQuestObjectiveCapability
        implements AgentExecutableCapability<AutoCompleteQuestObjectiveCapability.Command> {
    public record Command(String objectiveId, int mapId, int questId, int npcId)
            implements AgentCapabilityCommand {
        public Command {
            if (objectiveId == null || objectiveId.isBlank() || mapId <= 0 || questId <= 0 || npcId <= 0) {
                throw new IllegalArgumentException("auto-complete objective parameters are required");
            }
        }

        @Override
        public String type() {
            return "auto-complete-quest-objective";
        }
    }

    private final AmherstObjectiveCapabilitySupport support;

    public AutoCompleteQuestObjectiveCapability() {
        support = new AmherstObjectiveCapabilitySupport();
    }

    public AutoCompleteQuestObjectiveCapability(PrimitiveCapabilityGateway gateway) {
        support = new AmherstObjectiveCapabilitySupport(gateway);
    }

    @Override
    public String id() {
        return "auto-complete-quest-objective";
    }

    @Override
    public AgentCapabilityStep tick(AgentCapabilityContext context, Command command) {
        AgentCapabilityStep failure = support.propagateChildFailure(context);
        if (failure != null) {
            return failure;
        }
        if (support.gateway().questStatus(context.agent(), command.questId()) == 2) {
            return AgentCapabilityStep.terminal(AmherstObjectiveCapabilitySupport.success(
                    command.objectiveId(), "auto-complete quest verified"));
        }
        int phase = context.memory().intValue("phase", 0);
        if (phase == 0) {
            AgentCapabilityStep approach = support.approachNpc(context, command.mapId(), command.npcId());
            if (approach != null) {
                return approach;
            }
            context.memory().putInt("phase", 1);
            return AgentCapabilityStep.handoff(support.talk(command.mapId(), command.npcId(), command.questId()),
                    "auto-complete objective requests NPC dialogue");
        }
        if (phase == 1) {
            context.memory().putInt("phase", 2);
            return AgentCapabilityStep.handoff(support.questStart(command.questId(), command.npcId()),
                    "auto-complete objective requests normal quest start");
        }
        if (phase == 2) {
            context.memory().putInt("phase", 3);
            return AgentCapabilityStep.handoff(support.questState(command.questId(), 2),
                    "auto-complete objective verifies STARTED-to-COMPLETED transition");
        }
        return AgentCapabilityStep.terminal(AmherstObjectiveCapabilitySupport.success(
                command.objectiveId(), "auto-complete quest verified"));
    }
}
