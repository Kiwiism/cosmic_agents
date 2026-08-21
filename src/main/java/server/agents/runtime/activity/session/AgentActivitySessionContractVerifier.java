package server.agents.runtime.activity.session;

import java.util.ArrayList;
import java.util.List;

/** Read-only common lifecycle checks reusable by every concrete adapter test. */
public final class AgentActivitySessionContractVerifier {
    private AgentActivitySessionContractVerifier() {
    }

    public static List<String> snapshotIssues(AgentActivitySessionSnapshot snapshot) {
        List<String> issues = new ArrayList<>();
        if (snapshot == null) {
            return List.of("adapter returned no session snapshot");
        }
        if (snapshot.phase().ownsAgent()) {
            if (snapshot.sessionId().isBlank()) issues.add("owning snapshot has no session id");
            if (snapshot.callerId().isBlank()) issues.add("owning snapshot has no caller id");
            if (snapshot.agentId().isBlank()) issues.add("owning snapshot has no Agent id");
        }
        if (snapshot.phase() == AgentActivityPhase.IDLE && !snapshot.sessionId().isBlank()) {
            issues.add("idle snapshot retains a session id");
        }
        return List.copyOf(issues);
    }

    public static List<String> terminalIssues(AgentActivityTerminalOutcome outcome) {
        if (outcome == null) return List.of("adapter returned no terminal outcome");
        List<String> issues = new ArrayList<>();
        if (!outcome.phase().terminal()) issues.add("outcome phase is not terminal");
        if (outcome.sessionId().isBlank()) issues.add("terminal outcome has no session id");
        if (outcome.agentId().isBlank()) issues.add("terminal outcome has no Agent id");
        if (outcome.endedAtMs() < outcome.startedAtMs()) {
            issues.add("terminal outcome ends before it starts");
        }
        return List.copyOf(issues);
    }

    public static List<String> admissionIssues(
            AgentActivityAdmissionResult result,
            AgentActivityKind expectedKind,
            String expectedAgentId,
            long nowMs) {
        if (result == null) return List.of("adapter returned no admission result");
        List<String> issues = new ArrayList<>();
        if (result.status() == AgentActivityAdmissionResult.Status.ACCEPTED) {
            issues.addAll(snapshotIssues(result.session()));
            if (result.session() != null && result.session().kind() != expectedKind) {
                issues.add("admission returned the wrong activity kind");
            }
            if (result.session() != null && expectedAgentId != null
                    && !expectedAgentId.equals(result.session().agentId())) {
                issues.add("admission returned the wrong Agent id");
            }
        }
        if (result.status() == AgentActivityAdmissionResult.Status.DEFERRED
                && result.retryAtMs() <= nowMs) {
            issues.add("deferred admission does not retry in the future");
        }
        return List.copyOf(issues);
    }

    public static List<String> exitIssues(AgentActivityExitResult result, long nowMs) {
        if (result == null) return List.of("adapter returned no exit result");
        List<String> issues = new ArrayList<>();
        if (result.status() == AgentActivityExitResult.Status.DEFERRED
                && result.retryAtMs() <= nowMs) {
            issues.add("deferred exit does not retry in the future");
        }
        if ((result.status() == AgentActivityExitResult.Status.DEFERRED
                || result.status() == AgentActivityExitResult.Status.REJECTED)
                && result.reason().isBlank()) {
            issues.add("non-successful exit has no reason");
        }
        return List.copyOf(issues);
    }
}
