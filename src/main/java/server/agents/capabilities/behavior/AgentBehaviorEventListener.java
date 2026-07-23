package server.agents.capabilities.behavior;

import client.Character;
import config.YamlConfig;
import server.agents.behavior.AgentBehaviorRuntime;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.events.AgentEvent;
import server.agents.events.AgentEventListener;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.operations.events.AgentAttackResolvedEvent;
import server.agents.operations.events.AgentMobKilledEvent;
import server.agents.operations.events.AgentCombatTargetChangedEvent;
import server.agents.operations.events.AgentCrowdRespiteEvent;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Updates live adaptation and projects rare, observer-gated facial reactions. */
public final class AgentBehaviorEventListener implements AgentEventListener<AgentEvent> {
    private static final long BUDGET_WINDOW_MS = config.AgentTuning.longValue("server.agents.capabilities.behavior.AgentBehaviorEventListener.BUDGET_WINDOW_MS");
    private static final Map<Integer, MapBudget> MAP_BUDGETS = new ConcurrentHashMap<>();

    private final AgentRuntimeEntry entry;
    private long nextExpressionAtMs;

    public AgentBehaviorEventListener(AgentRuntimeEntry entry) { this.entry = entry; }

    @Override
    public void onAgentEvent(AgentEvent event) {
        if (!AgentBehaviorRuntime.enabled(entry)) return;
        if (event instanceof AgentMobKilledEvent) {
            AgentBehaviorRuntime.adaptation(entry).mobKilled();
            return;
        }
        if (event instanceof AgentCombatTargetChangedEvent changed && changed.targetObjectId() == 0) {
            AgentBehaviorRuntime.adaptation(entry).targetLost();
            return;
        }
        if (event instanceof AgentCrowdRespiteEvent respite) {
            if (respite.stage() == AgentCrowdRespiteEvent.Stage.RESUMED) {
                AgentBehaviorRuntime.adaptation(entry).rested();
            } else if (respite.stage() == AgentCrowdRespiteEvent.Stage.SETTLED) {
                maybeShowRestExpression(respite);
            }
            return;
        }
        if (!(event instanceof AgentAttackResolvedEvent attack)) return;
        AgentBehaviorRuntime.adaptation(entry).attackResolved(attack.hitLines(), attack.missLines());
        if (attack.hitLines() > 0 || AgentBehaviorRuntime.adaptation(entry).consecutiveMisses() < 3) return;
        maybeShowMissExpression(attack);
    }

    private void maybeShowMissExpression(AgentAttackResolvedEvent event) {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        int chance = AgentBehaviorRuntime.policy(entry).reactions().missEmotePercent();
        if (!canShow(agent, event.occurredAtMs(), "miss-emote", chance)) {
            AgentBehaviorTelemetry.expressionSuppressed();
            return;
        }
        int emote = AgentBehaviorRuntime.adaptation(entry).frustration() >= 60 ? 5 : 4;
        agent.changeFaceExpression(emote);
        nextExpressionAtMs = event.occurredAtMs()
                + AgentBehaviorRuntime.policy(entry).reactions().cooldownMs();
        AgentBehaviorTelemetry.expressionShown();
    }

    private void maybeShowRestExpression(AgentCrowdRespiteEvent event) {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        int chance = AgentBehaviorRuntime.policy(entry).reactions().restEmotePercent();
        if (!canShow(agent, event.occurredAtMs(), "rest-emote", chance)) {
            AgentBehaviorTelemetry.expressionSuppressed();
            return;
        }
        agent.changeFaceExpression(AgentBehaviorRuntime.calibration(entry).nextPercent("rest-face") < 65 ? 2 : 3);
        nextExpressionAtMs = event.occurredAtMs() + AgentBehaviorRuntime.policy(entry).reactions().cooldownMs();
        AgentBehaviorTelemetry.expressionShown();
    }

    private boolean canShow(Character agent, long nowMs, String channel, int chance) {
        if (!config.AgentYamlConfig.config.agent.AGENT_COMBAT_EMOTES_ENABLED || agent == null || agent.getMap() == null
                || nowMs < nextExpressionAtMs || AgentMovementStateRuntime.inAir(entry)
                || AgentMovementStateRuntime.climbing(entry)
                || AgentBehaviorRuntime.calibration(entry).nextPercent(channel) >= chance
                || agent.getMap().getCharacters().stream().noneMatch(character ->
                !AgentCharacterGatewayRuntime.characters().isAgentCharacter(character))) return false;
        return MAP_BUDGETS.computeIfAbsent(agent.getMapId(), ignored -> new MapBudget()).tryAcquire(
                nowMs, Math.max(1, config.AgentYamlConfig.config.agent.AGENT_COMBAT_EMOTE_MAP_BUDGET_PER_10S));
    }

    private static final class MapBudget {
        private long windowStartedAtMs;
        private int used;

        synchronized boolean tryAcquire(long nowMs, int limit) {
            if (windowStartedAtMs == 0L || nowMs - windowStartedAtMs >= BUDGET_WINDOW_MS) {
                windowStartedAtMs = nowMs;
                used = 0;
            }
            if (used >= limit) return false;
            used++;
            return true;
        }
    }
}
