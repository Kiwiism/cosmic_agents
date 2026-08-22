package server.agents.runtime.activity.control;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.control.binding.AgentWorldActivityBinding;
import server.agents.runtime.activity.control.binding.AgentWorldActivityBindingResolver;
import server.agents.runtime.activity.session.AgentActivityAdmissionResult;
import server.agents.runtime.activity.session.AgentActivityHandoffCoordinator;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.session.AgentActivitySessionSnapshot;
import server.agents.runtime.activity.session.AgentActivityTransferPort;
import server.agents.runtime.activity.session.AgentPersistentActivityHandoffCoordinator;
import server.agents.runtime.activity.world.AgentWorldDirective;
import server.agents.runtime.activity.world.AgentWorldDirectiveEnvelope;
import server.agents.runtime.activity.world.AgentWorldDirectiveInbox;
import server.agents.runtime.activity.world.AgentWorldDirectiveStatus;
import server.agents.runtime.activity.world.AgentWorldDirectiveType;
import server.agents.runtime.activity.world.AgentWorldDirectorMode;
import server.agents.runtime.activity.world.AgentWorldDirectorPhase;
import server.agents.runtime.activity.world.AgentWorldDirectorSession;
import server.agents.runtime.activity.world.AgentWorldDirectorSessionStore;

import java.util.Optional;
import server.agents.progression.AgentCareerBuildBundleService;

/** Durable one-step processor. The central scheduler decides when it may run. */
public final class AgentWorldDirectiveProcessor {
    private final AgentWorldDirectorSessionStore sessions;
    private final AgentWorldDirectiveInbox directives;
    private final AgentPersistentActivityHandoffCoordinator handoffs;
    private final AgentWorldActivityBindingResolver bindings;
    private final AgentWorldDirectiveExecutionGate executionGate;
    private final AgentWorldActivityLifecycleHandler lifecycle;
    private final long handoffTimeoutMs;

    public AgentWorldDirectiveProcessor(
            AgentWorldDirectorSessionStore sessions,
            AgentWorldDirectiveInbox directives,
            AgentPersistentActivityHandoffCoordinator handoffs,
            AgentWorldActivityBindingResolver bindings,
            AgentWorldDirectiveExecutionGate executionGate,
            long handoffTimeoutMs) {
        this(sessions, directives, handoffs, bindings, executionGate,
                AgentWorldActivityLifecycleHandler.unsupported(), handoffTimeoutMs);
    }

    public AgentWorldDirectiveProcessor(
            AgentWorldDirectorSessionStore sessions,
            AgentWorldDirectiveInbox directives,
            AgentPersistentActivityHandoffCoordinator handoffs,
            AgentWorldActivityBindingResolver bindings,
            AgentWorldDirectiveExecutionGate executionGate,
            AgentWorldActivityLifecycleHandler lifecycle,
            long handoffTimeoutMs) {
        if (sessions == null || directives == null || handoffs == null
                || bindings == null || executionGate == null || lifecycle == null
                || handoffTimeoutMs <= 0L) {
            throw new IllegalArgumentException("complete Director processor dependencies are required");
        }
        this.sessions = sessions;
        this.directives = directives;
        this.handoffs = handoffs;
        this.bindings = bindings;
        this.executionGate = executionGate;
        this.lifecycle = lifecycle;
        this.handoffTimeoutMs = handoffTimeoutMs;
    }

