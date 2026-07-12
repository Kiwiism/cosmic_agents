package server.agents.integration.cosmic;

import client.Character;
import server.agents.capabilities.combat.MonsterAggroTargetService;
import server.integration.MonsterAggroTargetProvider;
import server.life.Monster;

public enum CosmicMonsterAggroTargetProvider implements MonsterAggroTargetProvider {
    INSTANCE;

    @Override
    public boolean onAcceptedDamage(Monster monster, Character attacker, int damage) {
        return CosmicMobReactionGateway.INSTANCE.handleAcceptedDamage(monster, attacker, damage);
    }

    @Override
    public void clear(Monster monster) {
        MonsterAggroTargetService.clear(monster);
    }
}
