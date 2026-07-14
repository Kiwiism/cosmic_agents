package server.agents.runtime.scheduler;

import server.agents.runtime.AgentRuntimeEntry;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

/** Public profile/snapshot/release boundary over the owning schedule handle. */
public final class AgentQuiescenceService {
    private AgentQuiescenceService() {
    }

    public static CompletionStage<AgentQuiescenceToken> quiesce(
            AgentRuntimeEntry entry,
            AgentQuiescenceReason reason) {
        return AgentScheduler.quiesce(entry, reason);
    }

    public static CompletionStage<AgentQuiescenceToken> quiesce(
            AgentRuntimeEntry entry,
            AgentQuiescenceReason reason,
            Duration timeout) {
        return AgentScheduler.quiesce(entry, reason, timeout);
    }

    public static boolean resume(AgentRuntimeEntry entry, AgentQuiescenceToken token) {
        return AgentScheduler.resume(entry, token);
    }

    public static void requireValidToken(AgentRuntimeEntry entry, AgentQuiescenceToken token) {
        if (!AgentScheduler.validatesQuiescence(entry, token)) {
            throw new AgentQuiescenceException(
                    AgentQuiescenceException.Reason.INVALID_TOKEN,
                    "A valid generation-bound Agent quiescence token is required");
        }
    }
}
