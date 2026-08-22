package server.agents.runtime.activity.control;

import client.Character;
import server.agents.integration.cosmic.CosmicAgentWorldContextFactory;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.behavior.AgentBehaviorAdaptationPersistenceRuntime;
import server.agents.runtime.activity.world.AgentFileWorldDirectiveInbox;
import server.agents.runtime.activity.world.AgentFileWorldDirectorSessionStore;
import server.agents.runtime.activity.world.AgentWorldContext;
import server.agents.runtime.activity.world.AgentWorldDirective;
import server.agents.runtime.activity.world.AgentWorldDirectiveEnvelope;
import server.agents.runtime.activity.world.AgentWorldDirectiveInbox;
import server.agents.runtime.activity.world.AgentWorldDirectiveSource;
import server.agents.runtime.activity.world.AgentWorldDirectorMode;
import server.agents.runtime.activity.world.AgentWorldDirectorPhase;
import server.agents.runtime.activity.world.AgentWorldDirectorSession;
import server.agents.runtime.activity.world.AgentWorldDirectorSessionStore;
import server.agents.runtime.activity.outcome.AgentActivityOutcomeEnvelope;
import server.agents.runtime.activity.outcome.AgentActivityOutcomeInbox;
import server.agents.runtime.activity.outcome.AgentFileActivityOutcomeInbox;
import server.agents.runtime.journey.AgentFileJourneyJournalStore;
import server.agents.runtime.journey.AgentJourneyEvent;
import server.agents.runtime.journey.AgentJourneyEventDraft;
import server.agents.runtime.journey.AgentJourneyEventType;
import server.agents.runtime.journey.AgentJourneyJournalStore;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Shared application service for all Director clients. It exposes facts and durable intent;
 * the central scheduler remains the only live gameplay executor.
 */
public final class AgentWorldDirectorExecutive {
    private final AgentWorldDirectorControlService control;
    private final AgentWorldDirectorSessionStore sessions;
    private final AgentWorldDirectiveInbox directives;
    private final AgentActivityOutcomeInbox outcomes;
    private final AgentJourneyJournalStore journey;
    private final AgentWorldDirectiveJourneyRecorder directiveJourney;
    private final AgentDirectorActionCatalog actions;

    public AgentWorldDirectorExecutive(
            AgentWorldDirectorControlService control,
            AgentWorldDirectorSessionStore sessions,
            AgentWorldDirectiveInbox directives,
            AgentActivityOutcomeInbox outcomes,
            AgentJourneyJournalStore journey,
            AgentDirectorActionCatalog actions) {
        if (control == null || sessions == null || directives == null || outcomes == null
                || journey == null || actions == null) {
            throw new IllegalArgumentException("complete Director executive dependencies are required");
        }
        this.control = control;
        this.sessions = sessions;
        this.directives = directives;
        this.outcomes = outcomes;
        this.journey = journey;
        this.directiveJourney = new AgentWorldDirectiveJourneyRecorder(journey);
        this.actions = actions;
    }

    public static AgentWorldDirectorExecutive runtimeDefault() {
        AgentWorldDirectorSessionStore sessions = AgentFileWorldDirectorSessionStore.runtimeDefault();
        AgentWorldDirectiveInbox directives = AgentFileWorldDirectiveInbox.runtimeDefault();
        return new AgentWorldDirectorExecutive(
                new AgentWorldDirectorControlService(sessions, directives),
                sessions, directives, AgentFileActivityOutcomeInbox.runtimeDefault(),
                new AgentFileJourneyJournalStore(),
                new AgentDirectorActionCatalog());
    }

