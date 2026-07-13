package server.agents.runtime.async;

import server.agents.runtime.scheduler.AgentSessionId;

/** Immutable result delivered back to the owning Agent mailbox. */
public record AgentAsyncCompletion<T>(AgentSessionId sessionId,
                                      long requestId,
                                      AgentAsyncWorkKind workKind,
                                      String requestKey,
                                      Status status,
                                      T result,
                                      Throwable failure,
                                      long durationNs) {
    public enum Status {
        SUCCEEDED,
        FAILED,
        TIMED_OUT
    }

    public boolean succeeded() {
        return status == Status.SUCCEEDED;
    }
}