    public Result tick(
            AgentRuntimeEntry entry,
            Character agent,
            AgentActivityKind sourceKind,
            String sourceSessionId,
            long nowMs) {
        if (entry == null || agent == null || nowMs < 0L) return Result.idle("invalid Agent context");
        AgentWorldDirectorSession session = sessions.load(agent.getId()).orElse(null);
        if (session == null) return Result.idle("no Director session");
        AgentWorldDirectiveEnvelope envelope = claimed(agent.getId()).orElseGet(() ->
                directives.nextPending(agent.getId(), nowMs).orElse(null));
        if (envelope == null) return Result.idle("no Director directive");
        AgentWorldDirective directive = envelope.directive();
        if (envelope.status() == AgentWorldDirectiveStatus.PENDING) {
            var gate = executionGate.inspect(session, directive, entry, agent, nowMs);
            if (!gate.permitted()) return Result.blocked(gate.reason());
            envelope = directives.claim(agent.getId(), directive.directiveId(), nowMs);
        }
        Result result = switch (directive.type()) {
            case SET_MODE -> resolveMode(session, directive, entry, nowMs);
            case PAUSE -> resolveMode(session, directive, entry,
                    AgentWorldDirectorMode.EMERGENCY_HOLD, nowMs);
            case RESUME -> resolveMode(session, directive, entry,
                    AgentWorldDirectorMode.MANUAL, nowMs);
            case STOP_ACTIVITY, SUSPEND_ACTIVITY, RESUME_ACTIVITY,
                    ABANDON_ACTIVITY, REQUEST_SUPPLY_MAINTENANCE -> advanceLifecycle(
                    directive, session, entry, agent, sourceKind, sourceSessionId, nowMs);
            case CONFIGURE_CAREER_BUILD -> configureCareer(
                    directive, session, entry, agent, sourceKind, nowMs);
            case START_ACTIVITY, TRANSFER_ACTIVITY -> advanceActivity(
                    directive, session, entry, agent, sourceKind, sourceSessionId, nowMs);
        };
        return result.withDirectiveId(directive.directiveId());
    }

    private Result configureCareer(
            AgentWorldDirective directive,
            AgentWorldDirectorSession session,
            AgentRuntimeEntry entry,
            Character agent,
            AgentActivityKind sourceKind,
            long nowMs) {
        if (sourceKind != null) {
            return reject(directive, session,
                    "finish or suspend the current activity before changing career build", nowMs);
        }
        try {
            var bundle = AgentCareerBuildBundleService.assignExplicit(
                    entry, directive.requestId(), nowMs);
            String reason = "career build selected: " + bundle.bundleId();
            directives.resolve(directive.agentId(), directive.directiveId(),
                    AgentWorldDirectiveStatus.COMPLETED, reason, nowMs);
            sessions.save(session.transition(AgentWorldDirectorPhase.WAITING,
                    null, "", "", reason, nowMs));
            return Result.completed(reason);
        } catch (java.io.IOException | RuntimeException failure) {
            return reject(directive, session,
                    "career build selection failed: " + failure.getMessage(), nowMs);
        }
    }

    private Result advanceLifecycle(
            AgentWorldDirective directive,
            AgentWorldDirectorSession session,
            AgentRuntimeEntry entry,
            Character agent,
            AgentActivityKind sourceKind,
            String sourceSessionId,
            long nowMs) {
        AgentActivityKind retainedKind = sourceKind != null
                ? sourceKind : session.observedActivityKind();
        String retainedSessionId = sourceSessionId == null || sourceSessionId.isBlank()
                ? session.observedSessionId() : sourceSessionId;
        AgentWorldActivityLifecycleHandler.Result lifecycleResult = lifecycle.advance(
                directive, session, entry, agent, retainedKind, retainedSessionId, nowMs);
        if (lifecycleResult.status()
                == AgentWorldActivityLifecycleHandler.Result.Status.PROGRESSED) {
            sessions.save(session.transition(AgentWorldDirectorPhase.HANDOFF,
                    lifecycleResult.activityKind(), lifecycleResult.sessionId(),
                    directive.directiveId(), lifecycleResult.reason(), nowMs));
            return Result.progressed(lifecycleResult.reason());
        }
        if (lifecycleResult.status()
                == AgentWorldActivityLifecycleHandler.Result.Status.REJECTED) {
            return reject(directive, session, lifecycleResult.reason(), nowMs);
        }
        directives.resolve(directive.agentId(), directive.directiveId(),
                AgentWorldDirectiveStatus.COMPLETED, lifecycleResult.reason(), nowMs);
        AgentWorldDirectorPhase nextPhase = switch (directive.type()) {
            case SUSPEND_ACTIVITY -> AgentWorldDirectorPhase.PAUSED;
            case RESUME_ACTIVITY -> AgentWorldDirectorPhase.RUNNING;
            case STOP_ACTIVITY, ABANDON_ACTIVITY -> AgentWorldDirectorPhase.WAITING;
            case REQUEST_SUPPLY_MAINTENANCE -> session.phase();
            default -> throw new IllegalStateException("unexpected lifecycle directive");
        };
        AgentActivityKind nextKind = switch (directive.type()) {
            case STOP_ACTIVITY, ABANDON_ACTIVITY -> null;
            default -> lifecycleResult.activityKind();
        };
        String nextSessionId = switch (directive.type()) {
            case STOP_ACTIVITY, ABANDON_ACTIVITY -> "";
            default -> lifecycleResult.sessionId();
        };
        sessions.save(session.transition(nextPhase, nextKind, nextSessionId,
                "", lifecycleResult.reason(), nowMs));
        return Result.completed(lifecycleResult.reason());
    }

