package server.agents.runtime.autonomy;

import server.agents.model.AgentSnapshot;
import server.agents.perception.AgentPerceptionSnapshot;

/**
 * Immutable facts used for one top-level autonomy choice.
 *
 * <p>The snapshot deliberately excludes live Cosmic objects so the decision can
 * be explained, tested, persisted, and eventually replayed.</p>
 */
public record AgentAutonomySnapshot(
        long sequence,
        long capturedAtMs,
        AgentSnapshot agent,
        AgentPerceptionSnapshot perception) {

    public AgentAutonomySnapshot {
        if (sequence <= 0 || capturedAtMs < 0 || agent == null || perception == null) {
            throw new IllegalArgumentException("A sequenced autonomy snapshot is required");
        }
    }
}
