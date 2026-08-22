package server.agents.runtime.activity.control;

import client.Character;
import server.agents.integration.cosmic.CosmicAgentWorldContextFactory;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentTickFailureStateRuntime;
import server.agents.runtime.activity.AgentActivityOwnershipState;
import server.agents.runtime.activity.control.binding.AgentStandardWorldActivityBindingResolver;
import server.agents.runtime.activity.control.binding.AgentWorldDirectiveRequestCompiler;
import server.agents.runtime.activity.control.facade.AgentLiveActivityFacade;
import server.agents.runtime.activity.control.facade.AgentLiveActivityFacadeRegistry;
import server.agents.runtime.activity.control.facade.AgentStandardLiveActivityFacades;
import server.agents.runtime.activity.control.rollout.AgentWorldDirectorAssistedGate;
import server.agents.runtime.activity.control.rollout.AgentWorldDirectorAutonomousGate;
import server.agents.runtime.activity.control.rollout.AgentWorldDirectorRolloutConfigLoader;
import server.agents.runtime.activity.control.rollout.AgentWorldDirectorRolloutGateResult;
import server.agents.runtime.activity.session.AgentActivityHandoffCoordinator;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.session.AgentFileActivityHandoffStore;
import server.agents.runtime.activity.session.AgentPersistentActivityHandoffCoordinator;
import server.agents.runtime.activity.world.AgentFileWorldDirectiveInbox;
import server.agents.runtime.activity.world.AgentFileWorldDirectorSessionStore;
import server.agents.runtime.activity.world.AgentWorldContext;
import server.agents.runtime.activity.world.AgentWorldDirectiveSource;
import server.agents.runtime.activity.world.AgentWorldDirectorMode;

/** Scheduler bridge for durable manual directives and separately gated policy directives. */
public final class AgentWorldDirectorExecutionRuntime {
    private static final long POLL_INTERVAL_MS = config.AgentTuning.longValue(
            "server.agents.runtime.activity.control.AgentWorldDirectorExecutionRuntime.POLL_INTERVAL_MS");
    private static final long HANDOFF_TIMEOUT_MS = config.AgentTuning.longValue(
            "server.agents.runtime.activity.control.AgentWorldDirectorExecutionRuntime.HANDOFF_TIMEOUT_MS");
    private static final AgentFileActivityHandoffStore HANDOFF_STORE =
            AgentFileActivityHandoffStore.runtimeDefault();
    private static final AgentLiveActivityFacadeRegistry FACADES =
            AgentStandardLiveActivityFacades.registry();
    private static final AgentWorldDirectiveProcessor PROCESSOR = new AgentWorldDirectiveProcessor(
            AgentFileWorldDirectorSessionStore.runtimeDefault(),
            AgentFileWorldDirectiveInbox.runtimeDefault(),
            new AgentPersistentActivityHandoffCoordinator(HANDOFF_STORE),
            new AgentStandardWorldActivityBindingResolver(
                    new AgentWorldDirectiveRequestCompiler(), FACADES),
            AgentWorldDirectorExecutionRuntime::gate, HANDOFF_TIMEOUT_MS);

    private AgentWorldDirectorExecutionRuntime() { }

    public static void tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null || agent == null) return;
        AgentWorldDirectorMode mode = entry.capabilityStates()
                .require(AgentWorldDirectorRuntimeState.STATE_KEY).snapshot().mode();
        if (!mode.acceptsOperatorDirectives() && !mode.allowsAutomaticProposals()) return;
        AgentWorldDirectivePollState poll = entry.capabilityStates()
                .require(AgentWorldDirectivePollState.STATE_KEY);
        if (!poll.claim(nowMs, POLL_INTERVAL_MS)) return;
        try {
            AgentWorldContext context = CosmicAgentWorldContextFactory.capture(entry, agent, nowMs);
            AgentWorldDirectiveProcessor.Result result = PROCESSOR.tick(entry, agent,
                    context.currentActivityKind(), context.currentSessionId(), nowMs);
            poll.result(result.status() + ": " + result.reason());
        } catch (RuntimeException failure) {
            poll.result("FAILED: " + failure.getMessage());
        }
    }

    private static AgentWorldDirectorRolloutGateResult gate(
            server.agents.runtime.activity.world.AgentWorldDirectorSession session,
            server.agents.runtime.activity.world.AgentWorldDirective directive,
            AgentRuntimeEntry entry,
            Character agent,
            long nowMs) {
        AgentWorldContext context = CosmicAgentWorldContextFactory.capture(entry, agent, nowMs);
        AgentActivityKind sourceKind = context.currentActivityKind() == null
                ? directive.targetActivityKind() : context.currentActivityKind();
        AgentLiveActivityFacade source = FACADES.bind(sourceKind, entry, agent);
        var ownership = entry.capabilityStates()
                .require(AgentActivityOwnershipState.STATE_KEY).snapshot();
        if (directive.source() == AgentWorldDirectiveSource.OPERATOR
                && session.mode().acceptsOperatorDirectives()) {
            if (!ownership.permitsExecution()) {
                return AgentWorldDirectorRolloutGateResult.block(
                        "restored activity ownership is not clean");
            }
            return AgentWorldDirectorRolloutGateResult.allow(
                    "explicit operator directive and clean ownership");
        }
        int activeHandoffs = (int) HANDOFF_STORE.list().stream()
                .filter(handoff -> !handoff.terminal()).count();
        if (session.mode() == AgentWorldDirectorMode.ASSISTED) {
            return new AgentWorldDirectorAssistedGate(
                    AgentWorldDirectorRolloutConfigLoader.assisted()).inspect(
                    session.mode(), directive, source, ownership, activeHandoffs);
        }
        if (session.mode() == AgentWorldDirectorMode.AUTONOMOUS) {
            long samples = entry.capabilityStates()
                    .require(AgentWorldDirectorObserveState.STATE_KEY).snapshot().sampleCount();
            return new AgentWorldDirectorAutonomousGate(
                    AgentWorldDirectorRolloutConfigLoader.autonomous()).inspect(
                    session.mode(), directive, source, ownership, samples,
                    AgentTickFailureStateRuntime.failureCount(entry), activeHandoffs);
        }
        return AgentWorldDirectorRolloutGateResult.block(
                "Director mode does not authorize this directive source");
    }
}
