package net.packet;

import config.YamlConfig;

import java.util.EnumMap;
import java.util.Map;

public final class PacketRateLimiter {
    private static final long WINDOW_MS = 1_000;
    private static final long VIOLATION_WINDOW_MS = 10_000;

    private final int violationLimit;
    private final Map<PacketFamily, Integer> familyRates;
    private final Map<PacketFamily, TokenBucket> familyBuckets = new EnumMap<>(PacketFamily.class);
    private final TokenBucket globalBucket;
    private final Counter violationCounter = new Counter();

    public PacketRateLimiter(int globalRate, int globalBurst, int movementRate, int combatRate,
                             int chatRate, int economyRate, int otherRate, int violationLimit) {
        int safeGlobalRate = positive(globalRate);
        this.globalBucket = new TokenBucket(safeGlobalRate, Math.max(safeGlobalRate, globalBurst));
        this.violationLimit = positive(violationLimit);
        this.familyRates = new EnumMap<>(PacketFamily.class);
        familyRates.put(PacketFamily.AUTH, otherRate);
        familyRates.put(PacketFamily.MOVEMENT, movementRate);
        familyRates.put(PacketFamily.COMBAT, combatRate);
        familyRates.put(PacketFamily.CHAT, chatRate);
        familyRates.put(PacketFamily.ECONOMY, economyRate);
        familyRates.put(PacketFamily.OTHER, otherRate);
        for (PacketFamily family : PacketFamily.values()) {
            familyRates.compute(family, (ignored, value) -> positive(value == null ? otherRate : value));
            int rate = familyRates.get(family);
            familyBuckets.put(family, new TokenBucket(rate, rate * 2));
        }
    }

    public static PacketRateLimiter fromConfig() {
        return new PacketRateLimiter(
                YamlConfig.config.server.PACKET_GLOBAL_RATE_PER_SECOND,
                YamlConfig.config.server.PACKET_GLOBAL_BURST,
                YamlConfig.config.server.PACKET_MOVEMENT_RATE_PER_SECOND,
                YamlConfig.config.server.PACKET_COMBAT_RATE_PER_SECOND,
                YamlConfig.config.server.PACKET_CHAT_RATE_PER_SECOND,
                YamlConfig.config.server.PACKET_ECONOMY_RATE_PER_SECOND,
                YamlConfig.config.server.PACKET_OTHER_RATE_PER_SECOND,
                YamlConfig.config.server.PACKET_RATE_LIMIT_VIOLATIONS_BEFORE_DISCONNECT);
    }

    public synchronized Decision allow(short opcode, long nowMs) {
        PacketFamily family = PacketFamily.classify(opcode);
        int familyLimit = familyRates.get(family);
        TokenBucket global = globalBucket;
        TokenBucket familyBucket = familyBuckets.get(family);
        boolean globalAllowed = global.tryConsume(nowMs);
        boolean familyAllowed = familyBucket.tryConsume(nowMs);
        boolean allowed = globalAllowed && familyAllowed;
        if (allowed) {
            return new Decision(true, false, family, global.consumedInWindow(),
                    familyBucket.consumedInWindow(), familyLimit);
        }
        int violations = violationCounter.increment(nowMs, VIOLATION_WINDOW_MS);
        return new Decision(false, violations >= violationLimit, family, global.consumedInWindow(),
                familyBucket.consumedInWindow(), familyLimit);
    }

    private static int positive(int value) {
        return Math.max(1, value);
    }

    public record Decision(boolean allowed, boolean disconnect, PacketFamily family,
                           int globalCount, int familyCount, int familyLimit) {
    }

    private static final class Counter {
        private long startedMs;
        private int count;

        int increment(long nowMs, long windowMs) {
            if (startedMs == 0 || nowMs - startedMs >= windowMs) {
                startedMs = nowMs;
                count = 0;
            }
            return ++count;
        }
    }

    private static final class TokenBucket {
        private final int ratePerSecond;
        private final int capacity;
        private double tokens;
        private long lastRefillMs = -1;
        private long countWindowStartedMs = -1;
        private int consumedInWindow;

        TokenBucket(int ratePerSecond, int capacity) {
            this.ratePerSecond = ratePerSecond;
            this.capacity = capacity;
            this.tokens = capacity;
        }

        boolean tryConsume(long nowMs) {
            if (lastRefillMs < 0) {
                lastRefillMs = nowMs;
                countWindowStartedMs = nowMs;
            } else if (nowMs > lastRefillMs) {
                tokens = Math.min(capacity, tokens + (nowMs - lastRefillMs) * ratePerSecond / 1_000.0);
                lastRefillMs = nowMs;
            }
            if (nowMs - countWindowStartedMs >= WINDOW_MS) {
                countWindowStartedMs = nowMs;
                consumedInWindow = 0;
            }
            if (tokens < 1.0) {
                return false;
            }
            tokens -= 1.0;
            consumedInWindow++;
            return true;
        }

        int consumedInWindow() {
            return consumedInWindow;
        }
    }
}
