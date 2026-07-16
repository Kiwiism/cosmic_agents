package server.agents.capabilities.mobcontrol;

import client.Character;
import net.server.services.task.channel.MobPhysicsService;
import net.server.services.type.ChannelServices;
import server.life.Monster;
import server.maps.MapleMap;

public enum PhysicsMobReactionStrategy implements AgentMobReactionStrategy {
    INSTANCE;

    @Override
    public void acceptedHit(Character attacker, Monster monster, int appliedDamage, long reactionDelayMs) {
        if (monster == null) return;
        MapleMap map = monster.getMap();
        if (map == null || map.getChannelServer() == null) return;
        MobPhysicsService service = (MobPhysicsService) map.getChannelServer()
                .getServiceAccess(ChannelServices.MOB_PHYSICS);
        service.acceptedHit(attacker, monster, appliedDamage, reactionDelayMs);
    }
}
