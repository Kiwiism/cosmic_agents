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
    public void onControllerMovement(Monster monster, int movementCommand) {
        MonsterAggroTargetService.recordControllerMovement(
                monster, movementCommand, System.currentTimeMillis());
    }

    @Override
    public boolean usesServerPursuit(Monster monster) {
        return MonsterAggroTargetService.usesServerPursuit(monster, System.currentTimeMillis());
    }

    @Override
    public void clear(Monster monster) {
        MonsterAggroTargetService.clear(monster);
    }
}
