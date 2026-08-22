package server.agents.capabilities.supplies;

import server.agents.runtime.state.AgentCapabilityStateKey;
import server.agents.capabilities.contracts.AgentResourceCategory;

public final class AgentSupplyProcurementState {
    public static final AgentCapabilityStateKey<AgentSupplyProcurementState> STATE_KEY =
            new AgentCapabilityStateKey<>("supplies.procurement-execution", AgentSupplyProcurementState.class,
                    AgentSupplyProcurementState::new);

    private String requestId = "";
    private String objectiveId = "";
    private boolean shopRequested;
    private AgentResourceCategory category;
    private Phase phase = Phase.IDLE;
    private int supplierMapId;
    private int supplierNpcId;
    private int returnMapId;
    private int quantityBefore;
    private int mesosBefore;
    private AgentSupplyProcurementOutcome lastOutcome;
    private int recoveryAttempts;
    private long recoveryStartedAtMs;
    private long recoveryDeadlineAtMs;
    private int recoveryMapId;
    private int recoveryBaselineMeso;
    private int recoveryTargetMeso;
    private String stalledReason = "";

    public enum Phase {
        IDLE,
        TRAVEL_TO_SUPPLIER,
        SHOPPING,
        RETURNING,
        RESTING,
        INCOME_RECOVERY,
        STALLED
    }

    public synchronized boolean isActive() {
        return !requestId.isBlank();
    }

    public synchronized String requestId() {
        return requestId;
    }

    public synchronized String objectiveId() {
        return objectiveId;
    }

    public synchronized boolean shopRequested() {
        return shopRequested;
    }

    public synchronized AgentResourceCategory category() {
        return category;
    }

    public synchronized Phase phase() {
        return phase;
    }

    public synchronized int supplierMapId() {
        return supplierMapId;
    }

    public synchronized int supplierNpcId() {
        return supplierNpcId;
    }

    public synchronized int returnMapId() {
        return returnMapId;
    }

    public synchronized int quantityBefore() {
        return quantityBefore;
    }

    public synchronized int mesosBefore() {
        return mesosBefore;
    }

    public synchronized AgentSupplyProcurementOutcome lastOutcome() {
        return lastOutcome;
    }

    public synchronized int recoveryAttempts() {
        return recoveryAttempts;
    }

    public synchronized long recoveryStartedAtMs() {
        return recoveryStartedAtMs;
    }

    public synchronized long recoveryDeadlineAtMs() {
        return recoveryDeadlineAtMs;
    }

    public synchronized int recoveryMapId() {
        return recoveryMapId;
    }

    public synchronized int recoveryBaselineMeso() {
        return recoveryBaselineMeso;
    }

    public synchronized int recoveryTargetMeso() {
        return recoveryTargetMeso;
    }

    public synchronized String stalledReason() {
        return stalledReason;
    }

    public synchronized void start(String requestId, String objectiveId, AgentResourceCategory category) {
        start(requestId, objectiveId, category, 0, 0, 0, Phase.SHOPPING);
    }

    public synchronized void start(String requestId,
                                   String objectiveId,
                                   AgentResourceCategory category,
                                   int supplierMapId,
                                   int supplierNpcId,
                                   int returnMapId,
                                   Phase phase) {
        start(requestId, objectiveId, category, supplierMapId, supplierNpcId,
                returnMapId, phase, 0, 0);
    }

    public synchronized void start(String requestId,
                                   String objectiveId,
                                   AgentResourceCategory category,
                                   int supplierMapId,
                                   int supplierNpcId,
                                   int returnMapId,
                                   Phase phase,
                                   int quantityBefore,
                                   int mesosBefore) {
        this.requestId = requestId;
        this.objectiveId = objectiveId;
        this.category = category;
        this.supplierMapId = supplierMapId;
        this.supplierNpcId = supplierNpcId;
        this.returnMapId = returnMapId;
        this.phase = phase;
        this.quantityBefore = Math.max(0, quantityBefore);
        this.mesosBefore = Math.max(0, mesosBefore);
        shopRequested = false;
        recoveryAttempts = 0;
        recoveryStartedAtMs = 0L;
        recoveryDeadlineAtMs = 0L;
        recoveryMapId = 0;
        recoveryBaselineMeso = 0;
        recoveryTargetMeso = 0;
        stalledReason = "";
    }

    public synchronized void complete(AgentSupplyProcurementOutcome outcome) {
        lastOutcome = outcome;
    }

    public synchronized void markShopRequested() {
        shopRequested = true;
        phase = Phase.SHOPPING;
    }

    public synchronized void markReturning() {
        phase = Phase.RETURNING;
    }

    public synchronized void beginRecovery(
            int mapId, int baselineMeso, int targetMeso, long nowMs, long deadlineAtMs) {
        recoveryAttempts++;
        recoveryStartedAtMs = nowMs;
        recoveryDeadlineAtMs = Math.max(nowMs, deadlineAtMs);
        recoveryMapId = mapId;
        recoveryBaselineMeso = Math.max(0, baselineMeso);
        recoveryTargetMeso = Math.max(recoveryBaselineMeso, targetMeso);
        shopRequested = false;
        phase = Phase.RESTING;
    }

    public synchronized void markResting(long nowMs, long deadlineAtMs) {
        recoveryStartedAtMs = nowMs;
        recoveryDeadlineAtMs = Math.max(nowMs, deadlineAtMs);
        phase = Phase.RESTING;
    }

    public synchronized void markIncomeRecovery(long nowMs, long deadlineAtMs) {
        recoveryStartedAtMs = nowMs;
        recoveryDeadlineAtMs = Math.max(nowMs, deadlineAtMs);
        phase = Phase.INCOME_RECOVERY;
    }

    public synchronized void retrySupplier(boolean supplierIsCurrentMap) {
        shopRequested = false;
        phase = supplierIsCurrentMap ? Phase.SHOPPING : Phase.TRAVEL_TO_SUPPLIER;
    }

    public synchronized void markStalled(String reason) {
        stalledReason = reason == null ? "" : reason.trim();
        phase = Phase.STALLED;
    }

    public synchronized void clear() {
        requestId = "";
        objectiveId = "";
        category = null;
        shopRequested = false;
        phase = Phase.IDLE;
        supplierMapId = 0;
        supplierNpcId = 0;
        returnMapId = 0;
        quantityBefore = 0;
        mesosBefore = 0;
        recoveryAttempts = 0;
        recoveryStartedAtMs = 0L;
        recoveryDeadlineAtMs = 0L;
        recoveryMapId = 0;
        recoveryBaselineMeso = 0;
        recoveryTargetMeso = 0;
        stalledReason = "";
    }
}
