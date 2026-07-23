package server.agents.progression;

import java.util.List;

/** Executable contract whose ordered stages are implemented by the progression state machine. */
public record AgentVictoriaLevel15StageContract(
        int schemaVersion,
        String contractId,
        String title,
        String status,
        String catalogId,
        EntryCriteria entryCriteria,
        List<String> requiredCapabilityIds,
        List<Stage> stages,
        ExitCriteria exitCriteria) {

    public AgentVictoriaLevel15StageContract {
        if (schemaVersion <= 0 || blank(contractId) || blank(title) || blank(status) || blank(catalogId)
                || entryCriteria == null || requiredCapabilityIds == null || requiredCapabilityIds.isEmpty()
                || requiredCapabilityIds.stream().anyMatch(AgentVictoriaLevel15StageContract::blank)
                || stages == null || stages.isEmpty() || exitCriteria == null) {
            throw new IllegalArgumentException("a complete versioned Victoria plan card is required");
        }
        requiredCapabilityIds = List.copyOf(requiredCapabilityIds);
        stages = List.copyOf(stages);
    }

    public record EntryCriteria(
            int mapId,
            int jobId,
            int minimumLevel,
            int maximumLevel,
            int mesos,
            boolean careerBundleRequired,
            List<String> startVariantIds) {
        public EntryCriteria {
            if (mapId <= 0 || jobId < 0 || minimumLevel <= 0 || maximumLevel < minimumLevel
                    || mesos < 0 || startVariantIds == null || startVariantIds.isEmpty()
                    || startVariantIds.stream().anyMatch(AgentVictoriaLevel15StageContract::blank)) {
                throw new IllegalArgumentException("valid Victoria entry criteria are required");
            }
            startVariantIds = List.copyOf(startVariantIds);
        }
    }

    public record Stage(String stageId, List<String> capabilityIds, String postcondition) {
        public Stage {
            if (blank(stageId) || capabilityIds == null || capabilityIds.isEmpty()
                    || capabilityIds.stream().anyMatch(AgentVictoriaLevel15StageContract::blank)
                    || blank(postcondition)) {
                throw new IllegalArgumentException("a stage id, capabilities, and postcondition are required");
            }
            capabilityIds = List.copyOf(capabilityIds);
        }
    }

    public record ExitCriteria(
            int minimumLevel,
            boolean firstJobRequired,
            boolean allInstructorTrainingQuestsComplete,
            String finalMap,
            boolean groundedNearInstructor) {
        public ExitCriteria {
            if (minimumLevel <= 0 || blank(finalMap)) {
                throw new IllegalArgumentException("valid Victoria exit criteria are required");
            }
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
