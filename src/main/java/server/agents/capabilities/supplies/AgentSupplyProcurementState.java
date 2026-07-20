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

    public enum Phase {
        IDLE,
        TRAVEL_TO_SUPPLIER,
        SHOPPING,
        RETURNING
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
        this.requestId = requestId;
        this.objectiveId = objectiveId;
        this.category = category;
        this.supplierMapId = supplierMapId;
        this.supplierNpcId = supplierNpcId;
        this.returnMapId = returnMapId;
        this.phase = phase;
        shopRequested = false;
    }

    public synchronized void markShopRequested() {
        shopRequested = true;
        phase = Phase.SHOPPING;
    }

    public synchronized void markReturning() {
        phase = Phase.RETURNING;
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
    }
}
