package server.agents.plans.amherst;

import server.agents.capabilities.runtime.AgentCapabilityResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AmherstPlanProgressService {
    private static final int MAX_JOURNAL_EVENTS = 512;

    public AmherstPlanProgressSnapshot ensureObjectives(AmherstPlanProgressSnapshot snapshot,
                                                        AmherstPlanCard card,
                                                        long nowMs) {
        Map<String, AmherstObjectiveProgress> progress = new LinkedHashMap<>(snapshot.objectives());
        card.objectives().forEach(objective -> progress.putIfAbsent(
                objective.objectiveId(), AmherstObjectiveProgress.pending(objective.objectiveId())));
        if (progress.equals(snapshot.objectives())) {
            return snapshot;
        }
        return update(snapshot, progress, snapshot.journal(), nowMs);
    }

    public AmherstPlanProgressSnapshot start(AmherstPlanProgressSnapshot snapshot,
                                             String objectiveId,
                                             long nowMs,
                                             int journalStart) {
        AmherstObjectiveProgress current = snapshot.objectives().getOrDefault(
                objectiveId, AmherstObjectiveProgress.pending(objectiveId));
        if (current.status() == AmherstObjectiveProgressStatus.RUNNING) {
            return snapshot;
        }
        AmherstObjectiveProgress next = new AmherstObjectiveProgress(objectiveId,
                AmherstObjectiveProgressStatus.RUNNING, current.attempts() + 1,
                "IN_PROGRESS", "objective assigned", nowMs, nowMs, 0L, journalStart, -1);
        return replace(snapshot, next, event(nowMs, AmherstPlanJournalEventType.OBJECTIVE_STARTED,
                objectiveId, "IN_PROGRESS", "objective assigned"), nowMs);
    }

    public AmherstPlanProgressSnapshot recoverInterrupted(AmherstPlanProgressSnapshot snapshot, long nowMs) {
        AmherstPlanProgressSnapshot recovered = snapshot;
        for (AmherstObjectiveProgress current : snapshot.objectives().values()) {
            if (current.status() != AmherstObjectiveProgressStatus.RUNNING) {
                continue;
            }
            AmherstObjectiveProgress pending = new AmherstObjectiveProgress(current.objectiveId(),
                    AmherstObjectiveProgressStatus.PENDING, current.attempts(),
                    "RUNTIME_RESTART", "interrupted runtime frame will be reconciled and retried",
                    current.startedAtMs(), nowMs, 0L,
                    current.capabilityJournalStart(), current.capabilityJournalEnd());
            recovered = replace(recovered, pending,
                    event(nowMs, AmherstPlanJournalEventType.RECONCILED_REOPENED,
                            current.objectiveId(), "RUNTIME_RESTART",
                            "interrupted runtime frame reopened"), nowMs);
        }
        return recovered;
    }

    public AmherstPlanProgressSnapshot terminal(AmherstPlanProgressSnapshot snapshot,
                                                String objectiveId,
                                                AgentCapabilityResult result,
                                                long nowMs,
                                                int journalEnd) {
        AmherstObjectiveProgress current = snapshot.objectives().getOrDefault(
                objectiveId, AmherstObjectiveProgress.pending(objectiveId));
        AmherstObjectiveProgressStatus status = switch (result.status()) {
            case SUCCESS -> AmherstObjectiveProgressStatus.SATISFIED;
            case CANCELLED -> AmherstObjectiveProgressStatus.CANCELLED;
            case MISSING_REQUIREMENT, BLOCKED_BY_SCOPE, BLOCKED_FORBIDDEN_QUEST,
                    BLOCKED_FORBIDDEN_MAP, BLOCKED_FORBIDDEN_NPC -> AmherstObjectiveProgressStatus.BLOCKED;
            default -> AmherstObjectiveProgressStatus.FAILED;
        };
        if (current.status() == status
                && current.reasonCode().equals(result.reasonCode().name())
                && current.message().equals(result.message())) {
            return snapshot;
        }
        AmherstObjectiveProgress next = new AmherstObjectiveProgress(objectiveId, status,
                current.attempts(), result.reasonCode().name(), result.message(),
                current.startedAtMs(), nowMs, status == AmherstObjectiveProgressStatus.SATISFIED ? nowMs : 0L,
                current.capabilityJournalStart(), journalEnd);
        AmherstPlanJournalEventType type = switch (status) {
            case CANCELLED -> AmherstPlanJournalEventType.CANCELLED;
            case BLOCKED -> AmherstPlanJournalEventType.BLOCKED;
            default -> AmherstPlanJournalEventType.OBJECTIVE_TERMINAL;
        };
        return replace(snapshot, next, event(nowMs, type, objectiveId,
                result.reasonCode().name(), result.message()), nowMs);
    }

    public AmherstPlanProgressSnapshot reconcile(AmherstPlanProgressSnapshot snapshot,
                                                 String objectiveId,
                                                 boolean satisfied,
                                                 String reason,
                                                 long nowMs) {
        AmherstObjectiveProgress current = snapshot.objectives().getOrDefault(
                objectiveId, AmherstObjectiveProgress.pending(objectiveId));
        if (satisfied && current.status() == AmherstObjectiveProgressStatus.SATISFIED) {
            return snapshot;
        }
        if (!satisfied && current.status() != AmherstObjectiveProgressStatus.SATISFIED) {
            return snapshot;
        }
        AmherstObjectiveProgressStatus status = satisfied
                ? AmherstObjectiveProgressStatus.SATISFIED : AmherstObjectiveProgressStatus.PENDING;
        AmherstObjectiveProgress next = new AmherstObjectiveProgress(objectiveId, status,
                current.attempts(), "LIVE_RECONCILIATION", reason,
                current.startedAtMs(), nowMs, satisfied ? nowMs : 0L,
                current.capabilityJournalStart(), current.capabilityJournalEnd());
        AmherstPlanJournalEventType type = satisfied
                ? AmherstPlanJournalEventType.RECONCILED_SATISFIED
                : AmherstPlanJournalEventType.RECONCILED_REOPENED;
        return replace(snapshot, next, event(nowMs, type, objectiveId,
                "LIVE_RECONCILIATION", reason), nowMs);
    }

    public AmherstPlanProgressSnapshot append(AmherstPlanProgressSnapshot snapshot,
                                              AmherstPlanJournalEvent event,
                                              long nowMs) {
        List<AmherstPlanJournalEvent> journal = boundedJournal(snapshot.journal(), event);
        return update(snapshot, snapshot.objectives(), journal, nowMs);
    }

    private AmherstPlanProgressSnapshot replace(AmherstPlanProgressSnapshot snapshot,
                                                AmherstObjectiveProgress progress,
                                                AmherstPlanJournalEvent event,
                                                long nowMs) {
        Map<String, AmherstObjectiveProgress> objectives = new LinkedHashMap<>(snapshot.objectives());
        objectives.put(progress.objectiveId(), progress);
        return update(snapshot, objectives, boundedJournal(snapshot.journal(), event), nowMs);
    }

    private AmherstPlanProgressSnapshot update(AmherstPlanProgressSnapshot snapshot,
                                               Map<String, AmherstObjectiveProgress> objectives,
                                               List<AmherstPlanJournalEvent> journal,
                                               long nowMs) {
        return new AmherstPlanProgressSnapshot(snapshot.planId(), snapshot.characterId(),
                snapshot.revision() + 1, nowMs, objectives, journal);
    }

    private static List<AmherstPlanJournalEvent> boundedJournal(List<AmherstPlanJournalEvent> existing,
                                                               AmherstPlanJournalEvent event) {
        List<AmherstPlanJournalEvent> journal = new ArrayList<>(existing);
        journal.add(event);
        if (journal.size() > MAX_JOURNAL_EVENTS) {
            journal = new ArrayList<>(journal.subList(journal.size() - MAX_JOURNAL_EVENTS, journal.size()));
        }
        return List.copyOf(journal);
    }

    private static AmherstPlanJournalEvent event(long nowMs,
                                                 AmherstPlanJournalEventType type,
                                                 String objectiveId,
                                                 String reasonCode,
                                                 String message) {
        return new AmherstPlanJournalEvent(nowMs, type, objectiveId, reasonCode, message);
    }
}
