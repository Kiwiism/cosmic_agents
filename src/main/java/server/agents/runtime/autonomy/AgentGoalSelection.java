package server.agents.runtime.autonomy;

import server.agents.plans.AgentPlanDefinition;

import java.util.List;

/** Deterministic result of evaluating one immutable proposal set. */
public record AgentGoalSelection(
        AgentGoalProposal accepted,
        AgentPlanDefinition plan,
        List<Rejection> rejections,
        String reason) {

    public AgentGoalSelection {
        rejections = List.copyOf(rejections == null ? List.of() : rejections);
        reason = reason == null ? "" : reason;
        if ((accepted == null) != (plan == null)) {
            throw new IllegalArgumentException(
                    "An accepted goal and resolved plan must be present together");
        }
    }

    public boolean selected() {
        return accepted != null;
    }

    public record Rejection(String proposalId, String reason) {
        public Rejection {
            if (proposalId == null || proposalId.isBlank()
                    || reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("A proposal rejection requires an explanation");
            }
        }
    }
}
