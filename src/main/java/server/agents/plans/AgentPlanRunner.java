package server.agents.plans;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

public interface AgentPlanRunner {
    boolean start(AgentRuntimeEntry entry,
                  Character agent,
                  String planId,
                  AgentPlanStartRequest request,
                  long nowMs);

    boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs);

    boolean cancel(AgentRuntimeEntry entry, Character agent, String reason, long nowMs);

    boolean reattach(AgentRuntimeEntry entry, Character agent, long nowMs);

    boolean startAvailableSuccessor(AgentRuntimeEntry entry,
                                    Character agent,
                                    String planId,
                                    AgentPlanStartRequest request,
                                    long nowMs);
}
