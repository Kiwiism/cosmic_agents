package server.agents.capabilities.primitive;

import server.agents.capabilities.reactor.AgentReactorInteractionMode;
import server.agents.capabilities.reactor.AgentReactorInteractionRequest;
import server.agents.capabilities.reactor.AgentReactorTargetSelector;
import server.agents.capabilities.reactor.AgentReactorTargetReservationRuntime;
import server.agents.capabilities.reactor.AgentReactorScopePolicy;
import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityResult;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;

import java.util.List;
import java.util.Map;

public final class AgentReactorPrimitiveCapability
        implements AgentExecutableCapability<AgentReactorPrimitiveCapability.Command> {
    static final int HIT_INTERVAL_MS = 650;
    static final int TARGET_RECHECK_MIN_MS = 750;
    static final int TARGET_RECHECK_JITTER_MS = 750;

    public record Command(int mapId,
                          int questId,
                          Integer reactorId,
                          String reactorName,
                          int maxRangePx,
                          Map<Integer, Integer> expectedItemCounts) implements AgentCapabilityCommand {
        public Command {
            expectedItemCounts = expectedItemCounts == null ? Map.of() : Map.copyOf(expectedItemCounts);
            if (mapId <= 0 || questId <= 0 || maxRangePx < 0
                    || expectedItemCounts.isEmpty()
                    || expectedItemCounts.entrySet().stream()
                    .anyMatch(entry -> entry.getKey() <= 0 || entry.getValue() <= 0)) {
                throw new IllegalArgumentException(
                        "map, quest, reactor selector, range, and expected item counts are required");
            }
        }

        @Override
        public String type() {
            return "reactor-interaction";
        }
    }

    private final PrimitiveCapabilityGateway gateway;
    private final AgentReactorScopePolicy scopePolicy;
    private final AgentReactorTargetSelector selector;

    public AgentReactorPrimitiveCapability() {
        this(AgentPrimitiveCapabilityGatewayRuntime.gateway(),
                new AgentReactorScopePolicy(), new AgentReactorTargetSelector());
    }

    public AgentReactorPrimitiveCapability(PrimitiveCapabilityGateway gateway,
                                           AgentReactorScopePolicy scopePolicy,
                                           AgentReactorTargetSelector selector) {
        this.gateway = gateway;
        this.scopePolicy = scopePolicy;
        this.selector = selector;
    }

    @Override
    public String id() {
        return "reactor-interaction";
    }

    @Override
    public AgentCapabilityStep tick(AgentCapabilityContext context, Command command) {
        if (gateway.mapId(context.agent()) != command.mapId()) {
            return AgentPrimitiveResults.mismatch("agent is not on the reactor map");
        }
        boolean itemsComplete = !command.expectedItemCounts().isEmpty()
                && command.expectedItemCounts().entrySet().stream().allMatch(entry ->
                gateway.itemCount(context.agent(), entry.getKey()) >= entry.getValue());
        if (itemsComplete) {
            AgentReactorTargetReservationRuntime.release(context.agent().getId());
            return AgentCapabilityStep.terminal(AgentCapabilityResult.success("reactor item result verified"));
        }
        AgentReactorInteractionRequest request = new AgentReactorInteractionRequest(
                command.mapId(), command.questId(), AgentReactorInteractionMode.HIT,
                command.reactorId(), command.reactorName(), null,
                gateway.position(context.agent()), command.maxRangePx());
        var scope = scopePolicy.check(request);
        if (!scope.allowed()) {
            return AgentPrimitiveResults.blocked(scope.status(), scope.reason());
        }
        int nextSearchAtMs = context.memory().intValue("nextSearchAtMs", 0);
        if (context.elapsedMs() < nextSearchAtMs) {
            if (gateway.lootNearby(context.agent(), command.expectedItemCounts().keySet())) {
                return AgentCapabilityStep.running(
                        "reactor drops found; verifying normal pickup result", false);
            }
            return AgentCapabilityStep.running("waiting before the next reactor availability check", false);
        }
        var target = selector.selectReserved(List.copyOf(gateway.reactors(context.agent())), request,
                context.agent().getId(), context.agent().getMap());
        if (target.isEmpty()) {
            if (gateway.lootNearby(context.agent(), command.expectedItemCounts().keySet())) {
                return AgentCapabilityStep.running("reactor drops found; verifying normal pickup result", false);
            }
            int jitter = Math.floorMod(context.agent().getId() * 31, TARGET_RECHECK_JITTER_MS + 1);
            context.memory().putInt("nextSearchAtMs", (int) Math.min(Integer.MAX_VALUE,
                    context.elapsedMs() + TARGET_RECHECK_MIN_MS + jitter));
            return AgentCapabilityStep.running(
                    "waiting for an unreserved active reactor or an existing quest drop", false);
        }
        context.memory().putInt("nextSearchAtMs", 0);
        int nextHitAtMs = context.memory().intValue("nextHitAtMs", 0);
        if (context.elapsedMs() < nextHitAtMs) {
            return AgentCapabilityStep.running("waiting for reactor hit cooldown", false);
        }
        if (!gateway.hitReactor(context.agent(), target.get().objectId())) {
            return AgentCapabilityStep.retry("reactor hit was not accepted");
        }
        context.memory().putInt("nextHitAtMs", (int) Math.min(Integer.MAX_VALUE,
                context.elapsedMs() + HIT_INTERVAL_MS));
        return AgentCapabilityStep.running("reactor hit; verifying resulting live state");
    }

    @Override
    public void onTerminal(AgentCapabilityContext context, Command command, AgentCapabilityResult result) {
        AgentReactorTargetReservationRuntime.release(context.agent().getId());
    }
}
