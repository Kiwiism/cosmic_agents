package server.agents.capabilities.objective;

import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.capabilities.quest.AmherstScopePolicy;
import server.agents.capabilities.navigation.AgentPortalRoutePolicy;

import java.util.Map;

public final class CombatQuestObjectiveCapability
        implements AgentExecutableCapability<CombatQuestObjectiveCapability.Command> {
    public record Command(String objectiveId,
                          int mapId,
                          int questId,
                          Map<Integer, Integer> requiredKills,
                          Map<Integer, Integer> requiredLoot) implements AgentCapabilityCommand {
        public Command(String objectiveId,
                       int mapId,
                       int questId,
                       Map<Integer, Integer> requiredKills) {
            this(objectiveId, mapId, questId, requiredKills, Map.of());
        }

        public Command {
            requiredKills = requiredKills == null ? Map.of() : Map.copyOf(requiredKills);
            requiredLoot = requiredLoot == null ? Map.of() : Map.copyOf(requiredLoot);
            if (objectiveId == null || objectiveId.isBlank() || mapId <= 0 || questId <= 0
                    || requiredKills.isEmpty()
                    || requiredLoot.entrySet().stream().anyMatch(entry ->
                    entry.getKey() <= 0 || entry.getValue() <= 0)) {
                throw new IllegalArgumentException("combat objective parameters are required");
            }
        }

        @Override
        public String type() {
            return "combat-quest-objective";
        }
    }

    private final AmherstObjectiveCapabilitySupport support;

    public CombatQuestObjectiveCapability() {
        support = new AmherstObjectiveCapabilitySupport();
    }

    public CombatQuestObjectiveCapability(PrimitiveCapabilityGateway gateway) {
        support = new AmherstObjectiveCapabilitySupport(gateway);
    }

    public CombatQuestObjectiveCapability(PrimitiveCapabilityGateway gateway,
                                          AmherstScopePolicy scopePolicy) {
        support = new AmherstObjectiveCapabilitySupport(gateway, scopePolicy);
    }

    public CombatQuestObjectiveCapability(PrimitiveCapabilityGateway gateway,
                                          AmherstScopePolicy scopePolicy,
                                          AgentPortalRoutePolicy routePolicy) {
        support = new AmherstObjectiveCapabilitySupport(
                gateway, scopePolicy, AmherstNpcInteractionDelay.NONE, routePolicy);
    }

    @Override
    public String id() {
        return "combat-quest-objective";
    }

    @Override
    public AgentCapabilityStep tick(AgentCapabilityContext context, Command command) {
        AgentCapabilityStep failure = support.propagateChildFailure(context);
        if (failure != null) {
            return failure;
        }
        if (support.gateway().questStatus(context.agent(), command.questId()) == 2) {
            return AgentCapabilityStep.terminal(AmherstObjectiveCapabilitySupport.success(
                    command.objectiveId(), "combat quest already completed"));
        }
        int phase = context.memory().intValue("phase", 0);
        if (phase == 0) {
            context.memory().putInt("phase", 1);
            return AgentCapabilityStep.handoff(support.questState(command.questId(), 1),
                    "combat objective verifies active quest");
        }
        if (phase == 1) {
            AgentCapabilityStep travel = support.travel(context, command.mapId());
            if (travel != null) {
                return travel;
            }
            context.memory().putInt("phase", 2);
            return AgentCapabilityStep.handoff(support.combat(
                            command.questId(), command.requiredKills(), command.requiredLoot()),
                    "combat objective delegates required kills");
        }
        if (phase == 2) {
            context.memory().putInt("phase", 3);
            return AgentCapabilityStep.handoff(support.questState(command.questId(), 1),
                    "combat objective verifies quest remains active");
        }
        if (phase == 3 && !command.requiredLoot().isEmpty()) {
            context.memory().putInt("phase", 4);
            return AgentCapabilityStep.handoff(support.loot(command.requiredLoot()),
                    "combat objective collects required quest loot");
        }
        return AgentCapabilityStep.terminal(AmherstObjectiveCapabilitySupport.success(
                command.objectiveId(), command.requiredLoot().isEmpty()
                        ? "combat quest progress verified"
                        : "combat quest progress and loot verified"));
    }
}
