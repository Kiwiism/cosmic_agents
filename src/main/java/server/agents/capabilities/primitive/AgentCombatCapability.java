package server.agents.capabilities.primitive;

import server.agents.capabilities.movement.AgentClimbStateRuntime;
import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityResult;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.capabilities.contracts.AgentDisposition;
import server.agents.capabilities.inventory.AgentInventoryReservationRuntime;

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
        if (!command.requiredItemCounts().isEmpty()) {
            AgentInventoryReservationRuntime.reserveObjectiveItems(
                    context.entry(), command.requiredItemCounts(),
                    AgentInventoryReservationRuntime.COMBAT_LOOT_CAPABILITY,
                    AgentDisposition.QUEST_RESERVE, "active combat loot objective", 900, context.nowMs());
        }
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
        Set<Integer> spawnPressureMobIds = spawnPressureMobIds(context, command, allowedMobIds);
        if (!gateway.grounded(context.agent())) {
            if (AgentClimbStateRuntime.climbing(context.entry())) {
                grind(context, allowedMobIds, spawnPressureMobIds);
                return AgentCapabilityStep.running(
                        "continuing combat navigation while climbing", false);
            }
            gateway.stop(context.entry());
            return AgentCapabilityStep.running("waiting to land before combat", false);
        }
        if (gateway.liveMonsterCount(context.agent(), allowedMobIds) == 0) {
            if (!spawnPressureMobIds.isEmpty()
                    && gateway.liveMonsterCount(context.agent(), spawnPressureMobIds) > 0) {
                grind(context, allowedMobIds, spawnPressureMobIds);
                return AgentCapabilityStep.running(
                        "clearing non-required map mobs to free required spawn slots", false);
            }
            grind(context, allowedMobIds, Set.of());
            return AgentCapabilityStep.running(
                    "waiting for a required target mob to become available", false);
        }
        grind(context, allowedMobIds, spawnPressureMobIds);
        return AgentCapabilityStep.running("delegating to reconstructed combat", false);
    }

    private void grind(AgentCapabilityContext context,
                       Set<Integer> requiredMobIds,
                       Set<Integer> spawnPressureMobIds) {
        if (spawnPressureMobIds.isEmpty()) {
            gateway.grind(context.entry(), requiredMobIds);
            return;
        }
        gateway.grind(context.entry(), requiredMobIds, spawnPressureMobIds);
    }

    private Set<Integer> spawnPressureMobIds(AgentCapabilityContext context,
                                             Command command,
                                             Set<Integer> requiredMobIds) {
        Set<Integer> configuredSpawnIds = gateway.configuredMonsterSpawnIds(context.agent());
        if (!configuredSpawnIds.containsAll(requiredMobIds)) {
            return Set.of();
        }
        Set<Integer> fallback = new LinkedHashSet<>(configuredSpawnIds);
        fallback.removeAll(command.requiredKillCounts().keySet());
        return Set.copyOf(fallback);
    }

    @Override
    public void onTerminal(AgentCapabilityContext context, Command command, AgentCapabilityResult result) {
        AgentInventoryReservationRuntime.releaseCapability(
                context.entry(), AgentInventoryReservationRuntime.COMBAT_LOOT_CAPABILITY);
        gateway.stop(context.entry());
    }
}
