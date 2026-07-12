package server.integration;

import client.Character;
import server.life.Monster;

public interface MonsterAggroTargetProvider {
    /**
     * @return true when the provider has applied the controller/target policy and
     * the legacy highest-damage controller selection must be skipped.
     */
    boolean onAcceptedDamage(Monster monster, Character attacker, int damage);

    default void onControllerMovement(Monster monster, int movementCommand) {
    }

    default boolean usesServerPursuit(Monster monster) {
        return false;
    }

    default void clear(Monster monster) {
    }
}
