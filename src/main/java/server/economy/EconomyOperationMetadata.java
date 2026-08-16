package server.economy;

import java.time.Instant;
import java.util.UUID;

/** Optional simulation attribution; ordinary human gameplay remains valid without a run. */
public record EconomyOperationMetadata(UUID runId, Instant logicalAt, String decisionId,
                                       String activityId, String configRevision,
                                       String catalogRevision, String reasonCode,
                                       boolean primaryIsAgent, boolean secondaryIsAgent,
                                       EconomyTaxOverride taxOverride) {
    public EconomyOperationMetadata(UUID runId, Instant logicalAt, String decisionId,
                                    String activityId, String configRevision,
                                    String catalogRevision, String reasonCode,
                                    boolean primaryIsAgent, boolean secondaryIsAgent) {
        this(runId, logicalAt, decisionId, activityId, configRevision, catalogRevision,
                reasonCode, primaryIsAgent, secondaryIsAgent, null);
    }
    public EconomyOperationMetadata {
        if ((runId == null) != (logicalAt == null))
            throw new IllegalArgumentException("run and logical time must be supplied together");
        decisionId = clean(decisionId); activityId = clean(activityId);
        configRevision = clean(configRevision); catalogRevision = clean(catalogRevision);
        reasonCode = clean(reasonCode);
    }

    public static EconomyOperationMetadata unattributed() {
        return new EconomyOperationMetadata(null, null, null, null, null, null, null,
                false, false, null);
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value; }
}