    private Result advanceActivity(
            AgentWorldDirective directive,
            AgentWorldDirectorSession session,
            AgentRuntimeEntry entry,
            Character agent,
            AgentActivityKind sourceKind,
            String sourceSessionId,
            long nowMs) {
        AgentActivityKind retainedSourceKind = session.phase() == AgentWorldDirectorPhase.HANDOFF
                && session.observedActivityKind() != null
                ? session.observedActivityKind() : sourceKind;
        String retainedSourceSessionId = session.phase() == AgentWorldDirectorPhase.HANDOFF
                && !session.observedSessionId().isBlank()
                ? session.observedSessionId() : sourceSessionId;
        AgentWorldActivityBinding binding;
        try {
            binding = bindings.bind(directive, entry, agent,
                    retainedSourceKind, retainedSourceSessionId);
        } catch (RuntimeException invalidBinding) {
            return reject(directive, session,
                    "activity binding failed: " + invalidBinding.getMessage(), nowMs);
        }
        AgentActivitySessionSnapshot source = binding.source().snapshot(nowMs);
        String handoffId = directive.directiveId() + ":handoff";
        AgentActivityHandoffCoordinator.Handoff restored = handoffs.restore(handoffId).orElse(null);
        boolean switchRequired = restored != null || source != null && source.phase().retainsSession()
                && source.kind() != directive.targetActivityKind();
        if (!switchRequired) {
            AgentActivityTransferPort.Result transfer = binding.transfer().advance(nowMs);
            if (transfer.status() == AgentActivityTransferPort.Result.Status.PENDING) {
                sessions.save(session.transition(AgentWorldDirectorPhase.STARTING,
                        directive.targetActivityKind(), "", directive.directiveId(),
                        transfer.reason(), nowMs));
                return Result.progressed(transfer.reason());
            }
            if (transfer.status() == AgentActivityTransferPort.Result.Status.FAILED) {
                return reject(directive, session, transfer.reason(), nowMs);
            }
            AgentActivityAdmissionResult admission = binding.target().requestEntry(nowMs);
            return switch (admission.status()) {
                case ACCEPTED -> completeAdmission(directive, session, admission.session(), nowMs);
                case DEFERRED -> Result.progressed("destination admission deferred: " + admission.reason());
                case REJECTED -> reject(directive, session, admission.reason(), nowMs);
            };
        }
        AgentActivityHandoffCoordinator.Handoff handoff = restored != null ? restored
                : handoffs.begin(handoffId, "world-director:" + directive.directiveId(),
                        directive.targetActivityKind(), binding.source(), binding.targetPreflight(),
                        nowMs, nowMs + handoffTimeoutMs);
        sessions.save(session.transition(AgentWorldDirectorPhase.HANDOFF,
                retainedSourceKind, retainedSourceSessionId, handoffId, handoff.reason(), nowMs));
        if (!handoff.terminal()) {
            handoff = handoffs.advance(handoffId, binding.source(), binding.transfer(),
                    binding.target(), binding.rollback(), nowMs);
        }
        if (!handoff.terminal()) return Result.progressed(handoff.reason());
        if (handoff.phase() == AgentActivityHandoffCoordinator.Phase.COMPLETED) {
            // Target adapters are idempotent for the same request and recover the admitted handle.
            AgentActivityAdmissionResult admitted = binding.target().requestEntry(nowMs);
            if (admitted.status() == AgentActivityAdmissionResult.Status.ACCEPTED) {
                Result result = completeAdmission(directive, session, admitted.session(), nowMs);
                handoffs.acknowledgeTerminal(handoffId);
                return result;
            }
            Result result = reject(directive, session,
                    "completed handoff could not recover target session: " + admitted.reason(), nowMs);
            handoffs.acknowledgeTerminal(handoffId);
            return result;
        }
        Result result = reject(directive, session, handoff.reason(), nowMs);
        handoffs.acknowledgeTerminal(handoffId);
        return result;
    }

