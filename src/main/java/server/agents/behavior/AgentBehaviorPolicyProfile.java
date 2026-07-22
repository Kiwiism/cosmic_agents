package server.agents.behavior;

/** Tunable execution policy projected from a durable personality identity. */
public record AgentBehaviorPolicyProfile(
        String personalityProfileId,
        int version,
        Response response,
        Targeting targeting,
        Crowd crowd,
        Idle idle,
        Reactions reactions,
        Navigation navigation) {

    public AgentBehaviorPolicyProfile {
        if (personalityProfileId == null || personalityProfileId.isBlank() || version <= 0
                || response == null || targeting == null || crowd == null || idle == null
                || reactions == null || navigation == null) {
            throw new IllegalArgumentException("complete versioned behavior policy is required");
        }
    }

    public record Response(int minMs, int maxMs) {
        public Response {
            if (minMs < 0 || maxMs < minMs) throw new IllegalArgumentException("invalid response range");
        }
    }

    public record Targeting(int bestWeight, int nearWeight, int middleWeight,
                            int claimTolerance, int anchorPercent) {
        public Targeting {
            if (bestWeight < 0 || nearWeight < 0 || middleWeight < 0
                    || bestWeight + nearWeight + middleWeight <= 0
                    || claimTolerance < 1 || anchorPercent < 0 || anchorPercent > 100) {
                throw new IllegalArgumentException("invalid target policy");
            }
        }
    }

    public record Crowd(int avoidPercent, int minRestMs, int maxRestMs, int chairPercent) {
        public Crowd {
            if (avoidPercent < 0 || avoidPercent > 100 || minRestMs < 0 || maxRestMs < minRestMs
                    || chairPercent < 0 || chairPercent > 100) {
                throw new IllegalArgumentException("invalid crowd policy");
            }
        }
    }

    public record Idle(int waitWeight, int patrolWeight, int jumpWeight,
                       int proneWeight, int emptyAttackWeight) {
        public Idle {
            if (waitWeight < 0 || patrolWeight < 0 || jumpWeight < 0 || proneWeight < 0
                    || emptyAttackWeight < 0
                    || waitWeight + patrolWeight + jumpWeight + proneWeight + emptyAttackWeight <= 0) {
                throw new IllegalArgumentException("invalid idle policy");
            }
        }

    }

    public record Reactions(int missEmotePercent, int restEmotePercent, int cooldownMs) {
        public Reactions {
            if (missEmotePercent < 0 || missEmotePercent > 100 || restEmotePercent < 0
                    || restEmotePercent > 100 || cooldownMs < 0) {
                throw new IllegalArgumentException("invalid reaction policy");
            }
        }
    }

    public record Navigation(int alternateRoutePercent, int travelJumpPercent, double maxRouteStretch) {
        public Navigation {
            if (alternateRoutePercent < 0 || alternateRoutePercent > 100 || travelJumpPercent < 0
                    || travelJumpPercent > 100 || maxRouteStretch < 1.0) {
                throw new IllegalArgumentException("invalid navigation policy");
            }
        }
    }
}
