package server.agents.economy.persistence;

import server.agents.economy.activity.FarmSessionOutcome;
import server.agents.economy.activity.FarmSessionPlan;
import server.agents.economy.scenario.EconomyAgentProfile;
import server.agents.economy.scenario.EconomyRunCoordinator;

import java.time.Instant;
import java.util.UUID;

public interface EconomyLifecycleJournal {
    void admitted(UUID runId, EconomyAgentProfile profile, Instant logicalAt);
    void activityStarted(UUID runId, FarmSessionPlan plan);
    void activityCompleted(UUID runId, FarmSessionOutcome outcome);
    void stateChanged(UUID runId, String agentId, EconomyRunCoordinator.Status state,
                      String activityId, Instant logicalAt);
}
