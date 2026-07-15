package server.agents.profiles;

import server.agents.capabilities.movement.fidget.AgentFidgetMode;

import java.util.Set;

public record AgentBehaviorProfile(
        int schemaVersion,
        String profileId,
        int profileVersion,
        Presentation presentation) {

    public AgentBehaviorProfile {
        if (schemaVersion <= 0 || profileVersion <= 0 || profileId == null || profileId.isBlank()
                || presentation == null) {
            throw new IllegalArgumentException("behavior profile identity and presentation are required");
        }
    }

    public record Presentation(
            Timing timing,
            Movement movement,
            Encounter encounter,
            Rest rest) {
        public Presentation {
            if (timing == null || movement == null || encounter == null || rest == null) {
                throw new IllegalArgumentException("behavior profile presentation sections are required");
            }
        }
    }

    public record Timing(DelayRange beforeNpcInteractionMs, DelayRange betweenObjectivesMs) {
        public Timing {
            if (beforeNpcInteractionMs == null || betweenObjectivesMs == null) {
                throw new IllegalArgumentException("behavior profile timing ranges are required");
            }
        }
    }

    public record Movement(
            boolean navigationFidgetsEnabled,
            Set<AgentFidgetMode> navigationFidgetModes,
            DelayRange fidgetCooldownMs,
            DelayRange fidgetDurationMs) {
        public Movement {
            navigationFidgetModes = navigationFidgetModes == null
                    ? Set.of() : Set.copyOf(navigationFidgetModes);
            if (fidgetCooldownMs == null || fidgetDurationMs == null
                    || navigationFidgetModes.contains(AgentFidgetMode.NONE)) {
                throw new IllegalArgumentException("valid movement presentation settings are required");
            }
        }
    }

    public record Encounter(String style, int maxEstimatedHits) {
        public Encounter {
            if (style == null || style.isBlank() || maxEstimatedHits < 0) {
                throw new IllegalArgumentException("valid encounter presentation settings are required");
            }
        }
    }

    public record Rest(String spotPreference) {
        public Rest {
            if (spotPreference == null || spotPreference.isBlank()) {
                throw new IllegalArgumentException("a rest spot preference is required");
            }
        }
    }

    public record DelayRange(int min, int max) {
        public DelayRange {
            if (min < 0 || max < min) {
                throw new IllegalArgumentException("delay range must be non-negative and ordered");
            }
        }
    }
}
