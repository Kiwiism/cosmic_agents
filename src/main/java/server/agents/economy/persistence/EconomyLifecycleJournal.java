package server.agents.economy.persistence;

import server.agents.economy.activity.FarmSessionOutcome;
import server.agents.economy.activity.FarmSessionPlan;
import server.agents.economy.session.CommerceParticipant;
import server.agents.economy.scenario.EconomyRunCoordinator;
import server.agents.economy.session.EconomySessionPort;

import java.time.Instant;
import java.util.UUID;

public interface EconomyLifecycleJournal {
    void admitted(UUID runId, CommerceParticipant profile, Instant logicalAt);
    void activityStarted(UUID runId, FarmSessionPlan plan);
    void activityCompleted(UUID runId, FarmSessionOutcome outcome);
    void stateChanged(UUID runId, String agentId, EconomyRunCoordinator.Status state,
                      String activityId, Instant logicalAt);
    default void presence(UUID runId, String agentId, EconomySessionPort.Presence presence,
                          String reason, Instant logicalAt) { }
    default void sessionEvent(UUID runId, String agentId, UUID requestId, UUID sessionId,
                              String eventKind, Instant logicalAt, String reason,
                              Instant retryAt, Instant expiresAt) { }
}
