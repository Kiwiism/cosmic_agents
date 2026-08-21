package server.agents.progression.questwork;

import server.agents.progression.questcatalog.AgentQuestCatalogRepository;
import server.agents.progression.questcatalog.AgentQuestDefinition;
import server.agents.progression.questcatalog.AgentQuestSelectionDisposition;

import java.util.List;
import java.util.Optional;

/** Idempotent durable lifecycle facade; it performs no NPC, travel, combat, or inventory action. */
public final class AgentQuestWorkUnitService {
    private final AgentQuestCatalogRepository catalog;
    private final AgentQuestWorkUnitStore store;
    private final AgentQuestWorkReconciler reconciler;

    public AgentQuestWorkUnitService(
            AgentQuestCatalogRepository catalog,
            AgentQuestWorkUnitStore store,
            AgentQuestWorkReconciler reconciler) {
        if (catalog == null || store == null || reconciler == null) {
            throw new IllegalArgumentException("quest catalog, store, and reconciler are required");
        }
        this.catalog = catalog;
        this.store = store;
        this.reconciler = reconciler;
    }

    public synchronized AgentQuestWorkUnit begin(
            String workUnitId,
            String agentId,
            int characterId,
            int questId,
            long nowMs) {
        AgentQuestWorkUnit existing = store.load(workUnitId).orElse(null);
        if (existing != null) {
            if (!existing.agentId().equals(normalize(agentId))
                    || existing.characterId() != characterId
                    || existing.questId() != questId) {
                throw new IllegalStateException("quest work identity is already bound");
            }
            return existing;
        }
        AgentQuestDefinition definition = catalog.find(questId)
                .orElseThrow(() -> new IllegalArgumentException("unknown quest " + questId));
        if (!definition.autonomousStartAllowed()
                || definition.selectionDisposition() != AgentQuestSelectionDisposition.ELIGIBLE) {
            throw new IllegalArgumentException("quest is not approved for autonomous work");
        }
        if (store.loadAll().stream().anyMatch(unit ->
                unit.characterId() == characterId && !unit.terminal())) {
            throw new IllegalStateException("character already has unfinished quest work");
        }
        AgentQuestWorkUnit unit = new AgentQuestWorkUnit(
                1, workUnitId, agentId, characterId, questId,
                catalog.catalog().generatedRevision(), AgentQuestWorkPhase.SELECTED,
                AgentQuestWorkStage.TRAVEL_TO_START, nowMs, nowMs,
                0, 0, "", "SELECTED", java.util.Map.of());
        store.save(unit);
        return unit;
    }

    public synchronized AgentQuestWorkUnit requestSuspend(
            String workUnitId,
            String reason,
            boolean atSafeBoundary,
            long nowMs) {
        AgentQuestWorkUnit current = require(workUnitId);
        if (current.terminal() || current.suspended()) return current;
        AgentQuestWorkUnit updated = current.withPhase(
                atSafeBoundary ? AgentQuestWorkPhase.SUSPENDED
                        : AgentQuestWorkPhase.SUSPEND_REQUESTED,
                nowMs, atSafeBoundary ? "SUSPENDED_AT_SAFE_BOUNDARY" : "SUSPEND_REQUESTED",
                reason);
        store.save(updated);
        return updated;
    }

    public synchronized AgentQuestWorkUnit observeSafeBoundary(String workUnitId, long nowMs) {
        AgentQuestWorkUnit current = require(workUnitId);
        if (current.phase() != AgentQuestWorkPhase.SUSPEND_REQUESTED) return current;
        AgentQuestWorkUnit updated = current.withPhase(
                AgentQuestWorkPhase.SUSPENDED, nowMs,
                "SUSPENDED_AT_SAFE_BOUNDARY", current.suspensionReason());
        store.save(updated);
        return updated;
    }

    public synchronized AgentQuestWorkUnit resume(String workUnitId, long nowMs) {
        AgentQuestWorkUnit current = require(workUnitId);
        if (!current.suspended()) return current;
        AgentQuestWorkUnit updated = current.withPhase(
                AgentQuestWorkPhase.ACTIVE, nowMs, "RESUMED", "");
        store.save(updated);
        return updated;
    }

    public synchronized AgentQuestWorkUnit recordRetry(String workUnitId, String reason, long nowMs) {
        AgentQuestWorkUnit current = require(workUnitId);
        if (current.terminal()) return current;
        AgentQuestWorkUnit updated = current.withRetry(nowMs, normalize(reason));
        store.save(updated);
        return updated;
    }

    public synchronized AgentQuestWorkReconciliation reconcile(
            String workUnitId,
            AgentQuestLiveState live,
            long nowMs) {
        AgentQuestWorkUnit current = require(workUnitId);
        AgentQuestDefinition definition = catalog.find(current.questId()).orElse(null);
        if (definition == null) {
            AgentQuestWorkUnit suspended = current.withPhase(
                    AgentQuestWorkPhase.SUSPENDED, nowMs,
                    "CATALOG_DEFINITION_MISSING", "catalog definition requires review");
            store.save(suspended);
            return new AgentQuestWorkReconciliation(suspended,
                    AgentQuestWorkAction.MANUAL_REVIEW, 0,
                    "quest definition is no longer present in the catalog");
        }
        AgentQuestWorkReconciliation result = reconciler.reconcile(
                current, definition, catalog.catalog().generatedRevision(), live, nowMs);
        store.save(result.workUnit());
        return result;
    }

    public synchronized List<AgentQuestWorkUnit> restoreAll() {
        return store.loadAll();
    }

    public synchronized Optional<AgentQuestWorkUnit> find(String workUnitId) {
        return store.load(workUnitId);
    }

    private AgentQuestWorkUnit require(String workUnitId) {
        return store.load(workUnitId)
                .orElseThrow(() -> new IllegalArgumentException("unknown quest work unit " + workUnitId));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
