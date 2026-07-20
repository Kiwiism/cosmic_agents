package server.agents.progression;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AgentCareerBuildBundleRepository {
    private static final String DEFAULT_RESOURCE = "/agents/profiles/career-build-bundles.json";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final AgentCareerBuildBundleRepository DEFAULT = loadResource(DEFAULT_RESOURCE);

    private final List<AgentCareerBuildBundle> bundles;
    private final Map<String, AgentCareerBuildBundle> byId;

    AgentCareerBuildBundleRepository(AgentCareerBuildBundleCatalog catalog) {
        Map<String, AgentCareerBuildBundle> indexed = new LinkedHashMap<>();
        for (AgentCareerBuildBundle bundle : catalog.bundles()) {
            if (indexed.putIfAbsent(bundle.bundleId(), bundle) != null) {
                throw new IllegalArgumentException("duplicate career build bundle: " + bundle.bundleId());
            }
        }
        bundles = List.copyOf(indexed.values());
        byId = Map.copyOf(indexed);
    }

    public static AgentCareerBuildBundleRepository defaultRepository() {
        return DEFAULT;
    }

    public List<AgentCareerBuildBundle> all() {
        return bundles;
    }

    public Optional<AgentCareerBuildBundle> find(String bundleId) {
        return Optional.ofNullable(byId.get(bundleId));
    }

    private static AgentCareerBuildBundleRepository loadResource(String resourcePath) {
        try (InputStream input = AgentCareerBuildBundleRepository.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("missing Agent career build bundles: " + resourcePath);
            }
            return new AgentCareerBuildBundleRepository(
                    MAPPER.readValue(input, AgentCareerBuildBundleCatalog.class));
        } catch (IOException failure) {
            throw new IllegalStateException("could not load Agent career build bundles: " + resourcePath, failure);
        }
    }
}
