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
import server.agents.runtime.activity.session.AgentActivityHandoffJourneyRecorder;
import server.agents.runtime.activity.session.AgentActivityTerminalJourneyRecorder;
import server.agents.runtime.activity.session.AgentPersistentActivityHandoffCoordinator;
import server.agents.runtime.activity.world.AgentFileWorldDirectiveInbox;
import server.agents.runtime.activity.world.AgentFileWorldDirectorSessionStore;
import server.agents.runtime.activity.world.AgentWorldContext;
import server.agents.runtime.activity.world.AgentWorldDirectiveSource;
import server.agents.runtime.activity.world.AgentWorldDirectorMode;
import server.agents.runtime.activity.world.AgentWorldDirectiveEnvelope;
import server.agents.runtime.journey.AgentFileJourneyJournalStore;
import server.agents.runtime.activity.outcome.AgentFileActivityOutcomeInbox;
import server.agents.runtime.activity.outcome.AgentActivityOutcomeEnvelope;

/** Scheduler bridge for durable manual directives and separately gated policy directives. */
public final class AgentWorldDirectorExecutionRuntime {
    private static final long POLL_INTERVAL_MS = config.AgentTuning.longValue(
            "server.agents.runtime.activity.control.AgentWorldDirectorExecutionRuntime.POLL_INTERVAL_MS");
    private static final long HANDOFF_TIMEOUT_MS = config.AgentTuning.longValue(
            "server.agents.runtime.activity.control.AgentWorldDirectorExecutionRuntime.HANDOFF_TIMEOUT_MS");
    private static final AgentFileActivityHandoffStore HANDOFF_STORE =
            AgentFileActivityHandoffStore.runtimeDefault();
    private static final AgentFileJourneyJournalStore JOURNEY_STORE =
            new AgentFileJourneyJournalStore();
    private static final AgentLiveActivityFacadeRegistry FACADES =
            AgentStandardLiveActivityFacades.registry();
    private static final AgentFileWorldDirectiveInbox DIRECTIVES =
            AgentFileWorldDirectiveInbox.runtimeDefault();
    private static final AgentWorldDirectiveJourneyRecorder JOURNEY =
            new AgentWorldDirectiveJourneyRecorder(JOURNEY_STORE);
    private static final AgentFileActivityOutcomeInbox OUTCOMES =
            AgentFileActivityOutcomeInbox.runtimeDefault();
    private static final AgentActivityTerminalJourneyRecorder TERMINALS =
            new AgentActivityTerminalJourneyRecorder(JOURNEY_STORE);
    private static final java.util.Set<String> PUBLISHED_TERMINAL_IDS =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final AgentWorldDirectiveProcessor PROCESSOR = new AgentWorldDirectiveProcessor(
            AgentFileWorldDirectorSessionStore.runtimeDefault(),
            DIRECTIVES,
            new AgentPersistentActivityHandoffCoordinator(HANDOFF_STORE,
                    new AgentActivityHandoffJourneyRecorder(JOURNEY_STORE)),
            new AgentStandardWorldActivityBindingResolver(
                    new AgentWorldDirectiveRequestCompiler(), FACADES),
            AgentWorldDirectorExecutionRuntime::gate,
            new AgentStandardWorldActivityLifecycleHandler(FACADES),
            HANDOFF_TIMEOUT_MS);

    private AgentWorldDirectorExecutionRuntime() { }

