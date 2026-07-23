package server.agents.plans.amherst;

import client.Character;
import server.agents.capabilities.behavior.AgentPioRelaxerInterludeRuntime;
import server.agents.capabilities.runtime.AgentCapabilityRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentForegroundPauseRuntime;
import server.agents.profiles.AgentBehaviorProfileRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.io.IOException;
import java.nio.file.Path;

public final class AgentAmherstPlanRuntime {
    private static final Path DEFAULT_CARD_PATH = Path.of(
            "docs", "agents", "plans", "maple-island-amherst-subphase.plan.json");

    private AgentAmherstPlanRuntime() {
    }

    public static void startDefault(AgentRuntimeEntry entry, Character agent, long nowMs) throws IOException,
            AmherstPlanValidationException {
        AgentPioRelaxerInterludeRuntime.cancel(entry, agent, nowMs);
        AgentForegroundPauseRuntime.reset(entry);
        AgentBehaviorProfileRuntime.assignMapleIslandQuester(entry);
        AmherstPlanRuntimeRunner runner = defaultRunner(defaultCard(), entry);
        runner.start(entry, agent, nowMs);
    }

    public static void startManual(AgentRuntimeEntry entry,
                                   Character agent,
                                   long nowMs,
                                   AmherstPlanObserver observer) throws IOException, AmherstPlanValidationException {
        AgentPioRelaxerInterludeRuntime.cancel(entry, agent, nowMs);
        AgentForegroundPauseRuntime.reset(entry);
        AgentBehaviorProfileRuntime.assignMapleIslandQuester(entry);
        AmherstPlanRuntimeRunner runner = defaultRunner(defaultCard(), entry);
        runner.start(entry, agent, nowMs, AmherstPlanExecutionMode.MANUAL, observer);
    }

    public static void startAuto(AgentRuntimeEntry entry,
                                 Character agent,
                                 long nowMs,
                                 AmherstPlanObserver observer) throws IOException, AmherstPlanValidationException {
        AgentPioRelaxerInterludeRuntime.cancel(entry, agent, nowMs);
        AgentForegroundPauseRuntime.reset(entry);
        AgentBehaviorProfileRuntime.assignMapleIslandQuester(entry);
        AmherstPlanRuntimeRunner runner = defaultRunner(defaultCard(), entry);
        runner.start(entry, agent, nowMs, AmherstPlanExecutionMode.AUTO, observer);
    }

    public static AmherstPlanCard defaultCard() throws IOException, AmherstPlanValidationException {
        return new AmherstPlanCardLoader().load(DEFAULT_CARD_PATH);
    }

    public static FileAmherstPlanProgressStore defaultStore() {
        return FileAmherstPlanProgressStore.runtimeDefault();
    }

    public static boolean tickGate(AgentRuntimeEntry entry, Character agent, long nowMs) {
        long planNowMs = AgentForegroundPauseRuntime.effectiveNow(entry, nowMs);
        AmherstPlanExecutionState state = entry.amherstPlanExecutionState();
        AmherstPlanRuntimeRunner runner;
        synchronized (state) {
            runner = state.runner;
        }
        if (runner != null && runner.tick(entry, agent, planNowMs)) {
            return true;
        }
        if (state.completed()) {
            if (agent.getChair() >= 0) {
                return true;
            }
            if (AgentSouthperryPostPlanService.tick(entry, agent, planNowMs)) {
                return true;
            }
        }
        return AgentCapabilityRuntime.tick(entry, agent, planNowMs);
    }

    public static void cancel(AgentRuntimeEntry entry) {
        AmherstPlanExecutionState state = entry.amherstPlanExecutionState();
        AmherstPlanRuntimeRunner runner;
        synchronized (state) {
            runner = state.runner;
        }
        if (runner != null) {
            runner.cancel(entry);
        }
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        AgentPioRelaxerInterludeRuntime.cancel(entry, agent, System.currentTimeMillis());
        if (agent != null && agent.getChair() <= 0) {
            MapleIslandRelaxerSpotReservationRuntime.release(agent.getId());
        }
    }

    public static boolean requestNext(AgentRuntimeEntry entry) {
        AmherstPlanExecutionState state = entry.amherstPlanExecutionState();
        AmherstPlanRuntimeRunner runner;
        synchronized (state) {
            runner = state.runner;
        }
        return runner != null && runner.requestAdvance(entry);
    }

    public static boolean clearSession(AgentRuntimeEntry entry) {
        if (entry.capabilityRuntimeState().hasActiveCapability()) {
            return false;
        }
        AmherstPlanExecutionState state = entry.amherstPlanExecutionState();
        synchronized (state) {
            state.clearRuntime();
        }
        return true;
    }

    private static AmherstPlanRuntimeRunner defaultRunner(AmherstPlanCard card, AgentRuntimeEntry entry) {
        return new AmherstPlanRuntimeRunner(card,
                defaultStore(), new AmherstPlanProgressService(),
                new AmherstObjectiveReconciler(), new AmherstObjectiveHandlerRegistry(
                        server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime.gateway(),
                        server.agents.capabilities.objective.AmherstNpcInteractionDelay.profile(entry),
                        new server.agents.capabilities.quest.AmherstScopePolicy(), entry),
                AmherstObjectiveDelay.profile(entry));
    }

}
