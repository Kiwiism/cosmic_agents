package server.agents.runtime;

import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationDebugState;
import server.agents.capabilities.navigation.AgentNavigationEdgeState;
import server.agents.capabilities.navigation.AgentNavigationTargetState;
import server.agents.capabilities.navigation.AgentPortalCooldownState;

import server.agents.capabilities.build.AgentBuildState;
import server.agents.capabilities.combat.AgentCombatBuffState;
import server.agents.capabilities.combat.AgentAoeRepositionState;
import server.agents.capabilities.combat.AgentBreakoutState;
import server.agents.capabilities.combat.AgentDegenerateAttackState;
import server.agents.capabilities.combat.AgentRetreatHoldState;
import server.agents.capabilities.combat.AgentGrindWanderState;
import server.agents.capabilities.combat.AgentGrindTargetState;
import server.agents.capabilities.combat.AgentBuffState;
import server.agents.capabilities.combat.AgentCombatCooldownState;
import server.agents.capabilities.combat.AgentCombatSkillCacheState;
import server.agents.capabilities.combat.AgentMobTouchState;
import server.agents.capabilities.combat.AgentDeathState;
import server.agents.capabilities.follow.AgentOwnerMotionState;
import server.agents.capabilities.follow.AgentLeaderActivityState;

import server.agents.capabilities.movement.AgentGroundTravelState;
import server.agents.capabilities.movement.AgentFormationOffsetState;
import server.agents.capabilities.movement.AgentMoveTargetState;
import server.agents.capabilities.movement.AgentPatrolState;
import server.agents.capabilities.movement.AgentFarmAnchorState;
import server.agents.capabilities.movement.AgentMovementBroadcastState;
import server.agents.capabilities.movement.AgentMovementPhysicsCacheState;
import server.agents.capabilities.movement.AgentMovementStuckState;
import server.agents.capabilities.movement.AgentMapTrackingState;
import server.agents.capabilities.movement.AgentAirborneSteeringState;
import server.agents.capabilities.movement.AgentClimbState;
import server.agents.capabilities.movement.AgentDownJumpState;
import server.agents.capabilities.movement.AgentMovementInputState;
import server.agents.capabilities.movement.AgentMovementPhysicsState;
import server.agents.capabilities.movement.AgentSwimIntentState;
import server.agents.capabilities.movement.AgentMovementProfileState;

import client.Character;
import server.maps.MapItem;
import server.agents.commands.AgentMessageQueueState;
import server.agents.commands.AgentReplyChannelState;
import server.agents.capabilities.dialogue.AgentPendingActionState;
import server.agents.capabilities.inventory.AgentInventoryCooldownState;
import server.agents.capabilities.looting.AgentGrindLootState;
import server.agents.capabilities.social.AgentScrollReactionState;
import server.agents.capabilities.shop.AgentShopState;
import server.agents.capabilities.supplies.AgentAmmoSupplyState;
import server.agents.capabilities.supplies.AgentPotionSupplyState;
import server.agents.capabilities.social.airshow.AgentAirshowState;
import server.agents.capabilities.movement.fidget.AgentFidgetState;
import server.agents.capabilities.partyquest.kpq.AgentKpqState;
import server.agents.capabilities.trade.AgentPendingLootOfferState;
import server.agents.capabilities.trade.AgentPendingTradeSequenceState;
import server.agents.capabilities.trade.AgentManualTradeState;
import server.agents.capabilities.trade.AgentOwnerGivenTradeItemState;
import server.agents.capabilities.trade.AgentTradeRetryState;
import server.agents.capabilities.trade.AgentUpgradeOfferState;
import server.agents.plans.AgentScriptTaskQueueState;
import server.agents.plans.AgentScriptRuntimeState;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

public class AgentRuntimeEntry implements AgentRuntimeHandle {
    private final AgentRuntimeIdentityState identityState;
    private final AgentModeState modeState = new AgentModeState();
    private final AgentAirshowState airshowState = new AgentAirshowState();
    private final AgentScheduledTaskState scheduledTaskState;
    private final AgentMovementProfileState movementProfileState = new AgentMovementProfileState();

    public AgentScheduledTaskState scheduledTaskState() {
        return scheduledTaskState;
    }