    public static void tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null || agent == null) return;
        AgentWorldDirectorMode mode = entry.capabilityStates()
                .require(AgentWorldDirectorRuntimeState.STATE_KEY).snapshot().mode();
        boolean emergencyHold = mode == AgentWorldDirectorMode.EMERGENCY_HOLD;
        if (!mode.acceptsOperatorDirectives() && !mode.allowsAutomaticProposals()
                && !emergencyHold) return;
        AgentWorldDirectivePollState poll = entry.capabilityStates()
                .require(AgentWorldDirectivePollState.STATE_KEY);
        if (!poll.claim(nowMs, POLL_INTERVAL_MS)) return;
        if (emergencyHold && DIRECTIVES.list(agent.getId()).stream().noneMatch(envelope ->
                !envelope.status().terminal()
                        && envelope.directive().type()
                        == server.agents.runtime.activity.world.AgentWorldDirectiveType.RESUME)) {
            return;
        }
        try {
            AgentWorldContext context = CosmicAgentWorldContextFactory.capture(entry, agent, nowMs);
            AgentWorldDirectiveProcessor.Result result = PROCESSOR.tick(entry, agent,
                    context.currentActivityKind(), context.currentSessionId(), nowMs);
            poll.result(result.status() + ": " + result.reason());
            if (!result.directiveId().isEmpty()
                    && (result.status() == AgentWorldDirectiveProcessor.Result.Status.COMPLETED
                    || result.status() == AgentWorldDirectiveProcessor.Result.Status.REJECTED)) {
                AgentWorldDirectiveEnvelope envelope = DIRECTIVES.load(
                        agent.getId(), result.directiveId()).orElse(null);
                if (envelope != null && envelope.status().terminal()) {
                    JOURNEY.resolved(envelope, nowMs);
                }
            }
            publishTerminalOutcomes(entry, agent, nowMs);
        } catch (RuntimeException failure) {
            poll.result("FAILED: " + failure.getMessage());
        }
    }

    private static void publishTerminalOutcomes(
            AgentRuntimeEntry entry, Character agent, long nowMs) {
        for (AgentActivityKind kind : AgentActivityKind.values()) {
            var outcome = FACADES.bind(kind, entry, agent).outcome().terminalOutcome(nowMs);
            if (outcome == null) continue;
            String outcomeId = outcome.kind().name().toLowerCase() + ':'
                    + outcome.agentId() + ':' + outcome.sessionId() + ':'
                    + outcome.phase().name().toLowerCase();
            if (!PUBLISHED_TERMINAL_IDS.add(outcomeId)) continue;
            try {
                AgentActivityOutcomeEnvelope envelope = OUTCOMES.publish(
                        outcomeId, outcome, nowMs);
                TERMINALS.record(envelope);
            } catch (RuntimeException failure) {
                PUBLISHED_TERMINAL_IDS.remove(outcomeId);
                throw failure;
            }
        }
    }

    private static AgentWorldDirectorRolloutGateResult gate(
            server.agents.runtime.activity.world.AgentWorldDirectorSession session,
            server.agents.runtime.activity.world.AgentWorldDirective directive,
            AgentRuntimeEntry entry,
            Character agent,
            long nowMs) {
        AgentWorldContext context = CosmicAgentWorldContextFactory.capture(entry, agent, nowMs);
        var ownership = entry.capabilityStates()
                .require(AgentActivityOwnershipState.STATE_KEY).snapshot();
        if (directive.source() == AgentWorldDirectiveSource.OPERATOR
                && session.mode() == AgentWorldDirectorMode.EMERGENCY_HOLD
                && directive.type()
                == server.agents.runtime.activity.world.AgentWorldDirectiveType.RESUME) {
            return ownership.permitsExecution()
                    ? AgentWorldDirectorRolloutGateResult.allow(
                    "explicit operator release from Emergency Hold")
                    : AgentWorldDirectorRolloutGateResult.block(
                    "restored activity ownership is not clean");
        }
        if (directive.source() == AgentWorldDirectiveSource.OPERATOR
                && session.mode().acceptsOperatorDirectives()) {
            if (!ownership.permitsExecution()) {
                return AgentWorldDirectorRolloutGateResult.block(
                        "restored activity ownership is not clean");
            }
            return AgentWorldDirectorRolloutGateResult.allow(
                    "explicit operator directive and clean ownership");
        }
        AgentActivityKind sourceKind = context.currentActivityKind() == null
                ? directive.targetActivityKind() : context.currentActivityKind();
        if (sourceKind == null) {
            return AgentWorldDirectorRolloutGateResult.block(
                    "automatic lifecycle directive has no activity owner to inspect");
        }
        AgentLiveActivityFacade source = FACADES.bind(sourceKind, entry, agent);
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
