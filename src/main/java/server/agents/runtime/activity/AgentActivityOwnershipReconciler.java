package server.agents.runtime.activity;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.List;

/** Resolves restored primary ownership only when an authoritative expected owner is known. */
public final class AgentActivityOwnershipReconciler {
    private final AgentActivityControllerRegistry registry;

    public AgentActivityOwnershipReconciler(AgentActivityControllerRegistry registry) {
        if (registry == null) throw new IllegalArgumentException("controller registry is required");
        this.registry = registry;
    }

    public AgentActivityOwnershipReconciliation reconcile(
            AgentRuntimeEntry entry,
            Character agent,
            AgentActivityKind expectedOwner,
            long nowMs) {
        if (entry == null || agent == null || nowMs < 0L) {
            throw new IllegalArgumentException("entry, Agent, and current time are required");
        }
        List<AgentActivityController> retained = retained(entry, agent);
        if (retained.size() <= 1) {
            return result(AgentActivityOwnershipReconciliation.Status.CLEAN,
                    expectedOwner, retained, retained.isEmpty()
                            ? "no restored primary owner" : "one restored primary owner");
        }
        if (expectedOwner == null) {
            return result(AgentActivityOwnershipReconciliation.Status.BLOCKED,
                    null, retained,
                    "multiple restored primary owners lack an authoritative handoff owner");
        }
        AgentActivityController expected = retained.stream()
                .filter(controller -> controller.activityKind() == expectedOwner)
                .findFirst().orElse(null);
        if (expected == null) {
            return result(AgentActivityOwnershipReconciliation.Status.BLOCKED,
                    expectedOwner, retained,
                    "authoritative handoff owner is not among restored primary owners");
        }
        for (AgentActivityController controller : retained) {
            if (controller != expected) {
                controller.requestStop(entry, agent,
                        "registration ownership reconciliation retained " + expectedOwner, nowMs);
            }
        }
        List<AgentActivityController> after = retained(entry, agent);
        if (after.size() == 1 && after.getFirst().activityKind() == expectedOwner) {
            return result(AgentActivityOwnershipReconciliation.Status.RECONCILED,
                    expectedOwner, after, "restored ownership reconciled");
        }
        return result(AgentActivityOwnershipReconciliation.Status.DRAINING,
                expectedOwner, after,
                "conflicting restored owners are draining at their safe boundaries");
    }

    private List<AgentActivityController> retained(AgentRuntimeEntry entry, Character agent) {
        return registry.controllers().stream()
                .filter(AgentActivityController::exclusive)
                .filter(controller -> controller.active(entry, agent))
                .toList();
    }

    private static AgentActivityOwnershipReconciliation result(
            AgentActivityOwnershipReconciliation.Status status,
            AgentActivityKind expectedOwner,
            List<AgentActivityController> retained,
            String reason) {
        return new AgentActivityOwnershipReconciliation(status, expectedOwner,
                retained.stream().map(AgentActivityController::activityKind).toList(), reason);
    }
}
