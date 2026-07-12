package server.integration;

import client.Character;
import server.life.Monster;

public final class MonsterAggroTargetBridge {
    private static final MonsterAggroTargetProvider NOOP = (monster, attacker, damage) -> false;
    private static volatile MonsterAggroTargetProvider provider = NOOP;

    private MonsterAggroTargetBridge() {
    }

    public static boolean onAcceptedDamage(Monster monster, Character attacker, int damage) {
        return provider.onAcceptedDamage(monster, attacker, damage);
    }

    public static void clear(Monster monster) {
        provider.clear(monster);
    }

    public static void install(MonsterAggroTargetProvider newProvider) {
        provider = newProvider == null ? NOOP : newProvider;
    }
}
