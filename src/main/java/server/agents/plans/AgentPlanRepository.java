package server.agents.plans;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Strict classpath repository for every executable Agent plan. */
public final class AgentPlanRepository implements AgentPlanLibrary {
    private static final String INDEX_RESOURCE = "/agents/plans/index.json";
    private static final AgentPlanRepository DEFAULT = loadDefault();

    private final Map<String, AgentPlanDefinition> plans;

    AgentPlanRepository(List<AgentPlanDefinition> definitions) {
        Map<String, AgentPlanDefinition> loaded = new LinkedHashMap<>();
        for (AgentPlanDefinition definition : definitions) {
            AgentPlanSchemaValidator.validate(definition);
            if (loaded.putIfAbsent(definition.planId(), definition) != null) {
                throw new AgentPlanValidationException("duplicate planId " + definition.planId());
            }
        }
        for (AgentPlanDefinition definition : loaded.values()) {
            for (AgentPlanDefinition.Successor successor : definition.successors()) {
                if (!loaded.containsKey(successor.planId())) {
                    throw new AgentPlanValidationException("plan " + definition.planId()
                            + " references missing successor " + successor.planId());
                }
            }
        }
        plans = Map.copyOf(loaded);
    }

    public static AgentPlanRepository defaultRepository() {
        return DEFAULT;
    }

    public Optional<AgentPlanDefinition> find(String planId) {
        return Optional.ofNullable(plans.get(planId));
    }

    public AgentPlanDefinition require(String planId) {
        return find(planId).orElseThrow(() ->
                new AgentPlanValidationException("unknown Agent plan " + planId));
    }

    public List<AgentPlanDefinition> all() {
        return List.copyOf(plans.values());
    }

    private static AgentPlanRepository loadDefault() {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        try (InputStream input = AgentPlanRepository.class.getResourceAsStream(INDEX_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing Agent plan index " + INDEX_RESOURCE);
            }
            PlanIndex index = mapper.readValue(input, PlanIndex.class);
            if (index.schemaVersion() != AgentPlanSchemaValidator.CURRENT_SCHEMA_VERSION
                    || index.resources() == null || index.resources().isEmpty()) {
                throw new AgentPlanValidationException("invalid Agent plan index");
            }
            List<AgentPlanDefinition> plans = index.resources().stream()
                    .map(resource -> load(mapper, resource))
                    .toList();
            return new AgentPlanRepository(plans);
        } catch (IOException failure) {
            throw new IllegalStateException("could not load Agent plan index", failure);
        }
    }

    private static AgentPlanDefinition load(ObjectMapper mapper, String resource) {
        String path = resource.startsWith("/") ? resource : "/agents/plans/" + resource;
        try (InputStream input = AgentPlanRepository.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("missing Agent plan " + path);
            }
            return mapper.readValue(input, AgentPlanDefinition.class);
        } catch (IOException failure) {
            throw new IllegalStateException("could not load Agent plan " + path, failure);
        }
    }

    private record PlanIndex(int schemaVersion, List<String> resources) {
    }
}
