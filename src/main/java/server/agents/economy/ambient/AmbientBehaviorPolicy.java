package server.agents.economy.ambient;

import java.time.Instant;
import java.util.Optional;

/** Replaceable policy seam; ambient actions cannot change economic holdings. */
public interface AmbientBehaviorPolicy {
    Optional<AmbientAction> choose(Context context);

    record Context(String agentId, Instant logicalTime, int mapId, boolean ownsOpenStall,
                   boolean negotiating, boolean hasChair, boolean seated, int consecutiveActions) { }

    record AmbientAction(Type type, Integer chairItemId, String reason) {
        public enum Type { IDLE, FIDGET, SIT, STAND, SHORT_WALK }
    }
}
