package server.agents.capabilities.combat;

import client.Character;
import config.YamlConfig;
import server.agents.behavior.AgentBehaviorPolicyProfile;
import server.agents.behavior.AgentBehaviorRuntime;
import server.agents.capabilities.presentation.AgentWeaponFlourishService;
import server.agents.integration.AgentFidgetGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import server.agents.capabilities.behavior.AgentBehaviorTelemetry;

/** Profile-selected no-target presentation. Returns false when ordinary patrol should run. */
public final class AgentCombatIdleBehaviorRuntime {
    private AgentCombatIdleBehaviorRuntime() {
    }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, Point position, long nowMs) {
        if (!AgentBehaviorRuntime.enabled(entry)
                || !config.AgentYamlConfig.config.agent.AGENT_IDLE_COMBAT_PRESENTATION_ENABLED) return false;
        if (AgentFidgetGateway.combatIdleActive(entry)) {
            return AgentFidgetGateway.tickCombatIdle(entry, position, nowMs);
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
            AgentFidgetGateway.startCombatIdle(entry, AgentFidgetGateway.Action.WAIT, nowMs, duration);
        } else if ((roll -= idle.patrolWeight()) < 0) {
            return false;
        } else if ((roll -= idle.jumpWeight()) < 0) {
            AgentFidgetGateway.startCombatIdle(entry, AgentFidgetGateway.Action.JUMP, nowMs, duration);
        } else if ((roll -= idle.proneWeight()) < 0) {
            AgentFidgetGateway.startCombatIdle(entry, AgentFidgetGateway.Action.PRONE, nowMs, duration);
        } else {
            int direction = AgentBehaviorRuntime.calibration(entry).nextPercent("idle-facing") < 50 ? -1 : 1;
            AgentWeaponFlourishService.flourish(agent, new Point(position.x + direction * 40, position.y));
            AgentFidgetGateway.startCombatIdle(entry, AgentFidgetGateway.Action.WAIT, nowMs, duration);
        }
        return AgentFidgetGateway.tickCombatIdle(entry, position, nowMs);
    }
}
