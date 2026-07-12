package server.integration;

import client.Character;
import server.life.Monster;

public interface MonsterAggroTargetProvider {
    default boolean suppressLegacyAggro(Monster monster, Character attacker) {
        return false;
    }

    /**
     * @return true when the provider has applied the controller/target policy and
     * the legacy highest-damage controller selection must be skipped.
     */
    boolean onAcceptedDamage(Monster monster, Character attacker, int damage);

    default boolean onAcceptedDamage(Monster monster, Character attacker, int damage,
                                     int maxDamageLine) {
        return onAcceptedDamage(monster, attacker, damage);
    }

    default void onAcceptedDamage(Monster monster, MonsterDamageOutcome outcome) {
        onAcceptedDamage(monster, outcome.attacker(), outcome.appliedDamage(),
                outcome.maxAcceptedDamageLine());
    }

    default void onControllerMovement(Monster monster, int movementCommand) {
    }

    default boolean usesServerPursuit(Monster monster) {
        return false;
    }

    default void clear(Monster monster) {
    }
}
