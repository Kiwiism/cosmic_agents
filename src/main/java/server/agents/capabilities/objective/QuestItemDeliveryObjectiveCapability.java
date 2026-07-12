package server.agents.capabilities.objective;

import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.integration.PrimitiveCapabilityGateway;

import java.util.Map;

public final class QuestItemDeliveryObjectiveCapability
        implements AgentExecutableCapability<QuestItemDeliveryObjectiveCapability.Command> {
    public record Command(String objectiveId,
                          int mapId,
                          int questId,
                          int npcId,
                          Map<Integer, Integer> requiredItems) implements AgentCapabilityCommand {
        public Command {
            requiredItems = requiredItems == null ? Map.of() : Map.copyOf(requiredItems);
            if (objectiveId == null || objectiveId.isBlank() || mapId <= 0 || questId <= 0 || npcId <= 0
                    || requiredItems.isEmpty()) {
                throw new IllegalArgumentException("quest-item delivery parameters are required");
            }
        }

        @Override
        public String type() {
            return "quest-item-delivery-objective";
        }
    }

    private final AmherstObjectiveCapabilitySupport support;

    public QuestItemDeliveryObjectiveCapability() {
        support = new AmherstObjectiveCapabilitySupport();
    }

    public QuestItemDeliveryObjectiveCapability(PrimitiveCapabilityGateway gateway) {
        support = new AmherstObjectiveCapabilitySupport(gateway);
    }

    @Override
    public String id() {
        return "quest-item-delivery-objective";
    }

    @Override
    public AgentCapabilityStep tick(AgentCapabilityContext context, Command command) {
        AgentCapabilityStep failure = support.propagateChildFailure(context);
        if (failure != null) {
            return failure;
        }
        if (support.gateway().questStatus(context.agent(), command.questId()) == 2) {
            return AgentCapabilityStep.terminal(AmherstObjectiveCapabilitySupport.success(
                    command.objectiveId(), "quest-item delivery already completed"));
        }
        int phase = context.memory().intValue("phase", 0);
        if (phase == 0) {
            context.memory().putInt("phase", 1);
            return AgentCapabilityStep.handoff(support.questState(command.questId(), 1),
                    "delivery objective verifies active quest");
        }
        if (phase == 1) {
            AgentCapabilityStep approach = support.approachNpc(context, command.mapId(), command.npcId());
            if (approach != null) {
                return approach;
            }
            context.memory().putInt("itemIndex", 0);
            context.memory().putInt("phase", 2);
        }
        if (context.memory().intValue("phase", 0) == 2) {
            int itemIndex = context.memory().intValue("itemIndex", 0);
            var items = command.requiredItems().entrySet().stream().toList();
            if (itemIndex < items.size()) {
                var item = items.get(itemIndex);
                context.memory().putInt("item-" + item.getKey(),
                        support.gateway().itemCount(context.agent(), item.getKey()));
                context.memory().putInt("itemIndex", itemIndex + 1);
                return AgentCapabilityStep.handoff(support.inspect(item.getKey(), item.getValue()),
                        "delivery objective verifies turn-in inventory");
            }
            context.memory().putInt("phase", 3);
            return AgentCapabilityStep.handoff(support.talk(command.mapId(), command.npcId(), command.questId()),
                    "delivery objective requests NPC dialogue");
        }
        if (phase == 3) {
            context.memory().putInt("phase", 4);
            return AgentCapabilityStep.handoff(support.questComplete(command.questId(), command.npcId()),
                    "delivery objective requests normal quest completion");
        }
        if (phase == 4) {
            context.memory().putInt("phase", 5);
            return AgentCapabilityStep.handoff(support.questState(command.questId(), 2),
                    "delivery objective verifies completion");
        }
        if (command.requiredItems().entrySet().stream().anyMatch(item -> {
            int initial = context.memory().intValue("item-" + item.getKey(), item.getValue());
            return support.gateway().itemCount(context.agent(), item.getKey()) > initial - item.getValue();
        })) {
            return AmherstObjectiveCapabilitySupport.missing("quest completed but turn-in inventory was not consumed");
        }
        return AgentCapabilityStep.terminal(AmherstObjectiveCapabilitySupport.success(
                command.objectiveId(), "quest-item delivery verified"));
    }
}
