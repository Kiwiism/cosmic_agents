package server.agents.capabilities.supplies;

import server.agents.capabilities.contracts.AgentResourceCategory;
import server.agents.capabilities.contracts.AgentSupplyUrgency;
import server.agents.coordination.AgentCoordinationRuntime;
import server.agents.coordination.AgentSupplyNeedMessage;
import server.agents.events.AgentEvent;
import server.agents.events.AgentEventListener;

/** Projects low-supply facts into structured Agent-to-Agent coordination. */
public final class AgentSupplyCoordinationProjectionService implements AgentEventListener<AgentEvent> {
    @Override
    public void onAgentEvent(AgentEvent event) {
        if (!(event instanceof AgentSupplyThresholdChangedEvent threshold)
                || threshold.urgency().ordinal() < AgentSupplyUrgency.LOW.ordinal()) {
            return;
        }
        AgentSupplyNeedMessage.SupplyKind kind = supplyKind(threshold.category());
        if (kind == null) {
            return;
        }
        AgentCoordinationRuntime.publish(new AgentSupplyNeedMessage(
                threshold.agentId(),
                threshold.cohortId(),
                threshold.mapId(),
                kind,
                threshold.currentQuantity(),
                equipmentContext(threshold.category()),
                threshold.occurredAtMs()));
    }

    private static AgentSupplyNeedMessage.SupplyKind supplyKind(AgentResourceCategory category) {
        return switch (category) {
            case HP_POTION -> AgentSupplyNeedMessage.SupplyKind.HP_POTION;
            case MP_POTION -> AgentSupplyNeedMessage.SupplyKind.MP_POTION;
            case ARROW, CROSSBOW_BOLT, THROWING_STAR, BULLET ->
                    AgentSupplyNeedMessage.SupplyKind.AMMUNITION;
            default -> null;
        };
    }

    private static String equipmentContext(AgentResourceCategory category) {
        return switch (category) {
            case ARROW -> "BOW";
            case CROSSBOW_BOLT -> "CROSSBOW";
            case THROWING_STAR -> "CLAW";
            case BULLET -> "GUN";
            default -> "";
        };
    }
}
