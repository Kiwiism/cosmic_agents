package server.agents.plans;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.objectives.AgentObjectiveDefinition;
import server.agents.objectives.AgentObjectiveAttachment;
import server.agents.objectives.AgentObjectiveHandlerRegistry;
import server.agents.objectives.AgentObjectiveKernel;
import server.agents.objectives.AgentObjectiveStatus;
import server.agents.plans.amherst.AgentAmherstPlanRuntime;
import server.agents.plans.amherst.AmherstPlanObserver;
import server.agents.plans.amherst.AmherstPlanValidationException;
import server.agents.plans.mapleisland.AgentMapleIslandPlanRuntime;
import server.agents.progression.AgentFirstJobJourneyRuntime;
import server.agents.progression.AgentVictoriaTrainingObjectiveRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.io.IOException;
import java.util.Map;

/** Recreates transient plan runners from durable objective identity after relog/restart. */
public final class AgentPlanReattachmentRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentPlanReattachmentRuntime.class);
    private static final long RETRY_DELAY_MS = 10_000L;
    private static final String MAPLE_OBJECTIVE_TYPE = "maple-island-progression";
    private static final AgentObjectiveHandlerRegistry HANDLERS = new AgentObjectiveHandlerRegistry(Map.of(
            MAPLE_OBJECTIVE_TYPE, AgentPlanReattachmentRuntime::attachMapleIsland,
            AgentFirstJobJourneyRuntime.OBJECTIVE_TYPE, AgentFirstJobJourneyRuntime::reattach,
            AgentVictoriaTrainingObjectiveRuntime.OBJECTIVE_TYPE,
            AgentVictoriaTrainingObjectiveRuntime::reattach,
            "maintenance.resupply", AgentPlanReattachmentRuntime::reconcileInterruptedResupply));

    private AgentPlanReattachmentRuntime() {
    }

    public static boolean reattachIfNeeded(AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null || agent == null) {
            return false;
        }
        AgentObjectiveDefinition active = AgentObjectiveKernel.active(entry);
        if (active == null || HANDLERS.handlerFor(active.type()).isEmpty()) {
            return false;
        }
        AgentPlanAttachmentState state = entry.capabilityStates().require(AgentPlanAttachmentState.STATE_KEY);
        if (!state.ready(active.objectiveId(), nowMs)) {
            return false;
        }
        try {
            AgentObjectiveAttachment result = HANDLERS.reconcileAndAttach(entry, agent, active, nowMs);
            state.attached(active.objectiveId());
            if (result == AgentObjectiveAttachment.ATTACHED) {
                log.info("Reattached Agent '{}' to durable objective {}", agent.getName(), active.objectiveId());
                return true;
            }
            return false;
        } catch (Exception failure) {
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

    private static AgentObjectiveAttachment attachMapleIsland(AgentRuntimeEntry entry,
                                                               Character agent,
                                                               AgentObjectiveDefinition objective,
                                                               long nowMs)
            throws IOException, AmherstPlanValidationException {
        if (entry.amherstPlanExecutionState().active() || entry.amherstPlanExecutionState().completed()) {
            return AgentObjectiveAttachment.ALREADY_ATTACHED;
        }
        switch (resumeKind(objective.objectiveId())) {
            case AMHERST -> AgentAmherstPlanRuntime.startAuto(entry, agent, nowMs, AmherstPlanObserver.NONE);
            case SOUTHPERRY -> AgentMapleIslandPlanRuntime.startAuto(
                    entry, agent, nowMs, AmherstPlanObserver.NONE);
            case FULL_MAPLE_ISLAND -> AgentMapleIslandPlanRuntime.startFullAuto(
                    entry, agent, nowMs, AmherstPlanObserver.NONE);
            case UNSUPPORTED -> throw new IOException(
                    "unsupported durable plan objective " + objective.objectiveId());
        }
        return AgentObjectiveAttachment.ATTACHED;
    }

    private static AgentObjectiveAttachment reconcileInterruptedResupply(
            AgentRuntimeEntry entry,
            Character agent,
            AgentObjectiveDefinition objective,
            long nowMs) {
        AgentObjectiveKernel.finishAndResume(entry, objective.objectiveId(), AgentObjectiveStatus.FAILED,
                "relog discarded the transient shop transaction; supply planning will reassess", nowMs);
        return AgentObjectiveAttachment.TERMINAL;
    }

    static AgentObjectiveHandlerRegistry handlers() {
        return HANDLERS;
    }

    enum ResumeKind {
        AMHERST,
        SOUTHPERRY,
        FULL_MAPLE_ISLAND,
        UNSUPPORTED
    }
}
