package server.agents.social.personality;

import java.util.List;

record DialogueStyleCatalog(
        int schemaVersion,
        String defaultPersonalityProfileId,
        List<DialogueStyleProfile> profiles) {
    DialogueStyleCatalog {
        if (schemaVersion <= 0 || defaultPersonalityProfileId == null
                || defaultPersonalityProfileId.isBlank() || profiles == null || profiles.isEmpty()) {
            throw new IllegalArgumentException("Valid dialogue style catalog is required");
        }
        profiles = List.copyOf(profiles);
    }
}
