package server.agents.capabilities.objective;

import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.quest.AmherstScopePolicy;
import server.agents.capabilities.navigation.AgentPortalRoutePolicy;
import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.integration.PrimitiveCapabilityGateway;


/**
 * Explicit plan override for a quest that cannot follow its original client-only acquisition path.
 */
public final class ForceCompleteQuestObjectiveCapability
        implements AgentExecutableCapability<ForceCompleteQuestObjectiveCapability.Command> {
    public record FieldAbsence(long durationMs,
                               long safetyRestoreGraceMs,
                               long landingSettleMs,
                               String activity) {
        public FieldAbsence {
            if (durationMs < 0L || safetyRestoreGraceMs < 0L || landingSettleMs < 0L
                    || activity == null || activity.isBlank()) {
                throw new IllegalArgumentException("field absence parameters are required");
            }
        }
    }

    public record Command(String objectiveId, int mapId, int questId, int npcId,
                          FieldAbsence fieldAbsence, AgentNpcInteractionPlacementData placement)
            implements AgentCapabilityCommand {
        public Command(String objectiveId, int mapId, int questId, int npcId) {
            this(objectiveId, mapId, questId, npcId, null,
                    AgentNpcInteractionPlacementData.direct(AmherstObjectiveCapabilitySupport.NPC_RANGE_PX));
        }
        public Command(String objectiveId, int mapId, int questId, int npcId, FieldAbsence fieldAbsence) {
            this(objectiveId, mapId, questId, npcId, fieldAbsence,
                    AgentNpcInteractionPlacementData.direct(AmherstObjectiveCapabilitySupport.NPC_RANGE_PX));
        }

        public Command {
            if (objectiveId == null || objectiveId.isBlank() || mapId <= 0 || questId <= 0 || npcId <= 0) {
                throw new IllegalArgumentException("objective, map, quest, and NPC ids are required");
            }
            placement = placement == null
                    ? AgentNpcInteractionPlacementData.direct(AmherstObjectiveCapabilitySupport.NPC_RANGE_PX)
                    : placement;
        }

        @Override
        public String type() {
            return "force-complete-quest-objective";
        }
    }

    private final AmherstObjectiveCapabilitySupport support;
    private final AmherstScopePolicy scopePolicy;
    private final AmherstNpcInteractionDelay npcInteractionDelay;

    public ForceCompleteQuestObjectiveCapability(PrimitiveCapabilityGateway gateway,
                                                 AmherstScopePolicy scopePolicy,
                                                 AmherstNpcInteractionDelay npcInteractionDelay) {
        this(gateway, scopePolicy, npcInteractionDelay, AgentPortalRoutePolicy.DIRECT);
    }

    public ForceCompleteQuestObjectiveCapability(PrimitiveCapabilityGateway gateway,
                                                 AmherstScopePolicy scopePolicy,
                                                 AmherstNpcInteractionDelay npcInteractionDelay,
                                                 AgentPortalRoutePolicy routePolicy) {
        this.npcInteractionDelay = npcInteractionDelay == null
                ? AmherstNpcInteractionDelay.NONE : npcInteractionDelay;
        this.support = new AmherstObjectiveCapabilitySupport(
                gateway, scopePolicy, this.npcInteractionDelay, routePolicy);
        this.scopePolicy = scopePolicy;
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
            AgentCapabilityStep approach = support.approachNpc(
                    context, command.mapId(), command.npcId(), command.placement());
            if (approach != null) {
                return approach;
            }
            if (support.waitForNpcInteraction(context, 0, 0,
                    command.placement().distinguishInteractionStages())) {
                return AgentCapabilityStep.running("waiting briefly before NPC interaction", true);
            }
            context.memory().putInt("phase", 1);
            return AgentCapabilityStep.handoff(
                    support.talk(command.mapId(), command.npcId(), command.questId()),
                    "forced quest completion requests NPC interaction");
        }

        if (phase == 1) {
            FieldAbsence fieldAbsence = command.fieldAbsence();
            if (fieldAbsence == null) {
                context.memory().putInt("phase", 3);
                return AgentCapabilityStep.running("special quest completion authorized", false);
            }
            if (!support.gateway().beginFieldAbsence(
                    context.agent(), fieldAbsence.durationMs() + fieldAbsence.safetyRestoreGraceMs())) {
                return AgentCapabilityStep.retry(
                        "Agent could not begin the simulated " + fieldAbsence.activity() + " visit");
            }
            context.memory().putLong("returnAtMs", context.nowMs() + fieldAbsence.durationMs());
            context.memory().putInt("phase", 2);
            return AgentCapabilityStep.running("Agent is visiting the " + fieldAbsence.activity(), true);
        }
        if (phase == 2) {
            if (context.nowMs() < context.memory().longValue("returnAtMs", context.nowMs())) {
                return AgentCapabilityStep.running(
                        "Agent is visiting the " + command.fieldAbsence().activity(), true);
            }
            if (!support.gateway().endFieldAbsence(context.agent())) {
                return AgentCapabilityStep.retry(
                        "Agent could not return from the simulated "
                                + command.fieldAbsence().activity() + " visit");
            }
            context.memory().putLong("returnSettleDelayMs",
                    command.fieldAbsence().landingSettleMs()
                            + Math.max(0L, npcInteractionDelay.nextDelayMs()));
            context.memory().putLong("returnGroundedAtMs", -1L);
            context.memory().putInt("phase", 3);
            return AgentCapabilityStep.running(
                    "Agent returned from the " + command.fieldAbsence().activity(), false);
        }
        if (phase == 3 && command.fieldAbsence() != null) {
            if (!settledAfterCashShopReturn(context)) {
                context.memory().putLong("returnGroundedAtMs", -1L);
                return AgentCapabilityStep.running("waiting to land after returning from the "
                        + command.fieldAbsence().activity(), false);
            }
            long groundedAtMs = context.memory().longValue("returnGroundedAtMs", -1L);
            if (groundedAtMs < 0L) {
                context.memory().putLong("returnGroundedAtMs", context.nowMs());
                return AgentCapabilityStep.running("settling after returning from the "
                        + command.fieldAbsence().activity(), false);
            }
            long settleDelayMs = context.memory().longValue(
                    "returnSettleDelayMs", command.fieldAbsence().landingSettleMs());
            if (context.nowMs() - groundedAtMs < settleDelayMs) {
                return AgentCapabilityStep.running("settling after returning from the "
                        + command.fieldAbsence().activity(), false);
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
