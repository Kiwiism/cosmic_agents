package server.agents.capabilities.objective;

import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityResult;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.capabilities.quest.AmherstScopePolicy;

import java.util.List;

public final class NpcQuestObjectiveCapability
        implements AgentExecutableCapability<NpcQuestObjectiveCapability.Command> {
    public record QuestOperation(int questId, int startNpcId, int completeNpcId, int desiredStatus) {
        public QuestOperation {
            if (questId <= 0 || startNpcId <= 0 || completeNpcId <= 0
                    || desiredStatus < 1 || desiredStatus > 2) {
                throw new IllegalArgumentException("quest operation is invalid");
            }
        }
    }

    public record Command(String objectiveId,
                          int mapId,
                          List<QuestOperation> operations,
                          boolean skipUnavailable) implements AgentCapabilityCommand {
        public Command {
            operations = operations == null ? List.of() : List.copyOf(operations);
            if (objectiveId == null || objectiveId.isBlank() || mapId <= 0 || operations.isEmpty()) {
                throw new IllegalArgumentException("objective id, map, and quest operations are required");
            }
        }

        @Override
        public String type() {
            return "npc-quest-objective";
        }
    }

    private final AmherstObjectiveCapabilitySupport support;

    public NpcQuestObjectiveCapability() {
        this.support = new AmherstObjectiveCapabilitySupport();
    }

    public NpcQuestObjectiveCapability(PrimitiveCapabilityGateway gateway) {
        this.support = new AmherstObjectiveCapabilitySupport(gateway);
    }

    public NpcQuestObjectiveCapability(PrimitiveCapabilityGateway gateway,
                                       AmherstNpcInteractionDelay npcInteractionDelay) {
        this(gateway, new AmherstScopePolicy(), npcInteractionDelay);
    }

    public NpcQuestObjectiveCapability(PrimitiveCapabilityGateway gateway,
                                       AmherstScopePolicy scopePolicy,
                                       AmherstNpcInteractionDelay npcInteractionDelay) {
        this.support = new AmherstObjectiveCapabilitySupport(gateway, scopePolicy, npcInteractionDelay);
    }

    @Override
    public String id() {
        return "npc-quest-objective";
    }

    @Override
    public AgentCapabilityStep tick(AgentCapabilityContext context, Command command) {
        AgentCapabilityStep failure = support.propagateChildFailure(context);
        if (failure != null) {
            return failure;
        }
        int operationIndex = context.memory().intValue("operation", 0);
        if (operationIndex >= command.operations().size()) {
            return AgentCapabilityStep.terminal(AmherstObjectiveCapabilitySupport.success(
                    command.objectiveId(), "NPC quest objective verified"));
        }

        QuestOperation operation = command.operations().get(operationIndex);
        int liveStatus = support.gateway().questStatus(context.agent(), operation.questId());
        if (liveStatus >= operation.desiredStatus()) {
            context.memory().putInt("operation", operationIndex + 1);
            context.memory().putInt("phase", 0);
            return AgentCapabilityStep.running("quest operation already satisfied", false);
        }
        int phase = context.memory().intValue("phase", 0);
        if (phase == 0) {
            boolean completing = operation.desiredStatus() == 2 && liveStatus == 1;
            int npcId = completing ? operation.completeNpcId() : operation.startNpcId();
            int targetStatus = completing ? 2 : 1;
            if (!completing && command.skipUnavailable()
                    && !support.gateway().canStartQuest(context.agent(), operation.questId(), npcId)) {
                context.memory().putInt("operation", operationIndex + 1);
                return AgentCapabilityStep.running("optional unavailable quest skipped", false);
            }
            context.memory().putInt("npcId", npcId);
            context.memory().putInt("targetStatus", targetStatus);
            AgentCapabilityStep approach = support.approachNpc(context, command.mapId(), npcId);
            if (approach != null) {
                return approach;
            }
            if (support.waitForNpcInteraction(context, operationIndex)) {
                return AgentCapabilityStep.running("waiting briefly before NPC interaction", true);
            }
            context.memory().putInt("phase", 1);
            return AgentCapabilityStep.handoff(support.talk(command.mapId(), npcId, operation.questId()),
                    "NPC quest objective requests dialogue");
        }
        int npcId = context.memory().intValue("npcId", operation.startNpcId());
        int targetStatus = context.memory().intValue("targetStatus", 1);
        if (phase == 1) {
            context.memory().putInt("phase", 2);
            return AgentCapabilityStep.handoff(targetStatus == 2
                            ? support.questComplete(operation.questId(), npcId)
                            : support.questStart(operation.questId(), npcId),
                    "NPC quest objective requests normal quest transition");
        }
        if (phase == 2) {
            context.memory().putInt("phase", 3);
            return AgentCapabilityStep.handoff(support.questState(operation.questId(), targetStatus),
                    "NPC quest objective requests live quest verification");
        }
        context.memory().putInt("phase", 0);
        if (targetStatus == operation.desiredStatus()) {
            context.memory().putInt("operation", operationIndex + 1);
        }
        return AgentCapabilityStep.running("NPC quest operation advanced", false);
    }
}