    public AgentDirectorExecutiveView view(
            AgentRuntimeEntry entry, Character agent, int journeyLimit, long nowMs) {
        if (entry == null || agent == null || journeyLimit < 1 || nowMs < 0L) {
            throw new IllegalArgumentException("live Agent, positive limit, and valid time are required");
        }
        AgentWorldContext context = CosmicAgentWorldContextFactory.capture(entry, agent, nowMs);
        AgentDirectorResourceSnapshot resources = AgentDirectorResourceSnapshot.capture(agent);
        AgentDirectorEnergySnapshot energy = AgentDirectorEnergySnapshot.from(
                AgentBehaviorAdaptationPersistenceRuntime.observe(
                        entry, agent.getId(), context.currentActivityKind(), nowMs));
        AgentDirectorProfileSnapshot profile = AgentDirectorProfileSnapshot.capture(entry);
        AgentWorldDirectorSession session = sessions.load(agent.getId()).orElseGet(() ->
                AgentWorldDirectorSession.create(agent.getId(), AgentWorldDirectorMode.DISABLED, nowMs));
        List<AgentWorldDirectiveEnvelope> commandHistory = directives.list(agent.getId());
        List<AgentActivityOutcomeEnvelope> pendingOutcomes = outcomes.pending(
                Integer.toString(agent.getId()));
        List<AgentJourneyEvent> completeJourney = journey.read(Integer.toString(agent.getId()));
        List<AgentJourneyEvent> recent = tail(completeJourney, journeyLimit);
        boolean directiveInFlight = commandHistory.stream().anyMatch(
                envelope -> !envelope.status().terminal());
        List<AgentDirectorAction> available = actions.actions(entry, agent, context, session);
        if (directiveInFlight) {
            available = available.stream().map(action -> action.unavailable(
                    "another Director directive is in progress; cancel it or wait for completion"))
                    .toList();
        }
        AgentDirectorActivityProjection activity = projection(
                context, session, commandHistory, recent, available);
        return new AgentDirectorExecutiveView(
                AgentDirectorContextRevision.create(
                        context, resources, energy, profile, session, commandHistory),
                context, resources, energy, profile, session, activity, available, commandHistory,
                pendingOutcomes, recent);
    }

    public AgentWorldDirectorSession setMode(
            AgentRuntimeEntry entry,
            Character agent,
            AgentWorldDirectorMode mode,
            String reason,
            long nowMs) {
        if (entry == null || agent == null) {
            throw new IllegalArgumentException("live Agent is required");
        }
        if (mode == null) throw new IllegalArgumentException("Director mode is required");
        AgentWorldContext context = CosmicAgentWorldContextFactory.capture(entry, agent, nowMs);
        if (context.currentActivityKind() != null
                && (mode == AgentWorldDirectorMode.DISABLED
                || mode.isObservationOnly())) {
            throw new IllegalStateException(
                    "suspend or stop the active activity before disabling live Director control");
        }
        AgentWorldDirectorSession session = control.setMode(agent.getId(), mode, reason, nowMs);
        journey.append(new AgentJourneyEventDraft(
                "director-mode:" + agent.getId() + ':' + nowMs + ':' + mode,
                Integer.toString(agent.getId()), agent.getId(), nowMs,
                AgentJourneyEventType.DIRECTOR_MODE_CHANGED, session.observedActivityKind(),
                "world-director", "mode:" + agent.getId(), reason,
                Map.of("mode", mode.name(), "phase", session.phase().name())));
        return session;
    }

