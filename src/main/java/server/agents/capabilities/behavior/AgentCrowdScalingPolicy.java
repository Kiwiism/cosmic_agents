package server.agents.capabilities.behavior;

import server.agents.model.AgentPosition;
import server.agents.perception.AgentCharacterPerception;
import server.agents.perception.AgentPerceptionSnapshot;

/**
 * Pure crowd-pressure scaling shared by combat response, target diversity, and respite duration.
 * Personality supplies the Agent-relative value; live perception supplies the situational ceiling.
 */
public final class AgentCrowdScalingPolicy {
    private static final int RESPONSE_BASE_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.behavior.AgentCrowdScalingPolicy.RESPONSE_BASE_MS");
    private static final int RESPONSE_PER_CHARACTER_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.behavior.AgentCrowdScalingPolicy.RESPONSE_PER_CHARACTER_MS");
    private static final int RESPONSE_PER_CHARACTER_SQUARED_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.behavior.AgentCrowdScalingPolicy.RESPONSE_PER_CHARACTER_SQUARED_MS");
    private static final int RESPONSE_MAX_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.behavior.AgentCrowdScalingPolicy.RESPONSE_MAX_MS");
    private static final int RESPONSE_PROFILE_REFERENCE_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.behavior.AgentCrowdScalingPolicy.RESPONSE_PROFILE_REFERENCE_MS");
    private static final int QUIET_CHARACTER_THRESHOLD = config.AgentTuning.intValue(
            "server.agents.capabilities.behavior.AgentCrowdScalingPolicy.QUIET_CHARACTER_THRESHOLD");
    private static final int REST_BASE_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.behavior.AgentCrowdScalingPolicy.REST_BASE_MS");
    private static final int REST_PER_CHARACTER_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.behavior.AgentCrowdScalingPolicy.REST_PER_CHARACTER_MS");
    private static final int REST_PER_CHARACTER_SQUARED_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.behavior.AgentCrowdScalingPolicy.REST_PER_CHARACTER_SQUARED_MS");
    private static final int REST_MAX_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.behavior.AgentCrowdScalingPolicy.REST_MAX_MS");
    private static final int REST_PROFILE_REFERENCE_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.behavior.AgentCrowdScalingPolicy.REST_PROFILE_REFERENCE_MS");
    private static final int REST_MINIMUM_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.behavior.AgentCrowdScalingPolicy.REST_MINIMUM_MS");
    private static final int LOCAL_CROWD_RADIUS_PX = config.AgentTuning.intValue(
            "server.agents.capabilities.behavior.AgentCrowdScalingPolicy.LOCAL_CROWD_RADIUS_PX");
    private static final int TARGET_VARIATION_START_CHARACTERS = config.AgentTuning.intValue(
            "server.agents.capabilities.behavior.AgentCrowdScalingPolicy.TARGET_VARIATION_START_CHARACTERS");
    private static final int TARGET_VARIATION_FULL_CHARACTERS = config.AgentTuning.intValue(
            "server.agents.capabilities.behavior.AgentCrowdScalingPolicy.TARGET_VARIATION_FULL_CHARACTERS");

    private AgentCrowdScalingPolicy() {
    }

    public static int totalCharacters(AgentPerceptionSnapshot snapshot) {
        if (snapshot == null) {
            return 1;
        }
        if (!snapshot.characters().isEmpty()) {
            return Math.max(1, snapshot.characters().size());
        }
        return Math.max(1, snapshot.realPlayerObservers() + snapshot.agentPeers().size());
    }

    public static int localCharacters(AgentPerceptionSnapshot snapshot, AgentPosition center) {
        if (snapshot == null || center == null) {
            return 1;
        }
        if (snapshot.characters().isEmpty()) {
            return totalCharacters(snapshot);
        }
        long radiusSq = (long) LOCAL_CROWD_RADIUS_PX * LOCAL_CROWD_RADIUS_PX;
        return Math.max(1, (int) snapshot.characters().stream()
                .filter(character -> distanceSq(center, character) <= radiusSq)
                .count());
    }