    private Result completeAdmission(
            AgentWorldDirective directive,
            AgentWorldDirectorSession session,
            AgentActivitySessionSnapshot admitted,
            long nowMs) {
        directives.resolve(directive.agentId(), directive.directiveId(),
                AgentWorldDirectiveStatus.COMPLETED, "destination admitted", nowMs);
        sessions.save(session.transition(AgentWorldDirectorPhase.RUNNING,
                admitted.kind(), admitted.sessionId(), "", "destination admitted", nowMs));
        return Result.completed("destination admitted");
    }

    private Result resolveMode(
            AgentWorldDirectorSession session,
            AgentWorldDirective directive,
            AgentRuntimeEntry entry,
            long nowMs) {
        return resolveMode(session, directive, entry, directive.requestedMode(), nowMs);
    }

    private Result resolveMode(
            AgentWorldDirectorSession session,
            AgentWorldDirective directive,
            AgentRuntimeEntry entry,
            AgentWorldDirectorMode mode,
            long nowMs) {
        if (mode == null) return reject(directive, session, "mode directive lacks a mode", nowMs);
        AgentWorldDirectorSession updated = session.withMode(mode, directive.reason(), nowMs);
        sessions.save(updated);
        if (entry != null) {
            entry.capabilityStates().require(AgentWorldDirectorRuntimeState.STATE_KEY)
                    .restore(mode, directive.reason(), nowMs);
            AgentWorldDirectorObserveState observe = entry.capabilityStates()
                    .require(AgentWorldDirectorObserveState.STATE_KEY);
            if (mode.isObservationOnly()) {
                observe.configure(mode, config.AgentTuning.longValue(
                        "server.agents.runtime.AgentRegistrationCoordinator.WORLD_DIRECTOR_OBSERVE_INTERVAL_MS"));
            } else {
                observe.disable();
            }
        }
        directives.resolve(directive.agentId(), directive.directiveId(),
                AgentWorldDirectiveStatus.COMPLETED, "Director mode updated", nowMs);
        return Result.completed("Director mode updated");
    }

    private Result reject(
            AgentWorldDirective directive,
            AgentWorldDirectorSession session,
            String reason,
            long nowMs) {
        directives.resolve(directive.agentId(), directive.directiveId(),
                AgentWorldDirectiveStatus.REJECTED, reason, nowMs);
        sessions.save(session.transition(AgentWorldDirectorPhase.FAILED,
                session.observedActivityKind(), session.observedSessionId(), "", reason, nowMs));
        return Result.rejected(reason);
    }

    private Optional<AgentWorldDirectiveEnvelope> claimed(int agentId) {
        return directives.list(agentId).stream()
                .filter(value -> value.status() == AgentWorldDirectiveStatus.CLAIMED)
                .findFirst();
    }

    public record Result(Status status, String reason, String directiveId) {
        public Result {
            reason = reason == null ? "" : reason.trim();
            directiveId = directiveId == null ? "" : directiveId.trim();
        }
        public static Result idle(String reason) { return new Result(Status.IDLE, reason, ""); }
        public static Result blocked(String reason) { return new Result(Status.BLOCKED, reason, ""); }
        public static Result progressed(String reason) { return new Result(Status.PROGRESSED, reason, ""); }
        public static Result completed(String reason) { return new Result(Status.COMPLETED, reason, ""); }
        public static Result rejected(String reason) { return new Result(Status.REJECTED, reason, ""); }
        public Result withDirectiveId(String value) {
            return new Result(status, reason, value);
        }
        public enum Status { IDLE, BLOCKED, PROGRESSED, COMPLETED, REJECTED }
    }
}
