package server.agents.social.policy;

import client.Character;
import server.agents.runtime.AgentRuntimeHandle;

import java.util.List;
import java.util.function.ToIntFunction;

/** Deterministically elects at most one responder for untargeted social chat. */
public final class AgentSocialResponderElection {
    private AgentSocialResponderElection() {
    }

    public static <E extends AgentRuntimeHandle> E elect(
            Character speaker,
            List<E> candidates,
            String message,
            ToIntFunction<E> agentId) {
        if (speaker == null || candidates == null || candidates.isEmpty() || agentId == null) {
            return null;
        }
        int seed = 31 * speaker.getId() + (message == null ? 0 : message.toLowerCase().hashCode());
        E selected = null;
        long selectedScore = Long.MAX_VALUE;
        for (E candidate : candidates) {
            int candidateId = agentId.applyAsInt(candidate);
            if (candidateId <= 0) {
                continue;
            }
            long score = Integer.toUnsignedLong(mix(seed ^ candidateId));
            if (score < selectedScore) {
                selected = candidate;
                selectedScore = score;
            }
        }
        return selected;
    }

    private static int mix(int value) {
        int mixed = value ^ (value >>> 16);
        mixed *= 0x7feb352d;
        mixed ^= mixed >>> 15;
        mixed *= 0x846ca68b;
        return mixed ^ (mixed >>> 16);
    }
}
