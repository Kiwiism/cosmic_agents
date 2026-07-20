package server.agents.personality;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.model.AgentIdentity;

import java.io.IOException;

/** Restores a stable personality independent from career and plan assignment. */
public final class AgentPersonalityAssignmentService {
    private static final Logger log = LoggerFactory.getLogger(AgentPersonalityAssignmentService.class);
    private static final AgentPersonalityAssignmentStore STORE =
            FileAgentPersonalityAssignmentStore.runtimeDefault();

    private AgentPersonalityAssignmentService() {
    }

    public static AgentPersonalityProfile restoreOrAssign(AgentPersonalityState state,
                                                           AgentIdentity identity,
                                                           boolean presentationEnabled,
                                                           long nowMs) {
        if (!valid(state, identity)) {
            return null;
        }
        try {
            return restoreOrAssign(state, identity, presentationEnabled, nowMs,
                    AgentPersonalityProfileRepository.defaultRepository(), STORE);
        } catch (IOException | RuntimeException failure) {
            log.warn("Could not restore Agent personality for {} ({})",
                    identity.name(), identity.id().characterId(), failure);
            return null;
        }
    }

    static AgentPersonalityProfile restoreOrAssign(
            AgentPersonalityState state,
            AgentIdentity identity,
            boolean presentationEnabled,
            long nowMs,
            AgentPersonalityProfileRepository profiles,
            AgentPersonalityAssignmentStore store) throws IOException {
        if (!valid(state, identity)) {
            throw new IllegalArgumentException("Agent identity and personality state are required");
        }
        int characterId = identity.id().characterId();
        AgentPersonalityAssignment assignment = store.load(characterId).orElse(null);
        AgentPersonalityProfile profile;
        if (assignment == null) {
            profile = profiles.deterministicFor(characterId);
            assignment = new AgentPersonalityAssignment(
                    1,
                    characterId,
                    identity.name(),
                    profile.profileId(),
                    profile.profileVersion(),
                    behaviorSeed(characterId, profile.profileId()),
                    nowMs);
            store.save(assignment);
        } else {
            AgentPersonalityAssignment restored = assignment;
            profile = profiles.find(restored.personalityProfileId())
                    .orElseThrow(() -> new IOException("assigned personality profile no longer exists: "
                            + restored.personalityProfileId()));
            if (profile.profileVersion() != restored.personalityProfileVersion()) {
                throw new IOException("assigned personality profile version changed without migration: "
                        + restored.personalityProfileId());
            }
        }
        state.assign(assignment, profile, presentationEnabled);
        return profile;
    }

    private static boolean valid(AgentPersonalityState state, AgentIdentity identity) {
        return state != null && identity != null && identity.id() != null
                && identity.id().characterId() > 0
                && identity.name() != null && !identity.name().isBlank();
    }

    static long behaviorSeed(int characterId, String profileId) {
        long value = Integer.toUnsignedLong(characterId) << 32;
        value ^= Integer.toUnsignedLong(profileId.hashCode());
        value += 0x9E3779B97F4A7C15L;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
