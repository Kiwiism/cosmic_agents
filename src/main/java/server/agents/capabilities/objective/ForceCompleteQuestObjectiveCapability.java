package server.agents.capabilities.objective;

import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.quest.AmherstScopePolicy;
import server.agents.capabilities.quest.MapleIslandSouthperryQuestCatalog;
import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.integration.PrimitiveCapabilityGateway;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/**
 * Explicit plan override for a quest that cannot follow its original client-only acquisition path.
 */
public final class ForceCompleteQuestObjectiveCapability
        implements AgentExecutableCapability<ForceCompleteQuestObjectiveCapability.Command> {
    private static final long MIN_CASH_SHOP_VISIT_MS = 2_500L;
    private static final long MAX_CASH_SHOP_VISIT_MS = 5_000L;
    private static final long SAFETY_RESTORE_GRACE_MS = 2_000L;
    private static final long MIN_RETURN_LANDING_SETTLE_MS = 3_000L;
    public record Command(String objectiveId, int mapId, int questId, int npcId)
            implements AgentCapabilityCommand {
        public Command {
            if (objectiveId == null || objectiveId.isBlank() || mapId <= 0 || questId <= 0 || npcId <= 0) {
                throw new IllegalArgumentException("objective, map, quest, and NPC ids are required");
            }
        }

        @Override
        public String type() {
            return "force-complete-quest-objective";
        }
    }

    private final AmherstObjectiveCapabilitySupport support;
    private final AmherstScopePolicy scopePolicy;
    private final AmherstNpcInteractionDelay npcInteractionDelay;
    private final LongSupplier absenceDelayMs;

    public ForceCompleteQuestObjectiveCapability(PrimitiveCapabilityGateway gateway,
                                                 AmherstScopePolicy scopePolicy,
                                                 AmherstNpcInteractionDelay npcInteractionDelay) {
        this(gateway, scopePolicy, npcInteractionDelay,
                () -> ThreadLocalRandom.current().nextLong(
                        MIN_CASH_SHOP_VISIT_MS, MAX_CASH_SHOP_VISIT_MS + 1L));
    }

    ForceCompleteQuestObjectiveCapability(PrimitiveCapabilityGateway gateway,
                                          AmherstScopePolicy scopePolicy,
                                          AmherstNpcInteractionDelay npcInteractionDelay,
                                          LongSupplier absenceDelayMs) {
        this.npcInteractionDelay = npcInteractionDelay == null
                ? AmherstNpcInteractionDelay.NONE : npcInteractionDelay;
        this.support = new AmherstObjectiveCapabilitySupport(gateway, scopePolicy, this.npcInteractionDelay);
        this.scopePolicy = scopePolicy;
        this.absenceDelayMs = absenceDelayMs;
    }

    @Override
    public String id() {
        return "force-complete-quest-objective";
    }

    @Override
    public AgentCapabilityStep tick(AgentCapabilityContext context, Command command) {
        AgentCapabilityStep failure = support.propagateChildFailure(context);
        if (failure != null) {
            return failure;
        }
        if (support.gateway().questStatus(context.agent(), command.questId()) == 2) {
            return AgentCapabilityStep.terminal(AmherstObjectiveCapabilitySupport.success(
                    command.objectiveId(), "forced quest completion verified"));
        }

        var scope = scopePolicy.checkQuest(command.questId());
        if (!scope.allowed()) {
            return AmherstObjectiveCapabilitySupport.blocked(scope.status(), scope.reason());
        }

        int phase = context.memory().intValue("phase", 0);
        if (phase == 0) {
            AgentCapabilityStep approach = support.approachNpc(context, command.mapId(), command.npcId());
            if (approach != null) {
                return approach;
            }
            if (support.waitForNpcInteraction(context, 0)) {
                return AgentCapabilityStep.running("waiting briefly before NPC interaction", true);
            }
            context.memory().putInt("phase", 1);
            return AgentCapabilityStep.handoff(
                    support.talk(command.mapId(), command.npcId(), command.questId()),
                    "forced quest completion requests NPC interaction");
        }

        if (phase == 1) {
            if (command.questId() != MapleIslandSouthperryQuestCatalog.YOONA_SHOPPING_GUIDE_QUEST_ID) {
                context.memory().putInt("phase", 3);
                return AgentCapabilityStep.running("special quest completion authorized", false);
            }
            long delayMs = MapleIslandObjectiveRandomnessRuntime.sampleCashShopVisitDelayMs(
                            context.entry(), MIN_CASH_SHOP_VISIT_MS, MAX_CASH_SHOP_VISIT_MS)
                    .orElseGet(() -> Math.max(0L, absenceDelayMs.getAsLong()));
            if (!support.gateway().beginFieldAbsence(
                    context.agent(), delayMs + SAFETY_RESTORE_GRACE_MS)) {
                return AgentCapabilityStep.retry("Agent could not begin the simulated Cash Shop visit");
            }
            context.memory().putLong("returnAtMs", context.nowMs() + delayMs);
            context.memory().putInt("phase", 2);
            return AgentCapabilityStep.running("Agent is visiting the Cash Shop", true);
        }
        if (phase == 2) {
            if (context.nowMs() < context.memory().longValue("returnAtMs", context.nowMs())) {
                return AgentCapabilityStep.running("Agent is visiting the Cash Shop", true);
            }
            if (!support.gateway().endFieldAbsence(context.agent())) {
                return AgentCapabilityStep.retry("Agent could not return from the simulated Cash Shop visit");
            }
            context.memory().putLong("returnSettleDelayMs",
                    MIN_RETURN_LANDING_SETTLE_MS + Math.max(0L, npcInteractionDelay.nextDelayMs()));
            context.memory().putLong("returnGroundedAtMs", -1L);
            context.memory().putInt("phase", 3);
            return AgentCapabilityStep.running("Agent returned from the Cash Shop", false);
        }
        if (phase == 3
                && command.questId() == MapleIslandSouthperryQuestCatalog.YOONA_SHOPPING_GUIDE_QUEST_ID) {
            if (!settledAfterCashShopReturn(context)) {
                context.memory().putLong("returnGroundedAtMs", -1L);
                return AgentCapabilityStep.running("waiting to land after returning from the Cash Shop", false);
            }
            long groundedAtMs = context.memory().longValue("returnGroundedAtMs", -1L);
            if (groundedAtMs < 0L) {
                context.memory().putLong("returnGroundedAtMs", context.nowMs());
                return AgentCapabilityStep.running("settling after returning from the Cash Shop", false);
            }
            long settleDelayMs = context.memory().longValue(
                    "returnSettleDelayMs", MIN_RETURN_LANDING_SETTLE_MS);
            if (context.nowMs() - groundedAtMs < settleDelayMs) {
                return AgentCapabilityStep.running("settling after returning from the Cash Shop", false);
            }
            context.memory().putInt("phase", 4);
        }
        if (!support.gateway().forceCompleteQuest(context.agent(), command.questId(), command.npcId())) {
            return AgentCapabilityStep.retry("forced quest completion was not accepted");
        }
        return AgentCapabilityStep.running("quest force-completed; verifying live quest state");
    }

    private boolean settledAfterCashShopReturn(AgentCapabilityContext context) {
        return support.gateway().grounded(context.agent())
                && !AgentMovementStateRuntime.inAir(context.entry())
                && !AgentMovementStateRuntime.climbing(context.entry());
    }
}
