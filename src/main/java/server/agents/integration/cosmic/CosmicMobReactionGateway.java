package server.agents.integration.cosmic;

import client.Character;
import config.YamlConfig;
import net.server.channel.handlers.AbstractDealDamageHandler;
import server.agents.capabilities.combat.AcceptedMobHitResult;
import server.agents.capabilities.combat.AgentMobReactionMetrics;
import server.agents.capabilities.combat.MonsterAggroTargetService;
import server.agents.capabilities.combat.ObservedMobSimulationPolicy;
import server.agents.integration.MobReactionGateway;
import server.integration.AgentPresence;
import server.integration.MonsterDamageOutcome;
import server.life.Monster;
import server.maps.MapleMap;

import java.util.List;

public enum CosmicMobReactionGateway implements MobReactionGateway {
    INSTANCE;

    @Override
    public void prepareObservedAttack(AbstractDealDamageHandler.AttackInfo attack, Character agent) {
        if (attack == null || agent == null || !AgentPresence.isAgent(agent)
                || !observedReactionEnabled()) {
            return;
        }
        MapleMap map = agent.getMap();
        if (!ObservedMobSimulationPolicy.shouldSimulate(true,
                MonsterSimulationControllerResolver.hasObserver(map))) {
            AgentMobReactionMetrics.noObserverSkip();
            return;
        }

        for (var targetEntry : attack.targets.entrySet()) {
            Monster monster = map.getMonsterByOid(targetEntry.getKey());
            if (monster == null || !monster.isAlive()) {
                continue;
            }
            List<Integer> lines = targetEntry.getValue().damageLines();
            int damage = positiveDamage(lines);
            if (damage <= 0) {
                continue;
            }

            AgentMobReactionMetrics.duplicateDamageProtection();
            int threshold = monster.getStats().getPushed();
            boolean thresholdSatisfied = lines.stream().anyMatch(line -> line != null
                    && line > 0 && line >= threshold);
            if (thresholdSatisfied) {
                AgentMobReactionMetrics.thresholdMet();
            } else {
                AgentMobReactionMetrics.knockbackSuppressedBelowThreshold();
            }

            Character controller = MonsterSimulationControllerResolver.resolve(monster);
            if (controller == null) {
                AgentMobReactionMetrics.controllerFailure();
                continue;
            }
            ensureController(monster, controller);
            boolean movable = monster.isMobile() && monster.getStats().getFixedStance() == 0;
            if (thresholdSatisfied && movable) {
                AgentMobReactionMetrics.knockbackPrepared();
            } else if (thresholdSatisfied) {
                AgentMobReactionMetrics.knockbackSuppressedImmobile();
            }
            String reaction = thresholdSatisfied && movable
                    ? "client-knockback-eligible" : "hurt-only";
            if (lastHitAggroEnabled()) {
                MonsterAggroTargetService.prepareReaction(monster, agent, damage, threshold,
                        reaction, Math.max(0, targetEntry.getValue().delay()));
            }
        }
    }

    boolean handleAcceptedDamage(Monster monster, Character attacker, int damage) {
        return handleAcceptedDamage(monster, attacker, damage, damage);
    }

    boolean handleAcceptedDamage(Monster monster, Character attacker, int damage,
                                 int maxDamageLine) {
        return handleAcceptedDamage(monster, attacker, damage, maxDamageLine,
                monster != null && monster.isAlive(), false, direction(attacker, monster), null);
    }

    void handleAcceptedDamage(Monster monster, MonsterDamageOutcome outcome) {
        if (outcome == null) {
            return;
        }
        handleAcceptedDamage(monster, outcome.attacker(), outcome.appliedDamage(),
                outcome.maxAcceptedDamageLine(), outcome.monsterAlive(),
                outcome.monsterKilled(), outcome.hitDirection(), outcome.reactionDelayMs());
    }

    boolean shouldSuppressLegacyAggro(Monster monster, Character attacker) {
        if (monster == null || attacker == null || monster.getMap() == null
                || !MonsterSimulationControllerResolver.hasObserver(monster.getMap())) {
            return false;
        }
        return lastHitAggroEnabled()
                || observedReactionEnabled() && AgentPresence.isAgent(attacker);
    }

