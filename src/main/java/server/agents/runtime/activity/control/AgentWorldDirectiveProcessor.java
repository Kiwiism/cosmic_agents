package server.agents.runtime.activity.control;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.control.binding.AgentWorldActivityBinding;
import server.agents.runtime.activity.control.binding.AgentWorldActivityBindingResolver;
import server.agents.runtime.activity.session.AgentActivityAdmissionResult;
import server.agents.runtime.activity.session.AgentActivityHandoffCoordinator;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.session.AgentActivitySessionSnapshot;
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

/** Durable one-step processor. The central scheduler decides when it may run. */
public final class AgentWorldDirectiveProcessor {
    private final AgentWorldDirectorSessionStore sessions;
    private final AgentWorldDirectiveInbox directives;
    private final AgentPersistentActivityHandoffCoordinator handoffs;
    private final AgentWorldActivityBindingResolver bindings;
    private final AgentWorldDirectiveExecutionGate executionGate;
    private final long handoffTimeoutMs;

    public AgentWorldDirectiveProcessor(
            AgentWorldDirectorSessionStore sessions,
            AgentWorldDirectiveInbox directives,
            AgentPersistentActivityHandoffCoordinator handoffs,
            AgentWorldActivityBindingResolver bindings,
            AgentWorldDirectiveExecutionGate executionGate,
            long handoffTimeoutMs) {
        if (sessions == null || directives == null || handoffs == null
                || bindings == null || executionGate == null || handoffTimeoutMs <= 0L) {
            throw new IllegalArgumentException("complete Director processor dependencies are required");
        }
        this.sessions = sessions;
        this.directives = directives;
        this.handoffs = handoffs;
        this.bindings = bindings;
        this.executionGate = executionGate;
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
        return switch (directive.type()) {
            case SET_MODE -> resolveMode(session, directive, nowMs);
            case PAUSE -> resolveMode(session, directive, AgentWorldDirectorMode.EMERGENCY_HOLD, nowMs);
            case RESUME -> resolveMode(session, directive, AgentWorldDirectorMode.MANUAL, nowMs);
            case STOP_ACTIVITY -> reject(directive, session,
                    "STOP_ACTIVITY requires a bound graceful-stop compiler", nowMs);
            case START_ACTIVITY, TRANSFER_ACTIVITY -> advanceActivity(
                    directive, session, entry, agent, sourceKind, sourceSessionId, nowMs);
        };
    }

    private Result advanceActivity(
            AgentWorldDirective directive,
            AgentWorldDirectorSession session,
            AgentRuntimeEntry entry,
            Character agent,
            AgentActivityKind sourceKind,
            String sourceSessionId,
            long nowMs) {
        AgentWorldActivityBinding binding = bindings.bind(
                directive, entry, agent, sourceKind, sourceSessionId);
        AgentActivitySessionSnapshot source = binding.source().snapshot(nowMs);
        boolean switchRequired = source != null && source.phase().ownsExecution()
                && source.kind() != directive.targetActivityKind();
        if (!switchRequired) {
            AgentActivityAdmissionResult admission = binding.target().requestEntry(nowMs);
            return switch (admission.status()) {
                case ACCEPTED -> completeAdmission(directive, session, admission.session(), nowMs);
                case DEFERRED -> Result.progressed("destination admission deferred: " + admission.reason());
                case REJECTED -> reject(directive, session, admission.reason(), nowMs);
            };
        }
        String handoffId = directive.directiveId() + ":handoff";
        AgentActivityHandoffCoordinator.Handoff handoff = handoffs.restore(handoffId)
                .orElseGet(() -> handoffs.begin(handoffId, "world-director:" + directive.directiveId(),
                        directive.targetActivityKind(), binding.source(), binding.targetPreflight(),
                        nowMs, nowMs + handoffTimeoutMs));
        sessions.save(session.transition(AgentWorldDirectorPhase.HANDOFF,
                sourceKind, sourceSessionId, handoffId, handoff.reason(), nowMs));
        if (!handoff.terminal()) {
            handoff = handoffs.advance(handoffId, binding.source(), binding.transfer(),
                    binding.target(), binding.rollback(), nowMs);
        }
        if (!handoff.terminal()) return Result.progressed(handoff.reason());
        if (handoff.phase() == AgentActivityHandoffCoordinator.Phase.COMPLETED) {
            // Target adapters are idempotent for the same request and recover the admitted handle.
            AgentActivityAdmissionResult admitted = binding.target().requestEntry(nowMs);
            if (admitted.status() == AgentActivityAdmissionResult.Status.ACCEPTED) {
                return completeAdmission(directive, session, admitted.session(), nowMs);
            }
            return reject(directive, session,
                    "completed handoff could not recover target session: " + admitted.reason(), nowMs);
        }
        return reject(directive, session, handoff.reason(), nowMs);
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
            AgentWorldDirectorSession session, AgentWorldDirective directive, long nowMs) {
        return resolveMode(session, directive, directive.requestedMode(), nowMs);
    }

    private Result resolveMode(
            AgentWorldDirectorSession session,
            AgentWorldDirective directive,
            AgentWorldDirectorMode mode,
            long nowMs) {
        if (mode == null) return reject(directive, session, "mode directive lacks a mode", nowMs);
        sessions.save(session.withMode(mode, directive.reason(), nowMs));
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

    public record Result(Status status, String reason) {
        public Result { reason = reason == null ? "" : reason.trim(); }
        public static Result idle(String reason) { return new Result(Status.IDLE, reason); }
        public static Result blocked(String reason) { return new Result(Status.BLOCKED, reason); }
        public static Result progressed(String reason) { return new Result(Status.PROGRESSED, reason); }
        public static Result completed(String reason) { return new Result(Status.COMPLETED, reason); }
        public static Result rejected(String reason) { return new Result(Status.REJECTED, reason); }
        public enum Status { IDLE, BLOCKED, PROGRESSED, COMPLETED, REJECTED }
    }
}
