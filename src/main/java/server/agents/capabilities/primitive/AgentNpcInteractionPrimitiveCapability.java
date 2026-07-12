package server.agents.capabilities.primitive;

import server.agents.capabilities.npc.AgentNpcInteractionType;
import server.agents.capabilities.npc.AgentNpcInteractionPolicy;
import server.agents.capabilities.quest.AmherstScopePolicy;
import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityResult;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;

import java.awt.Point;

public final class AgentNpcInteractionPrimitiveCapability
        implements AgentExecutableCapability<AgentNpcInteractionPrimitiveCapability.Command> {
    public record Command(int mapId,
                          int npcId,
                          AgentNpcInteractionType interactionType,
                          Integer questId,
                          int maxRangePx,
                          boolean requireAmherstScope) implements AgentCapabilityCommand {
        public Command(int mapId,
                       int npcId,
                       AgentNpcInteractionType interactionType,
                       Integer questId,
                       boolean requireAmherstScope) {
            this(mapId, npcId, interactionType, questId,
                    AgentNpcInteractionPolicy.DEFAULT_CLICK_RANGE_PX, requireAmherstScope);
        }

        public Command {
            interactionType = interactionType == null ? AgentNpcInteractionType.TALK : interactionType;
            if (mapId <= 0 || npcId <= 0 || maxRangePx < 0) {
                throw new IllegalArgumentException("map, NPC, and non-negative range are required");
            }
            if ((interactionType == AgentNpcInteractionType.QUEST_START
                    || interactionType == AgentNpcInteractionType.QUEST_COMPLETE)
                    && (questId == null || questId <= 0)) {
                throw new IllegalArgumentException("quest interaction requires a quest id");
            }
        }

        @Override
        public String type() {
            return "npc-interaction";
        }
    }

    private final PrimitiveCapabilityGateway gateway;
    private final AmherstScopePolicy scopePolicy;

    public AgentNpcInteractionPrimitiveCapability() {
        this(AgentPrimitiveCapabilityGatewayRuntime.gateway(), new AmherstScopePolicy());
    }

    public AgentNpcInteractionPrimitiveCapability(PrimitiveCapabilityGateway gateway,
                                                   AmherstScopePolicy scopePolicy) {
        this.gateway = gateway;
        this.scopePolicy = scopePolicy;
    }

    @Override
    public String id() {
        return "npc-interaction";
    }

    @Override
    public AgentCapabilityStep tick(AgentCapabilityContext context, Command command) {
        if (gateway.mapId(context.agent()) != command.mapId()) {
            return AgentPrimitiveResults.mismatch("agent is not on the NPC map");
        }
        if (command.requireAmherstScope() && command.questId() != null) {
            var scope = scopePolicy.checkQuest(command.questId());
            if (!scope.allowed()) {
                return AgentPrimitiveResults.blocked(scope.status(), scope.reason());
            }
        }
        Point npcPosition = gateway.npcPosition(context.agent(), command.npcId());
        if (npcPosition == null) {
            return AgentPrimitiveResults.missing("NPC is not present on the map");
        }
        if (gateway.position(context.agent()).distanceSq(npcPosition)
                > (long) command.maxRangePx() * command.maxRangePx()) {
            return AgentPrimitiveResults.missing("agent is outside NPC interaction range");
        }
        gateway.facePosition(context.agent(), npcPosition);
        if (!gateway.interactNpc(context.agent(), command.npcId(), command.interactionType(), command.questId())) {
            return AgentCapabilityStep.retry("NPC interaction was not accepted");
        }
        if (command.interactionType() == AgentNpcInteractionType.QUEST_START
                && (command.questId() == null
                || gateway.questStatus(context.agent(), command.questId()) < 1)) {
            return AgentPrimitiveResults.mismatch("NPC quest-start state was not observed");
        }
        if (command.interactionType() == AgentNpcInteractionType.QUEST_COMPLETE
                && (command.questId() == null || gateway.questStatus(context.agent(), command.questId()) != 2)) {
            return AgentPrimitiveResults.mismatch("NPC quest-complete state was not observed");
        }
        return AgentCapabilityStep.terminal(AgentCapabilityResult.success("NPC interaction accepted"));
    }
}
