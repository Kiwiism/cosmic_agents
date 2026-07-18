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
import server.agents.objectives.AgentObjectiveDefinition;
import server.agents.objectives.AgentObjectiveKernel;
import server.agents.objectives.AgentObjectiveSource;
import server.agents.policy.behavior.AgentBehaviorCapability;
import server.agents.policy.behavior.AgentBehaviorRoute;
import server.agents.runtime.AgentBehaviorRoutingRuntime;

import java.io.IOException;
import java.nio.file.Path;

public final class AgentMapleIslandPlanRuntime {
    private static final Path SOUTHPERRY_CARD_PATH = Path.of(
            "docs", "agents", "plans", "maple-island-southperry-mvp.plan.json");
    private static final Path FULL_CARD_PATH = Path.of(
            "docs", "agents", "plans", "maple-island-full-mvp.plan.json");

    private AgentMapleIslandPlanRuntime() {
    }

    public static void startManual(AgentRuntimeEntry entry,
                                   Character agent,
                                   long nowMs,
                                   AmherstPlanObserver observer) throws IOException, AmherstPlanValidationException {
        AgentBehaviorProfileRuntime.assignMapleIslandQuester(entry);
        AmherstPlanCard card = defaultCard();
        prepareObjective(entry, card, nowMs);
        defaultRunner(card, entry, AmherstScopePolicy.southperry()).start(
                entry, agent, nowMs, AmherstPlanExecutionMode.MANUAL, observer);
    }

    public static void startAuto(AgentRuntimeEntry entry,
                                 Character agent,
                                 long nowMs,
                                 AmherstPlanObserver observer) throws IOException, AmherstPlanValidationException {
        AgentBehaviorProfileRuntime.assignMapleIslandQuester(entry);
        AmherstPlanCard card = defaultCard();
        prepareObjective(entry, card, nowMs);
        defaultRunner(card, entry, AmherstScopePolicy.southperry()).start(
                entry, agent, nowMs, AmherstPlanExecutionMode.AUTO, observer);
    }

    public static void startFullManual(AgentRuntimeEntry entry,
                                       Character agent,
                                       long nowMs,
                                       AmherstPlanObserver observer) throws IOException, AmherstPlanValidationException {
        AgentBehaviorProfileRuntime.assignMapleIslandQuester(entry);
        AmherstPlanCard card = fullCard(entry);
        prepareObjective(entry, card, nowMs);
        defaultRunner(card, entry, AmherstScopePolicy.fullMapleIsland()).start(
                entry, agent, nowMs, AmherstPlanExecutionMode.MANUAL, observer);
    }

    public static void startFullAuto(AgentRuntimeEntry entry,
                                     Character agent,
                                     long nowMs,
                                     AmherstPlanObserver observer) throws IOException, AmherstPlanValidationException {
        startFullAuto(entry, agent, nowMs, observer, 0L);
    }

    public static void startFullAuto(AgentRuntimeEntry entry,
                                     Character agent,
                                     long nowMs,
                                     AmherstPlanObserver observer,
                                     long initialObjectiveDelayMs) throws IOException, AmherstPlanValidationException {
        AgentBehaviorProfileRuntime.assignMapleIslandQuester(entry);
        AmherstPlanCard card = fullCard(entry);
        prepareObjective(entry, card, nowMs);
        defaultRunner(card, entry, AmherstScopePolicy.fullMapleIsland()).start(
                entry, agent, nowMs, AmherstPlanExecutionMode.AUTO, observer,
                initialObjectiveDelayMs);
    }

    public static AmherstPlanCard defaultCard() throws IOException, AmherstPlanValidationException {
        return new AmherstPlanCardLoader(new ObjectMapper(), AmherstPlanValidator.southperry())
                .load(SOUTHPERRY_CARD_PATH);
    }

    public static AmherstPlanCard fullCard() throws IOException, AmherstPlanValidationException {
        return new AmherstPlanCardLoader(new ObjectMapper(), AmherstPlanValidator.fullMapleIsland())
                .load(FULL_CARD_PATH);
    }

    private static AmherstPlanCard fullCard(AgentRuntimeEntry entry)
            throws IOException, AmherstPlanValidationException {
        return MapleIslandAmherstQuestOrderPolicy.apply(fullCard(), entry);
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

    private static void prepareObjective(AgentRuntimeEntry entry, AmherstPlanCard card, long nowMs) {
        AgentBehaviorRoute route = AgentBehaviorRoute.reconstructed(
                AgentBehaviorCapability.PROGRESSION, "maple-island-objective-v1");
        AgentBehaviorRoutingRuntime.assign(entry, route);
        AgentObjectiveKernel.start(entry, new AgentObjectiveDefinition(
                "plan:" + card.planId(),
                "maple-island-progression",
                100,
                Long.MAX_VALUE,
                3,
                AgentObjectiveSource.QUEST_PLAN,
                route.primaryVersion(),
                "maple-island:" + entry.sessionGeneration()), nowMs);
    }

    private static AmherstPlanRuntimeRunner defaultRunner(AmherstPlanCard card,
                                                          AgentRuntimeEntry entry,
                                                          AmherstScopePolicy scopePolicy) {
        return new AmherstPlanRuntimeRunner(card,
                defaultStore(), new AmherstPlanProgressService(),
                new AmherstObjectiveReconciler(),
                new AmherstObjectiveHandlerRegistry(
                        server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime.gateway(),
                        AmherstNpcInteractionDelay.profile(entry), scopePolicy, entry),
                AmherstObjectiveDelay.profile(entry));
    }
}
