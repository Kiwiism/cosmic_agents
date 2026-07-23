package server.agents.progression;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Loads the executable level-15 plan contract and validates its catalog/capability references. */
public final class AgentVictoriaLevel15StageContractRepository {
    private static final String DEFAULT_RESOURCE =
            "/agents/progression/victoria-level15-stage-contract.json";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final List<String> RUNTIME_STAGE_ORDER = List.of(
            "complete-biggs-at-olaf", "complete-olaf-lesson", "start-career-path",
            "pre-job-level-grind", "take-taxi", "enter-instructor", "complete-career-path",
            "advance-first-job", "allocate-initial-ap-sp",
            "initial-shop-trip", "return-to-instructor", "instructor-training",
            "home-quest-pack", "post-home-decision", "rotation-quest-pack",
            "fallback-grind", "final-return");
    private static final AgentVictoriaLevel15StageContract DEFAULT = loadResource(DEFAULT_RESOURCE);

    private AgentVictoriaLevel15StageContractRepository() {
    }

    public static AgentVictoriaLevel15StageContract defaultContract() {
        return DEFAULT;
    }

    private static AgentVictoriaLevel15StageContract loadResource(String resourcePath) {
        try (InputStream input =
                     AgentVictoriaLevel15StageContractRepository.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("missing Victoria level-15 plan card: " + resourcePath);
            }
            AgentVictoriaLevel15StageContract contract =
                    MAPPER.readValue(input, AgentVictoriaLevel15StageContract.class);
            validate(contract, AgentVictoriaLevel15CatalogRepository.defaultRepository().catalog());
            return contract;
        } catch (IOException failure) {
            throw new IllegalStateException("could not load Victoria level-15 plan card: " + resourcePath, failure);
        }
    }

    private static void validate(
            AgentVictoriaLevel15StageContract contract, AgentVictoriaLevel15Catalog catalog) {
        if (!"executable".equals(contract.status())) {
            throw new IllegalArgumentException("Victoria level-15 stage contract must be executable");
        }
        if (!catalog.catalogId().equals(contract.catalogId())) {
            throw new IllegalArgumentException("Victoria stage contract references catalog "
                    + contract.catalogId()
                    + " but runtime loaded " + catalog.catalogId());
        }
        List<String> stageOrder = contract.stages().stream()
                .map(AgentVictoriaLevel15StageContract.Stage::stageId)
                .toList();
        if (!RUNTIME_STAGE_ORDER.equals(stageOrder)) {
            throw new IllegalArgumentException("Victoria plan stage order is not implemented by the runtime: "
                    + stageOrder);
        }
        Set<String> required = Set.copyOf(contract.requiredCapabilityIds());
        Set<String> stageIds = new HashSet<>();
        for (AgentVictoriaLevel15StageContract.Stage stage : contract.stages()) {
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
