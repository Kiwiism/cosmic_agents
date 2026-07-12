package server.agents.capabilities.objective;

import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.integration.PrimitiveCapabilityGateway;

import java.util.Map;

public final class ReactorLootObjectiveCapability
        implements AgentExecutableCapability<ReactorLootObjectiveCapability.Command> {
    public record Command(String objectiveId,
                          int mapId,
                          int questId,
                          Integer reactorId,
                          String reactorName,
                          Map<Integer, Integer> requiredItems,
                          Integer completionNpcId) implements AgentCapabilityCommand {
        public Command {
            requiredItems = requiredItems == null ? Map.of() : Map.copyOf(requiredItems);
            if (objectiveId == null || objectiveId.isBlank() || mapId <= 0 || questId <= 0
                    || requiredItems.isEmpty() || completionNpcId != null && completionNpcId <= 0) {
                throw new IllegalArgumentException("reactor-loot objective parameters are required");
            }
        }

        @Override
        public String type() {
            return "reactor-loot-objective";
        }
    }

    private final AmherstObjectiveCapabilitySupport support;

    public ReactorLootObjectiveCapability() {
        support = new AmherstObjectiveCapabilitySupport();
    }

    public ReactorLootObjectiveCapability(PrimitiveCapabilityGateway gateway) {
        support = new AmherstObjectiveCapabilitySupport(gateway);
    }

    @Override
    public String id() {
        return "reactor-loot-objective";
    }

    @Override
    public AgentCapabilityStep tick(AgentCapabilityContext context, Command command) {
        AgentCapabilityStep failure = support.propagateChildFailure(context);
        if (failure != null) {
            return failure;
        }
        boolean itemsReady = command.requiredItems().entrySet().stream().allMatch(item ->
                support.gateway().itemCount(context.agent(), item.getKey()) >= item.getValue());
        if (support.gateway().questStatus(context.agent(), command.questId()) == 2) {
            return AgentCapabilityStep.terminal(AmherstObjectiveCapabilitySupport.success(
                    command.objectiveId(), "reactor-loot quest already completed"));
        }
        int phase = context.memory().intValue("phase", 0);
        if (phase == 0) {
            context.memory().putInt("phase", 1);
            return AgentCapabilityStep.handoff(support.questState(command.questId(), 1),
                    "reactor objective verifies active quest");
        }
        if (phase == 1 && !itemsReady) {
            AgentCapabilityStep approach = support.approachReactor(
                    context, command.mapId(), command.reactorId(), command.reactorName());
            if (approach != null) {
                return approach;
            }
            context.memory().putInt("phase", 2);
            return AgentCapabilityStep.handoff(support.reactor(command.mapId(), command.questId(),
                            command.reactorId(), command.reactorName(), command.requiredItems()),
                    "reactor objective requests normal reactor interaction");
        }
        if ((phase == 1 || phase == 2) && !itemsReady) {
            context.memory().putInt("phase", 3);
            return AgentCapabilityStep.handoff(support.loot(command.requiredItems()),
                    "reactor objective requests normal loot pickup");
        }
        if (phase <= 3) {
            context.memory().putInt("itemIndex", 0);
            context.memory().putInt("phase", 4);
        }
        if (context.memory().intValue("phase", 0) == 4) {
            int itemIndex = context.memory().intValue("itemIndex", 0);
            var items = command.requiredItems().entrySet().stream().toList();
            if (itemIndex < items.size()) {
                var item = items.get(itemIndex);
                context.memory().putInt("itemIndex", itemIndex + 1);
                return AgentCapabilityStep.handoff(support.inspect(item.getKey(), item.getValue()),
                        "reactor objective verifies collected items");
            }
            if (command.completionNpcId() == null) {
                return AgentCapabilityStep.terminal(AmherstObjectiveCapabilitySupport.success(
                        command.objectiveId(), "reactor loot verified"));
            }
            context.memory().putInt("phase", 5);
        }
        if (context.memory().intValue("phase", 0) == 5) {
            AgentCapabilityStep approach = support.approachNpc(
                    context, command.mapId(), command.completionNpcId());
            if (approach != null) {
                return approach;
            }
            context.memory().putInt("phase", 6);
            return AgentCapabilityStep.handoff(support.talk(
                            command.mapId(), command.completionNpcId(), command.questId()),
                    "reactor objective requests completion dialogue");
        }
        if (phase == 6) {
            context.memory().putInt("phase", 7);
            return AgentCapabilityStep.handoff(support.questComplete(
                            command.questId(), command.completionNpcId()),
                    "reactor objective requests normal quest completion");
        }
        if (phase == 7) {
            context.memory().putInt("phase", 8);
            return AgentCapabilityStep.handoff(support.questState(command.questId(), 2),
                    "reactor objective verifies quest completion");
        }
        return AgentCapabilityStep.terminal(AmherstObjectiveCapabilitySupport.success(
                command.objectiveId(), "reactor loot and completion verified"));
    }
}