    public AgentWorldDirectiveEnvelope submit(
            AgentRuntimeEntry entry,
            Character agent,
            String actionId,
            String expectedContextRevision,
            String idempotencyKey,
            String reason,
            boolean confirmDestructive,
            long nowMs) {
        String directiveId = text(idempotencyKey);
        if (directiveId.isEmpty()) {
            throw new IllegalArgumentException("a stable idempotency key is required");
        }
        AgentWorldDirectiveEnvelope existing = directives.load(agent.getId(), directiveId)
                .orElse(null);
        if (existing != null) {
            String boundAction = existing.directive().parameters()
                    .getOrDefault("directorActionId", "");
            if (!boundAction.equals(text(actionId))) {
                throw new IllegalStateException(
                        "idempotency key is already bound to a different Director action");
            }
            return existing;
        }
        AgentDirectorExecutiveView current = view(entry, agent, 1, nowMs);
        if (!current.contextRevision().equals(text(expectedContextRevision))) {
            throw new IllegalStateException("Agent context changed; refresh actions before executing");
        }
        AgentDirectorAction action = current.actions().stream()
                .filter(candidate -> candidate.actionId().equals(text(actionId)))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("unknown Director action"));
        if (!action.availability().executable()) {
            throw new IllegalStateException(action.reason());
        }
        if (action.destructive() && !confirmDestructive) {
            throw new IllegalStateException("destructive Director action requires explicit confirmation");
        }
        Map<String, String> directiveParameters = new java.util.LinkedHashMap<>(
                action.parameters());
        directiveParameters.put("directorActionId", action.actionId());
        AgentWorldDirective directive = new AgentWorldDirective(
                1, directiveId, agent.getId(), action.directiveType(),
                AgentWorldDirectiveSource.OPERATOR, null, action.targetActivityKind(),
                action.requestType(), action.requestId(), directiveParameters,
                action.interruptionPolicy(), action.completionPolicy(), action.priority(),
                nowMs, 0L, text(reason).isEmpty() ? action.reason() : text(reason));
        AgentWorldDirectiveEnvelope submitted = control.submit(directive, nowMs);
        directiveJourney.submitted(submitted, nowMs);
        return submitted;
    }

    public AgentWorldDirectiveEnvelope cancel(
            int agentId, String directiveId, String reason, long nowMs) {
        AgentWorldDirectiveEnvelope cancelled = control.cancel(
                agentId, directiveId, reason, nowMs);
        directiveJourney.resolved(cancelled, nowMs);
        return cancelled;
    }

    public AgentActivityOutcomeEnvelope acknowledgeOutcome(
            int agentId, String outcomeId, String reason, long nowMs) {
        AgentActivityOutcomeEnvelope current = outcomes.load(outcomeId)
                .orElseThrow(() -> new IllegalStateException("unknown activity outcome"));
        if (!current.outcome().agentId().equals(Integer.toString(agentId))) {
            throw new IllegalArgumentException("activity outcome does not belong to this Agent");
        }
        return outcomes.acknowledge(outcomeId, reason, nowMs);
    }

    private static AgentDirectorActivityProjection projection(
            AgentWorldContext context,
            AgentWorldDirectorSession session,
            List<AgentWorldDirectiveEnvelope> directives,
            List<AgentJourneyEvent> journey,
            List<AgentDirectorAction> actions) {
        AgentWorldDirectiveEnvelope latest = directives.stream()
                .max(Comparator.comparingLong(value -> value.directive().createdAtMs()))
                .orElse(null);
        AgentWorldDirectiveEnvelope inFlight = directives.stream()
                .filter(value -> !value.status().terminal())
                .max(Comparator.comparingLong(value -> value.directive().createdAtMs()))
                .orElse(null);
        AgentWorldDirectiveEnvelope rejected = directives.stream()
                .filter(value -> value.status().name().equals("REJECTED"))
                .max(Comparator.comparingLong(AgentWorldDirectiveEnvelope::resolvedAtMs))
                .orElse(null);
        String now = context.currentActivityKind() == null
                ? "Idle on map " + context.mapId()
                : context.currentActivityKind().name().toLowerCase().replace('_', ' ')
                + (context.currentPlanId().isEmpty() ? "" : " — " + context.currentPlanId());
        if (inFlight != null && session.phase() == AgentWorldDirectorPhase.STARTING) {
            now = "Starting " + readable(inFlight.directive().targetActivityKind())
                    + (session.lastReason().isEmpty() ? "" : " — " + session.lastReason());
        } else if (inFlight != null && session.phase() == AgentWorldDirectorPhase.HANDOFF) {
            now = "Switching activity safely"
                    + (session.lastReason().isEmpty() ? "" : " — " + session.lastReason());
        } else if (session.phase() == AgentWorldDirectorPhase.PAUSED) {
            now = "Paused" + (session.observedActivityKind() == null ? ""
                    : " — " + readable(session.observedActivityKind()));
        }
        String next = inFlight != null
                ? "Finish " + inFlight.directive().type().name().toLowerCase().replace('_', ' ')
                : actions.stream()
                .filter(action -> action.availability() == AgentDirectorActionAvailability.RECOMMENDED)
                .map(AgentDirectorAction::label).findFirst().orElse("Awaiting operator decision");
        String waiting = inFlight == null ? "" : inFlight.directive().type().name().toLowerCase()
                .replace('_', ' ') + " — " + session.lastReason();
        String blocked = rejected == null ? "" : rejected.resolution();
        String retained = session.phase() == AgentWorldDirectorPhase.PAUSED
                && session.observedActivityKind() != null
                ? session.observedActivityKind().name().toLowerCase().replace('_', ' ')
                + " session " + session.observedSessionId() : "";
        String lastEvent = !journey.isEmpty() ? journey.getLast().reason()
                : latest == null ? session.lastReason()
                : latest.resolution().isEmpty() ? latest.directive().reason() : latest.resolution();
        return new AgentDirectorActivityProjection(
                now, next, waiting, blocked, retained, lastEvent);
    }

    private static String readable(server.agents.runtime.activity.session.AgentActivityKind kind) {
        return kind == null ? "activity" : kind.name().toLowerCase().replace('_', ' ');
    }

    private static <T> List<T> tail(List<T> values, int limit) {
        if (values.size() <= limit) return List.copyOf(values);
        return List.copyOf(values.subList(values.size() - limit, values.size()));
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
