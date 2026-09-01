package server.agents.capabilities.mobcontrol;

import client.Character;
import net.server.services.task.channel.MobPhysicsService;
import net.server.services.task.channel.ServerMobAutonomyService;
import net.server.services.type.ChannelServices;
import server.life.Monster;
import server.integration.MobHitReactionContext;
import server.maps.MapleMap;

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
        if (acquireServerCombat(attacker, monster)) {
            return;
        }
        boolean physicsAcquired = strategy(AgentMobPhysicsConfig.config().AGENT_MOB_REACTION_MODE)
                .acceptedHit(attacker, monster, appliedDamage, reactionContext);
        if (!physicsAcquired) {
            ServerMobAutonomyService.releaseOrdinaryAggroInstances(
                    monster, "physics-acquisition-rejected");
        }
    }

    private static boolean acquireServerCombat(Character attacker, Monster monster) {
        if (attacker == null || monster == null) {
            return false;
        }
        MapleMap map = monster.getMap();
        if (map == null || map.getChannelServer() == null) {
            return false;
        }
        if (map.getChannelServer().getServiceAccess(ChannelServices.MOB_AUTONOMY)
                instanceof ServerMobAutonomyService service) {
            service.acquire(monster, attacker);
            return service.retainsNativeAuthority(monster)
                    || service.blocksAgentPhysics(monster);
        }
        return false;
    }

    public static void modeChanged(AgentMobReactionMode previous, AgentMobReactionMode current) {
        if (previous == current) return;
        if (previous == AgentMobReactionMode.PHYSICS) {
            MobPhysicsService.releaseAllInstances(MobPhysicsService.ReleaseReason.MODE_CHANGE);
        }
    }

    static AgentMobReactionStrategy strategy(AgentMobReactionMode mode) {
        return switch (mode) {
            case OFF -> OffMobReactionStrategy.INSTANCE;
            case PHYSICS -> PhysicsMobReactionStrategy.INSTANCE;
        };
    }
}
