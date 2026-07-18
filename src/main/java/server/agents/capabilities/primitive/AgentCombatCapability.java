package server.agents.capabilities.primitive;

import server.agents.capabilities.movement.AgentClimbStateRuntime;
import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityResult;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class AgentCombatCapability
        implements AgentExecutableCapability<AgentCombatCapability.Command> {
    public record Command(int questId,
                          Map<Integer, Integer> requiredKillCounts,
                          Map<Integer, Integer> requiredItemCounts)
            implements AgentCapabilityCommand {
        public Command(int questId, Map<Integer, Integer> requiredKillCounts) {
            this(questId, requiredKillCounts, Map.of());
        }

        public Command {
            requiredKillCounts = requiredKillCounts == null ? Map.of() : Map.copyOf(requiredKillCounts);
            requiredItemCounts = requiredItemCounts == null ? Map.of() : Map.copyOf(requiredItemCounts);
            if (questId <= 0 || requiredKillCounts.isEmpty()
                    || requiredKillCounts.entrySet().stream().anyMatch(entry ->
                    entry.getKey() <= 0 || entry.getValue() <= 0)
                    || requiredItemCounts.entrySet().stream().anyMatch(entry ->
                    entry.getKey() <= 0 || entry.getValue() <= 0)) {
                throw new IllegalArgumentException("quest id and positive mob kill requirements are required");
            }
        }

        @Override
        public String type() {
            return "combat";
        }
    }

    private final PrimitiveCapabilityGateway gateway;

    public AgentCombatCapability() {
        this(AgentPrimitiveCapabilityGatewayRuntime.gateway());
    }

    public AgentCombatCapability(PrimitiveCapabilityGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public String id() {
        return "combat";
    }

    @Override
    public AgentCapabilityStep tick(AgentCapabilityContext context, Command command) {
        if (!gateway.alive(context.agent())) {
            gateway.stop(context.entry());
            return AgentPrimitiveResults.missing("agent is dead and cannot continue combat");
        }
        if (!command.requiredItemCounts().isEmpty()) {
            gateway.lootNearby(context.agent(), command.requiredItemCounts().keySet());
        }
        Set<Integer> pendingMobIds = new LinkedHashSet<>();
        command.requiredKillCounts().forEach((mobId, requiredCount) -> {
            if (gateway.questProgress(context.agent(), command.questId(), mobId) < requiredCount) {
                pendingMobIds.add(mobId);
            }
        });
        boolean killsComplete = pendingMobIds.isEmpty();
        boolean lootComplete = command.requiredItemCounts().entrySet().stream().allMatch(entry ->
                gateway.itemCount(context.agent(), entry.getKey()) >= entry.getValue());
        boolean complete = killsComplete && lootComplete;
        if (complete) {
            gateway.stop(context.entry());
            return AgentCapabilityStep.terminal(AgentCapabilityResult.success("combat kill requirements verified"));
        }
        Set<Integer> allowedMobIds = pendingMobIds.isEmpty()
                ? command.requiredKillCounts().keySet() : Set.copyOf(pendingMobIds);
        if (!gateway.grounded(context.agent())) {
            if (AgentClimbStateRuntime.climbing(context.entry())) {
                gateway.grind(context.entry(), allowedMobIds);
                return AgentCapabilityStep.running(
                        "continuing combat navigation while climbing", false);
            }
            gateway.stop(context.entry());
            return AgentCapabilityStep.running("waiting to land before combat", false);
        }
        if (gateway.liveMonsterCount(context.agent(), allowedMobIds) == 0) {
            Set<Integer> configuredSpawnIds = gateway.configuredMonsterSpawnIds(context.agent());
            if (configuredSpawnIds.containsAll(allowedMobIds)) {
                Set<Integer> spawnPressureMobIds = new LinkedHashSet<>(configuredSpawnIds);
                spawnPressureMobIds.removeAll(command.requiredKillCounts().keySet());
                if (!spawnPressureMobIds.isEmpty()
                        && gateway.liveMonsterCount(context.agent(), spawnPressureMobIds) > 0) {
                    gateway.grind(context.entry(), spawnPressureMobIds);
                    return AgentCapabilityStep.running(
                            "clearing non-required map mobs to free required spawn slots", false);
                }
            }
            gateway.grind(context.entry(), allowedMobIds);
            return AgentCapabilityStep.running(
                    "waiting for a required target mob to become available", false);
        }
        gateway.grind(context.entry(), allowedMobIds);
        return AgentCapabilityStep.running("delegating to reconstructed combat", false);
    }

    @Override
    public void onTerminal(AgentCapabilityContext context, Command command, AgentCapabilityResult result) {
        gateway.stop(context.entry());
    }
}