    public AgentAirshowState airshowState() {
        return airshowState;
    }

    public AgentMovementProfileState movementProfileState() {
        return movementProfileState;
    }

    // Physics
    private final AgentMovementPhysicsState movementPhysicsState = new AgentMovementPhysicsState();

    private final AgentAirborneSteeringState airborneSteeringState = new AgentAirborneSteeringState();
    private final AgentDownJumpState downJumpState = new AgentDownJumpState();
    private final AgentMovementInputState movementInputState = new AgentMovementInputState();
    private final AgentSwimIntentState swimIntentState = new AgentSwimIntentState();
    private final AgentClimbState climbState = new AgentClimbState();

    public AgentGroundTravelState groundTravelState() {
        return movementPhysicsState.groundTravelState();
    }

    public AgentMovementPhysicsState movementPhysicsState() {
        return movementPhysicsState;
    }

    public void setScriptedMovementFrame(Point position,
                                         int velocityX,
                                         int velocityY,
                                         int facingDirection,
                                         boolean inAir,
                                         boolean climbing) {
        movementPhysicsState.setPhysicsPosition(position.x, position.y);
        movementInputState.setVelocity(velocityX, velocityY);
        movementInputState.setFacingDirection(facingDirection);
        movementPhysicsState.setInAir(inAir);
        climbState.setClimbingFlag(climbing);
    }

    // Swim intent — set by movement layer, consumed by physics engine. Movement
    // expresses "what the bot is trying to do"; physics integrates accordingly.
    // Mirrors how the real client only exposes discrete inputs (steer L/R,
    // jump-burst, hold UP/DOWN) — no continuous velocity overrides.
    public AgentSwimIntentState swimIntentState() {
        return swimIntentState;
    }

    // Movement intent — set by movement/fidget layer, consumed by physics engine.
    // Maps to the same left/right key hold used by the real client for both
    // ground walking and air steering. Physics reads this in the active mode:
    //   - Ground: applyGroundMotion() integrates through force/friction model
    //   - Airborne: stepAirborne() applies air steering accel (gated by fixedAirArc)
    // Mutually exclusive by state (inAir vs grounded), so one field suffices.
    public AgentMovementInputState movementInputState() {
        return movementInputState;
    }

    // Rope climbing
    public AgentClimbState climbState() {
        return climbState;
    }

    public AgentAirborneSteeringState airborneSteeringState() {
        return airborneSteeringState;
    }

    public AgentDownJumpState downJumpState() {
        return downJumpState;
    }

    // Grind mode
    private final AgentCombatCooldownState combatCooldownState = new AgentCombatCooldownState();
    private final AgentGrindTargetState grindTargetState = new AgentGrindTargetState();

    public AgentGrindTargetState grindTargetState() {
        return grindTargetState;
    }

    private final AgentCombatSkillCacheState combatSkillCacheState = new AgentCombatSkillCacheState();
    private final AgentCombatBuffState combatBuffState = new AgentCombatBuffState();

    public AgentCombatBuffState combatBuffState() {
        return combatBuffState;
    }

    public AgentCombatSkillCacheState combatSkillCacheState() {
        return combatSkillCacheState;
    }

    private final AgentAmmoSupplyState ammoSupplyState = new AgentAmmoSupplyState();

    private final AgentDegenerateAttackState degenerateAttackState = new AgentDegenerateAttackState();
    private final AgentRetreatHoldState retreatHoldState = new AgentRetreatHoldState();
    private final AgentBreakoutState breakoutState = new AgentBreakoutState();
    private final AgentAoeRepositionState aoeRepositionState = new AgentAoeRepositionState();
    private final AgentGrindWanderState grindWanderState = new AgentGrindWanderState();

    public AgentDegenerateAttackState degenerateAttackState() { return degenerateAttackState; }

    public AgentRetreatHoldState retreatHoldState() { return retreatHoldState; }

    public AgentBreakoutState breakoutState() { return breakoutState; }

    public AgentAoeRepositionState aoeRepositionState() { return aoeRepositionState; }

    private final AgentShopState shopState = new AgentShopState();

    public AgentShopState shopState() {
        return shopState;
    }

