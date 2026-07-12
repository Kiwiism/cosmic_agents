package server.integration;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.life.Monster;

import java.util.concurrent.atomic.AtomicLong;

public final class MonsterAggroTargetBridge {
    private static final Logger log = LoggerFactory.getLogger(MonsterAggroTargetBridge.class);
    private static final long FAILURE_LOG_INTERVAL_MS = 5_000L;
    private static final AtomicLong lastFailureLog = new AtomicLong();
    private static final MonsterAggroTargetProvider NOOP = (monster, attacker, damage) -> false;
    private static volatile MonsterAggroTargetProvider provider = NOOP;

    private MonsterAggroTargetBridge() {
    }

    public static boolean suppressLegacyAggro(Monster monster, Character attacker) {
        try {
            return provider.suppressLegacyAggro(monster, attacker);
        } catch (RuntimeException | LinkageError failure) {
            logFailure("pre-hit policy", failure);
            return false;
        }
    }

    public static boolean onAcceptedDamage(Monster monster, Character attacker, int damage) {
        try {
            return provider.onAcceptedDamage(monster, attacker, damage);
        } catch (RuntimeException | LinkageError failure) {
            logFailure("legacy accepted-damage callback", failure);
            return false;
        }
    }

    public static boolean onAcceptedDamage(Monster monster, Character attacker, int damage,
                                           int maxDamageLine) {
        try {
            return provider.onAcceptedDamage(monster, attacker, damage, maxDamageLine);
        } catch (RuntimeException | LinkageError failure) {
            logFailure("accepted-damage callback", failure);
            return false;
        }
    }

    public static void onAcceptedDamage(Monster monster, MonsterDamageOutcome outcome) {
        try {
            provider.onAcceptedDamage(monster, outcome);
        } catch (RuntimeException | LinkageError failure) {
            logFailure("accepted-damage callback", failure);
        }
    }

    public static void onControllerMovement(Monster monster, int movementCommand) {
        try {
            provider.onControllerMovement(monster, movementCommand);
        } catch (RuntimeException | LinkageError failure) {
            logFailure("controller-movement callback", failure);
        }
    }

    public static boolean usesServerPursuit(Monster monster) {
        try {
            return provider.usesServerPursuit(monster);
        } catch (RuntimeException | LinkageError failure) {
            logFailure("server-pursuit policy", failure);
            return false;
        }
    }

    public static void clear(Monster monster) {
        try {
            provider.clear(monster);
        } catch (RuntimeException | LinkageError failure) {
            logFailure("cleanup callback", failure);
        }
    }

    public static void install(MonsterAggroTargetProvider newProvider) {
        provider = newProvider == null ? NOOP : newProvider;
    }

    private static void logFailure(String operation, Throwable failure) {
        long now = System.currentTimeMillis();
        long previous = lastFailureLog.get();
        if (now - previous >= FAILURE_LOG_INTERVAL_MS
                && lastFailureLog.compareAndSet(previous, now)) {
            log.warn("Monster aggro integration {} failed; native combat will continue",
                    operation, failure);
        }
    }
}
