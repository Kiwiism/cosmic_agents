package server.agents.capabilities.mobcontrol;

import client.Character;
import net.server.services.task.channel.MobPhysicsService;
import net.server.services.type.ChannelServices;
import server.life.Monster;
import server.maps.MapleMap;
import server.integration.MobHitReactionContext;

public enum PhysicsMobReactionStrategy implements AgentMobReactionStrategy {
    INSTANCE;

    @Override
    public boolean acceptedHit(Character attacker, Monster monster, int appliedDamage,
                               MobHitReactionContext reactionContext) {
        if (monster == null) return false;
        MapleMap map = monster.getMap();
        if (map == null || map.getChannelServer() == null) return false;
        MobPhysicsService service = (MobPhysicsService) map.getChannelServer()
                .getServiceAccess(ChannelServices.MOB_PHYSICS);
        return service.acceptedHit(attacker, monster, appliedDamage, reactionContext);
    }
}