    // bumped whenever a new player directive resets scripted state (follow/stop/move/farm/patrol/grind);
    // background batches (Maker crafting / disassembly) capture it and self-interrupt when it changes
    private final AgentScriptTaskQueueState scriptTaskQueueState = new AgentScriptTaskQueueState();
    private final AgentDeathState deathState = new AgentDeathState();

    public AgentDeathState deathState() {
        return deathState;
    }

    private final AgentPortalCooldownState portalCooldownState = new AgentPortalCooldownState();

    public AgentPortalCooldownState portalCooldownState() {
        return portalCooldownState;
    }

    public AgentCombatCooldownState combatCooldownState() {
        return combatCooldownState;
    }

    private final AgentLeaderActivityState leaderActivityState = new AgentLeaderActivityState();
    private final AgentBuildState buildState = new AgentBuildState();

    public AgentLeaderActivityState leaderActivityState() {
        return leaderActivityState;
    }

    public AgentModeState modeState() { return modeState; }
    public Character bot() { return identityState.agent(); }
    public Character owner() { return identityState.leader(); }
    public void setOwner(Character owner) { identityState.setLeader(owner); }
    public AgentRuntimeIdentityState identityState() { return identityState; }
    public AgentBuildState buildState() {
        return buildState;
    }
    private final AgentMoveTargetState moveTargetState = new AgentMoveTargetState();

    public AgentMoveTargetState moveTargetState() { return moveTargetState; }
    public AgentGrindWanderState grindWanderState() { return grindWanderState; }
    private final AgentGrindLootState grindLootState = new AgentGrindLootState();

    public AgentGrindLootState grindLootState() { return grindLootState; }
    private final AgentMobTouchState mobTouchState = new AgentMobTouchState();

    public AgentMobTouchState mobTouchState() {
        return mobTouchState;
    }

    // Loot and potions
    private final AgentPotionSupplyState potionSupplyState = new AgentPotionSupplyState();
    private final AgentInventoryCooldownState inventoryCooldownState = new AgentInventoryCooldownState();

    public AgentInventoryCooldownState inventoryCooldownState() {
        return inventoryCooldownState;
    }

    public AgentPotionSupplyState potionSupplyState() {
        return potionSupplyState;
    }

    public AgentAmmoSupplyState ammoSupplyState() {
        return ammoSupplyState;
    }

    // Reply channel — tracks the chat channel the last owner command arrived on.
    // Bot replies are routed to this channel until the next command changes it.
    private final AgentReplyChannelState replyChannelState = new AgentReplyChannelState();

    public AgentReplyChannelState replyChannelState() {
        return replyChannelState;
    }

    private final AgentPendingActionState pendingActionState = new AgentPendingActionState();

    public AgentPendingActionState pendingActionState() {
        return pendingActionState;
    }

    private final AgentPendingLootOfferState pendingLootOfferState = new AgentPendingLootOfferState();
    private final AgentTradeRetryState tradeRetryState = new AgentTradeRetryState();

    public AgentPendingLootOfferState pendingLootOfferState() {
        return pendingLootOfferState;
    }

    // Bot-initiated trade retry: when a pot-share / ammo-share / loot-offer is blocked
    // because the sender or recipient is already in a trade, the attempt is stored here
    // and re-fired once the sender's trade clears and the delay expires.
    public AgentTradeRetryState tradeRetryState() {
        return tradeRetryState;
    }

    private final AgentPendingTradeSequenceState pendingTradeSequenceState = new AgentPendingTradeSequenceState();

    public AgentPendingTradeSequenceState pendingTradeSequenceState() {
        return pendingTradeSequenceState;
    }

    // Message queue
    private final AgentMessageQueueState messageQueueState = new AgentMessageQueueState();

    public AgentMessageQueueState messageQueueState() {
        return messageQueueState;
    }

    public AgentScriptTaskQueueState scriptTaskQueueState() {
        return scriptTaskQueueState;
    }

    // Foothold index, rebuilt on map change
    private final AgentMapTrackingState mapTrackingState = new AgentMapTrackingState();

    public AgentMapTrackingState mapTrackingState() {
        return mapTrackingState;
    }

    // Human-like spacing and stagger — assigned at registration based on bot index
    private final AgentFormationOffsetState formationOffsetState = new AgentFormationOffsetState();

