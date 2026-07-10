package server.agents.capabilities.follow;

import client.Character;

import java.util.ArrayList;
import java.util.List;

public final class AgentFollowTargetResolutionService {
    private static final int MIN_PREFIX_TARGET_LENGTH = 2;

    private AgentFollowTargetResolutionService() {
    }

    public record Hooks(FollowTargetCandidates followTargetCandidates,
                        LeaderMessage leaderMessage) {
    }

    @FunctionalInterface
    public interface FollowTargetCandidates {
        List<Character> candidates(Character leader);
    }

    @FunctionalInterface
    public interface LeaderMessage {
        void send(Character leader, String message);
    }

    public static Character resolveFollowTarget(Character leader, String targetToken, Hooks hooks) {
        if (leader == null || targetToken == null || targetToken.isBlank()) {
            if (leader != null) {
                hooks.leaderMessage().send(leader, "Can't follow that target.");
            }
            return null;
        }

        List<Character> candidates = hooks.followTargetCandidates().candidates(leader);
        if (candidates == null) {
            candidates = List.of();
        }

        for (Character candidate : candidates) {
            if (candidate.getName().equalsIgnoreCase(targetToken)) {
                return candidate;
            }
        }

        if (targetToken.length() < MIN_PREFIX_TARGET_LENGTH) {
            hooks.leaderMessage().send(leader, "Follow target must use at least "
                    + MIN_PREFIX_TARGET_LENGTH + " letters.");
            return null;
        }

        List<Character> prefixMatches = new ArrayList<>();
        for (Character candidate : candidates) {
            if (candidate.getName().regionMatches(true, 0, targetToken, 0, targetToken.length())) {
                prefixMatches.add(candidate);
            }
        }
        if (prefixMatches.size() == 1) {
            return prefixMatches.get(0);
        }
        if (prefixMatches.size() > 1) {
            StringBuilder message = new StringBuilder("Ambiguous follow target '")
                    .append(targetToken)
                    .append("': ");
            for (int i = 0; i < prefixMatches.size(); i++) {
                if (i > 0) {
                    message.append(", ");
                }
                message.append(prefixMatches.get(i).getName());
            }
            hooks.leaderMessage().send(leader, message.toString());
            return null;
        }

        hooks.leaderMessage().send(leader, "Can't follow '" + targetToken
                + "'. Target must be a same-party character or one of your active bots.");
        return null;
    }
}
