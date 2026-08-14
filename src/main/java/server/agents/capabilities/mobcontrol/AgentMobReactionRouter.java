package server.agents.capabilities.mobcontrol;

import client.Character;
import net.server.services.task.channel.MobPhysicsService;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.combat.AgentSyntheticMobReactionService;
import server.life.Monster;
import server.integration.MobHitReactionContext;

/** Exactly one strategy receives each accepted Agent hit. */
public final class AgentMobReactionRouter {
    private AgentMobReactionRouter() {
    }

    public static void acceptedHit(Character attacker, Monster monster,
                                   int appliedDamage, long reactionDelayMs) {
        acceptedHit(attacker, monster, appliedDamage,
                MobHitReactionContext.legacy(reactionDelayMs, attacker, monster));
    }

    public static void acceptedHit(Character attacker, Monster monster,
                                   int appliedDamage, MobHitReactionContext reactionContext) {
        strategy(AgentCombatConfig.cfg.AGENT_MOB_REACTION_MODE)
                .acceptedHit(attacker, monster, appliedDamage, reactionContext);
    }

    public static void modeChanged(AgentMobReactionMode previous, AgentMobReactionMode current) {
        if (previous == current) return;
        if (previous == AgentMobReactionMode.PHYSICS) {
            MobPhysicsService.releaseAllInstances(MobPhysicsService.ReleaseReason.MODE_CHANGE);
        }
        if (previous == AgentMobReactionMode.SYNTHETIC) {
            AgentSyntheticMobReactionService.releaseAll();
        }
    }

    static AgentMobReactionStrategy strategy(AgentMobReactionMode mode) {
        return switch (mode) {
            case OFF -> OffMobReactionStrategy.INSTANCE;
            case SYNTHETIC -> SyntheticMobReactionStrategy.INSTANCE;
            case PHYSICS -> PhysicsMobReactionStrategy.INSTANCE;
        };
    }
}
