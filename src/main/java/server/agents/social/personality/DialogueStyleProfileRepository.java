package server.agents.social.personality;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/** Loads communication styles independently from operational personality traits. */
public final class DialogueStyleProfileRepository {
    private static final String RESOURCE = "/agents/social/dialogue-style-profiles.json";
    private static final DialogueStyleProfileRepository DEFAULT = load();

    private final String defaultPersonalityProfileId;
    private final Map<String, DialogueStyleProfile> profiles;

    DialogueStyleProfileRepository(DialogueStyleCatalog catalog) {
        defaultPersonalityProfileId = catalog.defaultPersonalityProfileId();
        Map<String, DialogueStyleProfile> index = new LinkedHashMap<>();
        for (DialogueStyleProfile profile : catalog.profiles()) {
            if (index.putIfAbsent(profile.personalityProfileId(), profile) != null) {
                throw new IllegalArgumentException("duplicate dialogue style for "
                        + profile.personalityProfileId());
            }
        }
        if (!index.containsKey(defaultPersonalityProfileId)) {
            throw new IllegalArgumentException("unknown default dialogue style profile");
        }
        profiles = Map.copyOf(index);
    }

    public static DialogueStyleProfileRepository defaultRepository() {
        return DEFAULT;
    }

    public DialogueStyleProfile resolve(String personalityProfileId) {
        return profiles.getOrDefault(personalityProfileId, profiles.get(defaultPersonalityProfileId));
    }

    private static DialogueStyleProfileRepository load() {
        try (InputStream input = DialogueStyleProfileRepository.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing dialogue styles: " + RESOURCE);
            }
            return new DialogueStyleProfileRepository(
                    new ObjectMapper().readValue(input, DialogueStyleCatalog.class));
        } catch (IOException failure) {
            throw new IllegalStateException("could not load dialogue styles", failure);
        }
    }
}
