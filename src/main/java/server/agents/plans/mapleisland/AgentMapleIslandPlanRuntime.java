package server.agents.plans.mapleisland;

import client.Character;
import com.fasterxml.jackson.databind.ObjectMapper;
import server.agents.capabilities.objective.AmherstNpcInteractionDelay;
import server.agents.capabilities.quest.AmherstScopePolicy;
import server.agents.plans.amherst.AgentAmherstPlanRuntime;
import server.agents.plans.amherst.AmherstObjectiveDelay;
import server.agents.plans.amherst.AmherstObjectiveHandlerRegistry;
import server.agents.plans.amherst.AmherstObjectiveReconciler;
import server.agents.plans.amherst.AmherstPlanCard;
import server.agents.plans.amherst.AmherstPlanCardLoader;
import server.agents.plans.amherst.AmherstPlanExecutionMode;
import server.agents.plans.amherst.AmherstPlanObserver;
import server.agents.plans.amherst.AmherstPlanProgressService;
import server.agents.plans.amherst.AmherstPlanRuntimeRunner;
import server.agents.plans.amherst.AmherstPlanValidationException;
import server.agents.plans.amherst.AmherstPlanValidator;
import server.agents.plans.amherst.FileAmherstPlanProgressStore;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.profiles.AgentBehaviorProfileRuntime;

import java.io.IOException;
import java.nio.file.Path;

public final class AgentMapleIslandPlanRuntime {
    private static final Path DEFAULT_CARD_PATH = Path.of(
            "docs", "agents", "plans", "maple-island-southperry-mvp.plan.json");

    private AgentMapleIslandPlanRuntime() {
    }

    public static void startManual(AgentRuntimeEntry entry,
                                   Character agent,
                                   long nowMs,
                                   AmherstPlanObserver observer) throws IOException, AmherstPlanValidationException {
        AgentBehaviorProfileRuntime.assignMapleIslandQuester(entry);
        defaultRunner(defaultCard(), entry).start(
                entry, agent, nowMs, AmherstPlanExecutionMode.MANUAL, observer);
    }

    public static void startAuto(AgentRuntimeEntry entry,
                                 Character agent,
                                 long nowMs,
                                 AmherstPlanObserver observer) throws IOException, AmherstPlanValidationException {
        AgentBehaviorProfileRuntime.assignMapleIslandQuester(entry);
        defaultRunner(defaultCard(), entry).start(
                entry, agent, nowMs, AmherstPlanExecutionMode.AUTO, observer);
    }

    public static AmherstPlanCard defaultCard() throws IOException, AmherstPlanValidationException {
        return new AmherstPlanCardLoader(new ObjectMapper(), AmherstPlanValidator.southperry())
                .load(DEFAULT_CARD_PATH);
    }

    public static FileAmherstPlanProgressStore defaultStore() {
        return FileAmherstPlanProgressStore.runtimeDefault();
    }

    public static boolean clearSession(AgentRuntimeEntry entry) {
        return AgentAmherstPlanRuntime.clearSession(entry);
    }

    public static boolean requestNext(AgentRuntimeEntry entry) {
        return AgentAmherstPlanRuntime.requestNext(entry);
    }

    private static AmherstPlanRuntimeRunner defaultRunner(AmherstPlanCard card, AgentRuntimeEntry entry) {
        AmherstScopePolicy scopePolicy = AmherstScopePolicy.southperry();
        return new AmherstPlanRuntimeRunner(card,
                defaultStore(), new AmherstPlanProgressService(),
                new AmherstObjectiveReconciler(),
                new AmherstObjectiveHandlerRegistry(
                        server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime.gateway(),
                        AmherstNpcInteractionDelay.profile(entry), scopePolicy),
                AmherstObjectiveDelay.profile(entry));
    }
}