    public AgentFormationOffsetState formationOffsetState() {
        return formationOffsetState;
    }

    // "Move here" target — bot navigates to this fixed point, then idles until cleared
    // Explicit move-target storage now lives in AgentMoveTargetState.
    // "Farm here" anchor — bot returns to this fixed point and only takes local attacks.
    // "Patrol" region — bot wanders within this nav region and attacks opportunistically.

    private final AgentBuffState buffState = new AgentBuffState();
    private final AgentUpgradeOfferState upgradeOfferState = new AgentUpgradeOfferState();

    public AgentBuffState buffState() {
        return buffState;
    }

    public AgentUpgradeOfferState upgradeOfferState() {
        return upgradeOfferState;
    }

    // Party-quest state (one slot per PQ type; null = not in that PQ)
    private final AgentKpqState kpqState = new AgentKpqState();

    public AgentKpqState kpqState() {
        return kpqState;
    }

    private final AgentScriptRuntimeState scriptRuntimeState = new AgentScriptRuntimeState();

    public AgentScriptRuntimeState scriptRuntimeState() {
        return scriptRuntimeState;
    }

    private final AgentOwnerGivenTradeItemState ownerGivenTradeItemState = new AgentOwnerGivenTradeItemState();

    public AgentOwnerGivenTradeItemState ownerGivenTradeItemState() {
        return ownerGivenTradeItemState;
    }

    private final AgentNavigationDebugState navigationDebugState = new AgentNavigationDebugState();
    private final AgentNavigationEdgeState navigationEdgeState = new AgentNavigationEdgeState();
    private final AgentNavigationTargetState navigationTargetState = new AgentNavigationTargetState();
    private final AgentFarmAnchorState farmAnchorState = new AgentFarmAnchorState();
    private final AgentMovementBroadcastState movementBroadcastState = new AgentMovementBroadcastState();
    private final AgentMovementPhysicsCacheState movementPhysicsCacheState = new AgentMovementPhysicsCacheState();
    private final AgentMovementStuckState movementStuckState = new AgentMovementStuckState();
    private final AgentOwnerMotionState ownerMotionState = new AgentOwnerMotionState();
    private final AgentPatrolState patrolState = new AgentPatrolState();
    private final AgentTickFailureState tickFailureState = new AgentTickFailureState();
    private final AgentTickState tickState = new AgentTickState();

    private final AgentFidgetState fidgetState = new AgentFidgetState();

    public AgentFidgetState fidgetState() {
        return fidgetState;
    }

    private final AgentScrollReactionState scrollReactionState = new AgentScrollReactionState();

    public AgentNavigationDebugState navigationDebugState() {
        return navigationDebugState;
    }

    public AgentNavigationEdgeState navigationEdgeState() {
        return navigationEdgeState;
    }

    public AgentNavigationTargetState navigationTargetState() {
        return navigationTargetState;
    }

    public AgentFarmAnchorState farmAnchorState() {
        return farmAnchorState;
    }

    public AgentOwnerMotionState ownerMotionState() {
        return ownerMotionState;
    }

    public AgentPatrolState patrolState() {
        return patrolState;
    }

    public AgentMovementBroadcastState movementBroadcastState() {
        return movementBroadcastState;
    }

    public AgentMovementPhysicsCacheState movementPhysicsCacheState() {
        return movementPhysicsCacheState;
    }

    public AgentMovementStuckState movementStuckState() {
        return movementStuckState;
    }

    public AgentTickState tickState() {
        return tickState;
    }

    public AgentTickFailureState tickFailureState() {
        return tickFailureState;
    }

    public AgentScrollReactionState scrollReactionState() {
        return scrollReactionState;
    }

    // Manual trade: countdown before bot accepts an incoming trade invite (both owner and peer-bot)
    private final AgentManualTradeState manualTradeState = new AgentManualTradeState();

    public AgentManualTradeState manualTradeState() {
        return manualTradeState;
    }

    public AgentRuntimeEntry(Character bot, Character owner, ScheduledFuture<?> task) {
        this.identityState = new AgentRuntimeIdentityState(bot, owner);
        this.scheduledTaskState = new AgentScheduledTaskState(task);
    }

}