    private boolean handleAcceptedDamage(Monster monster, Character attacker, int damage,
                                         int maxDamageLine, boolean monsterAlive,
                                         boolean monsterKilled, int hitDirection,
                                         Long acceptedReactionDelayMs) {
        if (monster == null || attacker == null || damage <= 0 || monster.getMap() == null) {
            return false;
        }
        boolean agentAttacker = AgentPresence.isAgent(attacker);
        boolean reactionPolicy = observedReactionEnabled() && agentAttacker;
        boolean lastHitPolicy = lastHitAggroEnabled();
        if (!reactionPolicy && !lastHitPolicy) {
            return false;
        }
        boolean observed = MonsterSimulationControllerResolver.hasObserver(monster.getMap());
        if (!observed) {
            return false;
        }
        if (agentAttacker) {
            AgentMobReactionMetrics.acceptedHit();
            AgentMobReactionMetrics.hurtReaction();
        }

        MonsterAggroTargetService.PreparedReaction prepared = lastHitPolicy
                ? MonsterAggroTargetService.consumePreparedReaction(monster, attacker) : null;
        int threshold = prepared == null ? monster.getStats().getPushed() : prepared.threshold();
        boolean movable = monsterAlive && !monsterKilled && monster.isMobile()
                && monster.getStats().getFixedStance() == 0;
        boolean preparedKnockback = prepared == null
                || "client-knockback-eligible".equals(prepared.reaction());
        boolean acceptedKnockback = reactionPolicy && movable && preparedKnockback
                && maxDamageLine > 0 && maxDamageLine >= threshold;
        String reaction = prepared == null ? "accepted-damage"
                : acceptedKnockback
                && "client-knockback-eligible".equals(prepared.reaction())
                ? "client-knockback-eligible" : "hurt-only";
        long hitDelayMs = acceptedReactionDelayMs != null
                ? Math.max(0L, acceptedReactionDelayMs)
                : prepared == null ? 0L : prepared.hitDelayMs();
        AcceptedMobHitResult result = new AcceptedMobHitResult(
                attacker, agentAttacker, damage, Math.max(0, maxDamageLine),
                monsterAlive, monsterKilled, acceptedKnockback, hitDirection,
                hitDelayMs, observed, reaction);

        if (lastHitPolicy) {
            if (result.monsterKilled() || !result.monsterAlive()) {
                MonsterAggroTargetService.clear(monster);
                return true;
            }

            Character controller = MonsterSimulationControllerResolver.isEligible(
                    attacker, monster.getMap())
                    ? attacker : MonsterSimulationControllerResolver.resolve(monster);
            if (controller == null) {
                AgentMobReactionMetrics.controllerFailure();
                return agentAttacker;
            }
            ensureController(monster, controller);
            MonsterAggroTargetService.record(monster, result, controller, threshold,
                    System.currentTimeMillis(),
                    result.reactionDelayMs() + CosmicMonsterPursuitRuntime.IMPACT_SETTLE_MS);
            CosmicMonsterPursuitRuntime.ensureRunning();
        }
        return true;
    }

    private static int direction(Character attacker, Monster monster) {
        if (attacker == null || monster == null || attacker.getPosition() == null
                || monster.getPosition() == null) {
            return 0;
        }
        return Integer.compare(monster.getPosition().x, attacker.getPosition().x);
    }

    @Override
    public String describe(Monster monster) {
        if (monster == null) {
            return "No monster selected.";
        }
        long timeout = targetTimeoutMs();
        MonsterAggroTargetService.Snapshot target = MonsterAggroTargetService.inspect(
                monster, System.currentTimeMillis(), timeout);
        Character controller = monster.getController();
        return monster.getName() + " oid=" + monster.getObjectId()
                + " hp=" + monster.getHp() + "/" + monster.getMaxHp()
                + " pushed=" + monster.getStats().getPushed()
                + " observed=" + MonsterSimulationControllerResolver.hasObserver(monster.getMap())
                + " target=" + target.targetName() + "(" + target.targetId() + ")"
                + " targetType=" + (target.agentTarget() ? "agent" : "player")
                + " controller=" + (controller == null ? "none" : controller.getName()
                + "(" + controller.getId() + ")")
                + " simulator=" + (target.agentTarget() && controller == null
                ? "server-proxy" : "client")
                + " lastDamage=" + target.damage() + " reaction=" + target.reaction()
                + " knockbackEligible=" + target.knockbackEligible()
                + " hitDirection=" + target.hitDirection()
                + " aliveAtHit=" + target.monsterAliveAtHit()
                + " observedAtHit=" + target.observedAtHit()
                + " movement=" + target.latestMovement();
    }

    private static void ensureController(Monster monster, Character controller) {
        if (monster.getController() == controller) {
            monster.aggroAutoAggroUpdate(controller);
        } else {
            monster.aggroSwitchController(controller, true);
        }
    }

    private static int positiveDamage(List<Integer> lines) {
        long total = 0;
        for (Integer line : lines) {
            if (line != null && line > 0) {
                total += line;
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static boolean observedReactionEnabled() {
        return YamlConfig.config.agents != null && YamlConfig.config.agents.combat != null
                && YamlConfig.config.agents.combat.observedMobReaction != null
                && YamlConfig.config.agents.combat.observedMobReaction.enabled;
    }

    private static boolean lastHitAggroEnabled() {
        return YamlConfig.config.agents != null && YamlConfig.config.agents.combat != null
                && YamlConfig.config.agents.combat.lastHitAggro != null
                && YamlConfig.config.agents.combat.lastHitAggro.enabled;
    }

    private static long targetTimeoutMs() {
        return YamlConfig.config.agents == null || YamlConfig.config.agents.combat == null
                || YamlConfig.config.agents.combat.lastHitAggro == null
                ? 10_000L : YamlConfig.config.agents.combat.lastHitAggro.targetTimeoutMs;
    }
}
