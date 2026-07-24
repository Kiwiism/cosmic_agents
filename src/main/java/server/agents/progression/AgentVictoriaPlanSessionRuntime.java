package server.agents.progression;

import client.Character;
import server.agents.capabilities.townlife.AgentTownLifeRuntime;
import server.agents.runtime.AgentForegroundPauseRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Compatibility foreground owner for Victoria sessions created before the
 * universal plan executor became the sole progression-plan lifecycle.
 *
 * <p>New assignments must use {@code AgentUniversalPlanRuntime}. This runtime
 * remains only so an old in-memory/checkpoint session can finish safely.</p>
 */
@Deprecated
public final class AgentVictoriaPlanSessionRuntime {
    private AgentVictoriaPlanSessionRuntime() {
    }

    public static void startFirstJob(AgentRuntimeEntry entry, Character agent) {
        start(entry, agent, AgentVictoriaPlanSessionState.Plan.FIRST_JOB);
    }

    public static void startTraining(AgentRuntimeEntry entry, Character agent) {
        start(entry, agent, AgentVictoriaPlanSessionState.Plan.TRAINING);
    }

    public static boolean active(AgentRuntimeEntry entry) {
        return entry != null && entry.capabilityStates()
                .find(AgentVictoriaPlanSessionState.STATE_KEY)
                .map(AgentVictoriaPlanSessionState::active)
                .orElse(false);
    }

    public static AgentVictoriaPlanSessionState.Plan plan(AgentRuntimeEntry entry) {
        return entry == null ? AgentVictoriaPlanSessionState.Plan.NONE
                : entry.capabilityStates().find(AgentVictoriaPlanSessionState.STATE_KEY)
                .map(AgentVictoriaPlanSessionState::plan)
                .orElse(AgentVictoriaPlanSessionState.Plan.NONE);
    }

    public static void stop(AgentRuntimeEntry entry) {
        if (entry != null) {
            entry.capabilityStates().find(AgentVictoriaPlanSessionState.STATE_KEY)
                    .ifPresent(AgentVictoriaPlanSessionState::stop);
        }
    }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, long wallNowMs) {
        if (entry == null || agent == null) {
            return false;
        }
        AgentVictoriaPlanSessionState state = entry.capabilityStates()
                .require(AgentVictoriaPlanSessionState.STATE_KEY);
        AgentVictoriaPlanSessionState.Plan plan = state.plan();
        if (plan == AgentVictoriaPlanSessionState.Plan.NONE) {
            return false;
        }
        long planNowMs = AgentForegroundPauseRuntime.effectiveNow(entry, wallNowMs);
        boolean consumed = switch (plan) {
            case FIRST_JOB -> AgentFirstJobJourneyRuntime.tick(entry, agent, planNowMs);
            case TRAINING -> AgentVictoriaTrainingObjectiveRuntime.tick(entry, agent, planNowMs);
            case NONE -> false;
        };
        if (finished(entry, plan)) {
            state.stop();
        }
        return consumed;
    }

    private static void start(AgentRuntimeEntry entry,
                              Character agent,
                              AgentVictoriaPlanSessionState.Plan plan) {
        if (entry == null || agent == null) {
            throw new IllegalArgumentException("A live Agent session is required");
        }
        if (AgentTownLifeRuntime.active(entry)) {
            AgentTownLifeRuntime.stop(entry, agent);
        }
        AgentForegroundPauseRuntime.reset(entry);
        entry.capabilityStates().require(AgentVictoriaPlanSessionState.STATE_KEY).start(plan);
    }

    private static boolean finished(AgentRuntimeEntry entry,
                                    AgentVictoriaPlanSessionState.Plan plan) {
        if (plan == AgentVictoriaPlanSessionState.Plan.TRAINING) {
            return !entry.capabilityStates().require(AgentVictoriaTrainingState.STATE_KEY).active();
        }
        AgentCareerProgressionState career = entry.capabilityStates()
                .require(AgentCareerProgressionState.STATE_KEY);
        return career.bundle() == null
                || career.stage() == AgentCareerProgressionState.Stage.COMPLETE
                || career.stage() == AgentCareerProgressionState.Stage.BLOCKED;
    }
}