    public static int responseDelayMs(int profileDelayMs,
                                      int responseJitterPercent,
                                      int totalCharacters) {
        int ceiling = quadraticCeiling(
                crowdCharacters(totalCharacters),
                RESPONSE_BASE_MS,
                RESPONSE_PER_CHARACTER_MS,
                RESPONSE_PER_CHARACTER_SQUARED_MS,
                RESPONSE_MAX_MS);
        long jittered = (long) Math.max(0, profileDelayMs)
                * (100L + Math.max(0, responseJitterPercent)) / 100L;
        return scaledDuration(jittered, RESPONSE_PROFILE_REFERENCE_MS, ceiling, 0);
    }

    public static int restDurationMs(int profileDurationMs, int totalCharacters) {
        if (totalCharacters <= QUIET_CHARACTER_THRESHOLD) {
            return 0;
        }
        int ceiling = quadraticCeiling(
                crowdCharacters(totalCharacters),
                REST_BASE_MS,
                REST_PER_CHARACTER_MS,
                REST_PER_CHARACTER_SQUARED_MS,
                REST_MAX_MS);
        return scaledDuration(
                Math.max(0, profileDurationMs),
                REST_PROFILE_REFERENCE_MS,
                ceiling,
                Math.min(REST_MINIMUM_MS, ceiling));
    }

    public static int targetVariationPercent(int localCharacters) {
        if (localCharacters <= TARGET_VARIATION_START_CHARACTERS) {
            return 0;
        }
        if (localCharacters >= TARGET_VARIATION_FULL_CHARACTERS) {
            return 100;
        }
        int width = Math.max(1,
                TARGET_VARIATION_FULL_CHARACTERS - TARGET_VARIATION_START_CHARACTERS);
        return (localCharacters - TARGET_VARIATION_START_CHARACTERS) * 100 / width;
    }

    public static int anchorPercent(int profileAnchorPercent, int localCharacters) {
        return Math.clamp(profileAnchorPercent, 0, 100)
                * targetVariationPercent(localCharacters) / 100;
    }

    static int responseCeilingMs(int totalCharacters) {
        return quadraticCeiling(
                crowdCharacters(totalCharacters),
                RESPONSE_BASE_MS,
                RESPONSE_PER_CHARACTER_MS,
                RESPONSE_PER_CHARACTER_SQUARED_MS,
                RESPONSE_MAX_MS);
    }

    static int restCeilingMs(int totalCharacters) {
        if (totalCharacters <= QUIET_CHARACTER_THRESHOLD) {
            return 0;
        }
        return quadraticCeiling(
                crowdCharacters(totalCharacters),
                REST_BASE_MS,
                REST_PER_CHARACTER_MS,
                REST_PER_CHARACTER_SQUARED_MS,
                REST_MAX_MS);
    }

    private static long distanceSq(AgentPosition center, AgentCharacterPerception character) {
        long dx = (long) center.x() - character.position().x();
        long dy = (long) center.y() - character.position().y();
        return dx * dx + dy * dy;
    }

    private static int quadraticCeiling(
            int characters, int base, int linear, int quadratic, int maximum) {
        long count = Math.max(0, characters);
        long calculated = Math.max(0, base)
                + count * Math.max(0, linear)
                + count * count * Math.max(0, quadratic);
        return (int) Math.min(Math.max(0, maximum), calculated);
    }

    private static int crowdCharacters(int totalCharacters) {
        return Math.max(0, totalCharacters - QUIET_CHARACTER_THRESHOLD);
    }

    private static int scaledDuration(
            long profileDuration, int profileReference, int ceiling, int minimum) {
        if (profileDuration <= 0 || ceiling <= 0) {
            return 0;
        }
        long scaled = profileDuration * ceiling / Math.max(1, profileReference);
        return (int) Math.min(ceiling, Math.max(minimum, scaled));
    }
}
