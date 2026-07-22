package server.agents.capabilities.combat;

import client.Character;
import config.YamlConfig;
import server.agents.behavior.AgentBehaviorPolicyProfile;
import server.agents.behavior.AgentBehaviorRuntime;
import server.agents.capabilities.movement.fidget.AgentFidgetMode;
import server.agents.capabilities.movement.fidget.AgentFidgetService;
import server.agents.capabilities.movement.fidget.AgentFidgetStateRuntime;
import server.agents.capabilities.movement.fidget.AgentFidgetTrigger;
import server.agents.capabilities.presentation.AgentWeaponFlourishService;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import server.agents.capabilities.behavior.AgentBehaviorTelemetry;

/** Profile-selected no-target presentation. Returns false when ordinary patrol should run. */
public final class AgentCombatIdleBehaviorRuntime {
    private AgentCombatIdleBehaviorRuntime() {
    }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, Point position, long nowMs) {
        if (!AgentBehaviorRuntime.enabled(entry)
                || !YamlConfig.config.server.AGENT_IDLE_COMBAT_PRESENTATION_ENABLED) return false;
        if (AgentFidgetStateRuntime.trigger(entry) == AgentFidgetTrigger.COMBAT_IDLE) {
            return AgentFidgetService.tryHandleCombatIdleTick(entry, position, nowMs);
        }
        AgentCombatIdleBehaviorState state = entry.capabilityStates().require(AgentCombatIdleBehaviorState.STATE_KEY);
        if (!state.due(nowMs)) return true;

        AgentBehaviorPolicyProfile.Idle idle = AgentBehaviorRuntime.policy(entry).idle();
        int total = idle.waitWeight() + idle.patrolWeight() + idle.jumpWeight()
                + idle.proneWeight() + idle.emptyAttackWeight();
        int roll = AgentBehaviorRuntime.calibration(entry).nextPercent("idle") * total / 100;
        int duration = 1800 + AgentBehaviorRuntime.calibration(entry).nextPercent("idle-duration") * 32;
        AgentBehaviorTelemetry.idlePresentation();
        state.defer(nowMs, duration + 500);
        if ((roll -= idle.waitWeight()) < 0) {
            AgentFidgetService.startFidget(entry, AgentFidgetMode.WAIT, nowMs, duration, AgentFidgetTrigger.COMBAT_IDLE);
        } else if ((roll -= idle.patrolWeight()) < 0) {
            return false;
        } else if ((roll -= idle.jumpWeight()) < 0) {
            AgentFidgetService.startFidget(entry, AgentFidgetMode.JUMP, nowMs, duration, AgentFidgetTrigger.COMBAT_IDLE);
        } else if ((roll -= idle.proneWeight()) < 0) {
            AgentFidgetService.startFidget(entry, AgentFidgetMode.SPAM_PRONE, nowMs, duration, AgentFidgetTrigger.COMBAT_IDLE);
        } else {
            int direction = AgentBehaviorRuntime.calibration(entry).nextPercent("idle-facing") < 50 ? -1 : 1;
            AgentWeaponFlourishService.flourish(agent, new Point(position.x + direction * 40, position.y));
            AgentFidgetService.startFidget(entry, AgentFidgetMode.WAIT, nowMs, duration, AgentFidgetTrigger.COMBAT_IDLE);
        }
        return AgentFidgetService.tryHandleCombatIdleTick(entry, position, nowMs);
    }
}
