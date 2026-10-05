package server.agents.runtime;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Party gatherings, keyed by the owner the Agents gather around. A party buff only reaches the
 * members standing inside its box (StatEffect.applyBuff), so a rebuff asked for while the party is
 * spread out calls everyone to the owner first. While a gathering is open, each buffer in it holds
 * its party buffs until the whole party on the map is inside the box or the wait runs out.
 *
 * <p>Combat reads a gathering to decide when to cast; movement opens it, walks the Agents over,
 * and closes it once every buffer has finished, running the restore actions it registered.
 */
public final class AgentPartyGatherRegistry {
    private static final Map<Integer, Gathering> BY_OWNER = new ConcurrentHashMap<>();

    private AgentPartyGatherRegistry() {
    }

    public static final class Gathering {
        private final int ownerId;
        private final int mapId;
        private final long waitUntilMs;
        private final long closeByMs;
        private final Set<Integer> pendingBuffers = ConcurrentHashMap.newKeySet();
        private final Map<Integer, Runnable> restores = new ConcurrentHashMap<>();
        private final Map<Integer, Point> spots = new ConcurrentHashMap<>();
        private volatile int arrivalPx;
        private final AtomicBoolean watched = new AtomicBoolean();
        private volatile boolean gathered;

        Gathering(int ownerId, int mapId, long waitUntilMs, long closeByMs) {
            this.ownerId = ownerId;
            this.mapId = mapId;
            this.waitUntilMs = waitUntilMs;
            this.closeByMs = closeByMs;
        }

        public int ownerId() {
            return ownerId;
        }

        public int mapId() {
            return mapId;
        }

        /** Buffers still hold party buffs for stragglers until this time. */
        public boolean waitingForParty(long nowMs) {
            return nowMs < waitUntilMs;
        }

        /** Past this time the gathering is over whatever the buffers did. */
        public boolean expired(long nowMs) {
            return nowMs >= closeByMs;
        }

        public void addBuffer(int botId) {
            pendingBuffers.add(botId);
        }

        public void bufferFinished(int botId) {
            pendingBuffers.remove(botId);
        }

        public boolean buffersFinished() {
            return pendingBuffers.isEmpty();
        }

        /** Whether the party has been called over (as opposed to already standing together). */
        public boolean gathered() {
            return gathered;
        }

        public void markGathered() {
            gathered = true;
        }

        /** How to put an Agent back to what it was doing; kept only for Agents the gathering moved. */
        public void rememberRestore(int botId, Runnable restore) {
            restores.putIfAbsent(botId, restore);
        }

        /** The Agent was sent to {@code spot}; it counts as arrived within {@code arrivalPx}. */
        public void rememberSpot(int botId, Point spot, int arrivalPx) {
            spots.put(botId, new Point(spot));
            this.arrivalPx = arrivalPx;
        }

        /**
         * Still walking to its spot while the party is being waited for. A cast roots the caster for
         * its animation, so an Agent that buffs on the way never gets there.
         */
        public boolean stillWalking(int botId, Point position, long nowMs) {
            Point spot = spots.get(botId);
            return spot != null && position != null && waitingForParty(nowMs) && position.distance(spot) > arrivalPx;
        }

        /** True for the first caller only: one watcher closes the gathering. */
        public boolean startWatching() {
            return watched.compareAndSet(false, true);
        }

        List<Runnable> drainRestores() {
            List<Runnable> drained = List.copyOf(restores.values());
            restores.clear();
            return drained;
        }
    }

    /** The owner's open gathering on {@code mapId}, or a new one when there is none. */
    public static Gathering open(int ownerId, int mapId, long nowMs, long waitMs, long closeAfterMs) {
        return BY_OWNER.compute(ownerId, (id, current) ->
                current != null && current.mapId == mapId && !current.expired(nowMs)
                        ? current
                        : new Gathering(ownerId, mapId, nowMs + waitMs, nowMs + closeAfterMs));
    }

    /** The owner's gathering if one is open on {@code mapId}; an expired one counts as closed. */
    public static Gathering active(int ownerId, int mapId, long nowMs) {
        Gathering gathering = BY_OWNER.get(ownerId);
        return gathering != null && gathering.mapId == mapId && !gathering.expired(nowMs) ? gathering : null;
    }

    /** Ends the gathering (if it is still the owner's current one) and returns its restore actions. */
    public static List<Runnable> close(Gathering gathering) {
        if (gathering == null || !BY_OWNER.remove(gathering.ownerId, gathering)) {
            return List.of();
        }
        return gathering.drainRestores();
    }
}
