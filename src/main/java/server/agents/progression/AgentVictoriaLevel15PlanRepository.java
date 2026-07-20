package server.agents.progression;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Loads the executable level-15 plan contract and validates its catalog/capability references. */
public final class AgentVictoriaLevel15PlanRepository {
    private static final String DEFAULT_RESOURCE = "/agents/plans/victoria-level15-mvp.plan.json";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final List<String> RUNTIME_STAGE_ORDER = List.of(
            "complete-biggs-at-olaf", "complete-olaf-lesson", "start-career-path",
            "pre-job-level-grind", "take-taxi", "enter-instructor", "complete-career-path",
            "advance-first-job", "allocate-initial-ap-sp",
            "initial-shop-trip", "return-to-instructor", "instructor-training",
            "home-quest-pack", "post-home-decision", "rotation-quest-pack",
            "fallback-grind", "final-return");
    private static final AgentVictoriaLevel15PlanCard DEFAULT = loadResource(DEFAULT_RESOURCE);

    private AgentVictoriaLevel15PlanRepository() {
    }

    public static AgentVictoriaLevel15PlanCard defaultPlan() {
        return DEFAULT;
    }

    private static AgentVictoriaLevel15PlanCard loadResource(String resourcePath) {
        try (InputStream input = AgentVictoriaLevel15PlanRepository.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("missing Victoria level-15 plan card: " + resourcePath);
            }
            AgentVictoriaLevel15PlanCard plan = MAPPER.readValue(input, AgentVictoriaLevel15PlanCard.class);
            validate(plan, AgentVictoriaLevel15CatalogRepository.defaultRepository().catalog());
            return plan;
        } catch (IOException failure) {
            throw new IllegalStateException("could not load Victoria level-15 plan card: " + resourcePath, failure);
        }
    }

    private static void validate(AgentVictoriaLevel15PlanCard plan, AgentVictoriaLevel15Catalog catalog) {
        if (!"executable".equals(plan.status())) {
            throw new IllegalArgumentException("Victoria level-15 plan must be executable");
        }
        if (!catalog.catalogId().equals(plan.catalogId())) {
            throw new IllegalArgumentException("Victoria plan references catalog " + plan.catalogId()
                    + " but runtime loaded " + catalog.catalogId());
        }
        List<String> stageOrder = plan.stages().stream()
                .map(AgentVictoriaLevel15PlanCard.Stage::stageId)
                .toList();
        if (!RUNTIME_STAGE_ORDER.equals(stageOrder)) {
            throw new IllegalArgumentException("Victoria plan stage order is not implemented by the runtime: "
                    + stageOrder);
        }
        Set<String> required = Set.copyOf(plan.requiredCapabilityIds());
        Set<String> stageIds = new HashSet<>();
        for (AgentVictoriaLevel15PlanCard.Stage stage : plan.stages()) {
            if (!stageIds.add(stage.stageId())) {
                throw new IllegalArgumentException("duplicate Victoria plan stage: " + stage.stageId());
            }
            for (String capabilityId : stage.capabilityIds()) {
                if (!required.contains(capabilityId)) {
                    throw new IllegalArgumentException("stage " + stage.stageId()
                            + " uses undeclared capability " + capabilityId);
                }
            }
        }
    }
}
