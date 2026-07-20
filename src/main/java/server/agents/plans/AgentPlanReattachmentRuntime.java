package server.agents.plans;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.objectives.AgentObjectiveDefinition;
import server.agents.objectives.AgentObjectiveKernel;
import server.agents.plans.amherst.AgentAmherstPlanRuntime;
import server.agents.plans.amherst.AmherstPlanObserver;
import server.agents.plans.amherst.AmherstPlanValidationException;
import server.agents.plans.mapleisland.AgentMapleIslandPlanRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.io.IOException;

/** Recreates transient plan runners from durable objective identity after relog/restart. */
public final class AgentPlanReattachmentRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentPlanReattachmentRuntime.class);
    private static final long RETRY_DELAY_MS = 10_000L;
    private static final String MAPLE_OBJECTIVE_TYPE = "maple-island-progression";

    private AgentPlanReattachmentRuntime() {
    }

    public static boolean reattachIfNeeded(AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null || agent == null || entry.amherstPlanExecutionState().active()
                || entry.amherstPlanExecutionState().completed()) {
            return false;
        }
        AgentObjectiveDefinition active = AgentObjectiveKernel.active(entry);
        if (active == null || !MAPLE_OBJECTIVE_TYPE.equals(active.type())) {
            return false;
        }
        AgentPlanAttachmentState state = entry.capabilityStates().require(AgentPlanAttachmentState.STATE_KEY);
        if (!state.ready(active.objectiveId(), nowMs)) {
            return false;
        }
        try {
            start(active.objectiveId(), entry, agent, nowMs);
            state.attached(active.objectiveId(), nowMs + RETRY_DELAY_MS);
            log.info("Reattached Agent '{}' to durable objective {}", agent.getName(), active.objectiveId());
            return true;
        } catch (IOException | AmherstPlanValidationException | RuntimeException failure) {
            state.failed(active.objectiveId(), nowMs + RETRY_DELAY_MS);
            log.warn("Could not reattach Agent '{}' to {}", agent.getName(), active.objectiveId(), failure);
            return false;
        }
    }

    static ResumeKind resumeKind(String objectiveId) {
        String planId = objectiveId != null && objectiveId.startsWith("plan:")
                ? objectiveId.substring("plan:".length()) : "";
        return switch (planId) {
            case "maple-island-amherst-subphase" -> ResumeKind.AMHERST;
            case "maple-island-southperry-mvp" -> ResumeKind.SOUTHPERRY;
            case "maple-island-full-mvp" -> ResumeKind.FULL_MAPLE_ISLAND;
            default -> ResumeKind.UNSUPPORTED;
        };
    }

    private static void start(String objectiveId,
                              AgentRuntimeEntry entry,
                              Character agent,
                              long nowMs) throws IOException, AmherstPlanValidationException {
        switch (resumeKind(objectiveId)) {
            case AMHERST -> AgentAmherstPlanRuntime.startAuto(entry, agent, nowMs, AmherstPlanObserver.NONE);
            case SOUTHPERRY -> AgentMapleIslandPlanRuntime.startAuto(
                    entry, agent, nowMs, AmherstPlanObserver.NONE);
            case FULL_MAPLE_ISLAND -> AgentMapleIslandPlanRuntime.startFullAuto(
                    entry, agent, nowMs, AmherstPlanObserver.NONE);
            case UNSUPPORTED -> throw new IOException("unsupported durable plan objective " + objectiveId);
        }
    }

    enum ResumeKind {
        AMHERST,
        SOUTHPERRY,
        FULL_MAPLE_ISLAND,
        UNSUPPORTED
    }
}
