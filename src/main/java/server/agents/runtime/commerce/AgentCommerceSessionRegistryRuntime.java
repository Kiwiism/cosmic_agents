package server.agents.runtime.commerce;

import server.agents.economy.session.EconomySessionPort;
import server.agents.runtime.activity.session.AgentActivityExitResult;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.session.AgentActivitySessionSnapshot;
import server.agents.runtime.activity.session.AgentActivityTargetPort;
import server.agents.runtime.activity.session.AgentActivityTerminalOutcome;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Live registry for independently admitted per-Agent Commerce owners. */
public final class AgentCommerceSessionRegistryRuntime {
    private static final Map<Integer, AgentCommerceSessionRuntime> SESSIONS =
            new ConcurrentHashMap<>();

    private AgentCommerceSessionRegistryRuntime() {
    }

    public static AgentActivityTargetPort prepare(
            int characterId,
            EconomySessionPort sessions,
            AgentCommerceSessionStore store,
            AgentCommerceVisitRequest request) {
        if (characterId <= 0) {
            throw new IllegalArgumentException("Commerce character id must be positive");
        }
        AgentCommerceSessionRuntime created =
                new AgentCommerceSessionRuntime(sessions, store, request);
        AgentCommerceSessionRuntime previous = SESSIONS.putIfAbsent(characterId, created);
        if (previous != null) {
            throw new IllegalStateException("Commerce visit already prepared for character "
                    + characterId);
        }
        return created;
    }

    public static boolean active(int characterId) {
        AgentCommerceSessionRuntime runtime = SESSIONS.get(characterId);
        return runtime != null && runtime.checkpoint() != null
                && runtime.checkpoint().phase().retainsSession();
    }

    public static boolean tick(int characterId, long nowMs) {
        AgentCommerceSessionRuntime runtime = SESSIONS.get(characterId);
        return runtime != null && runtime.tick(nowMs);
    }

    public static AgentActivitySessionSnapshot snapshot(int characterId, long nowMs) {
        AgentCommerceSessionRuntime runtime = SESSIONS.get(characterId);
        return runtime == null
                ? AgentActivitySessionSnapshot.idle(
                        AgentActivityKind.COMMERCE, Integer.toString(characterId))
                : runtime.snapshot(nowMs);
    }

    public static AgentActivityExitResult requestStop(
            int characterId, String reason, long nowMs, long deadlineMs) {
        AgentCommerceSessionRuntime runtime = SESSIONS.get(characterId);
        return runtime == null
                ? AgentActivityExitResult.released("Commerce session is not active")
                : runtime.requestGracefulExit(reason, nowMs, deadlineMs);
    }

    public static AgentActivityTerminalOutcome terminalOutcome(int characterId, long nowMs) {
        AgentCommerceSessionRuntime runtime = SESSIONS.get(characterId);
        return runtime == null ? null : runtime.terminalOutcome(nowMs);
    }

    public static void acknowledgeTerminal(int characterId) {
        AgentCommerceSessionRuntime runtime = SESSIONS.get(characterId);
        if (runtime == null) {
            throw new IllegalStateException("Commerce visit is not registered");
        }
        runtime.acknowledgeTerminal();
        SESSIONS.remove(characterId, runtime);
    }

    public static void abandonPrepared(int characterId) {
        AgentCommerceSessionRuntime runtime = SESSIONS.get(characterId);
        if (runtime != null && runtime.checkpoint() == null) {
            SESSIONS.remove(characterId, runtime);
        }
    }

    static void clearForTests() {
        SESSIONS.clear();
    }
}
