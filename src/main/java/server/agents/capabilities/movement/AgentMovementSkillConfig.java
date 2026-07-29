package server.agents.capabilities.movement;

/**
 * Tuning owned by navigation-level movement skills.
 *
 * <p>Graph authoring, live execution, and shadow comparison are deliberately
 * independent. This lets the server validate new routes without silently
 * changing the movement of existing Agents.</p>
 */
public final class AgentMovementSkillConfig {
    public static final boolean TELEPORT_GRAPH_EDGES_ENABLED = config.AgentTuning.booleanValue(
            "server.agents.capabilities.movement.AgentMovementSkillConfig.TELEPORT_GRAPH_EDGES_ENABLED");
    public static final boolean FLASH_JUMP_GRAPH_EDGES_ENABLED = config.AgentTuning.booleanValue(
            "server.agents.capabilities.movement.AgentMovementSkillConfig.FLASH_JUMP_GRAPH_EDGES_ENABLED");
    public static final boolean TELEPORT_EXECUTION_ENABLED = config.AgentTuning.booleanValue(
            "server.agents.capabilities.movement.AgentMovementSkillConfig.TELEPORT_EXECUTION_ENABLED");
    public static final boolean FLASH_JUMP_EXECUTION_ENABLED = config.AgentTuning.booleanValue(
            "server.agents.capabilities.movement.AgentMovementSkillConfig.FLASH_JUMP_EXECUTION_ENABLED");
    public static final boolean SHADOW_DIAGNOSTICS_ENABLED = config.AgentTuning.booleanValue(
            "server.agents.capabilities.movement.AgentMovementSkillConfig.SHADOW_DIAGNOSTICS_ENABLED");
    public static final int MIN_MP_RESERVE_PERCENT = config.AgentTuning.intValue(
            "server.agents.capabilities.movement.AgentMovementSkillConfig.MIN_MP_RESERVE_PERCENT");
    public static final long CAST_COOLDOWN_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.movement.AgentMovementSkillConfig.CAST_COOLDOWN_MS");
    public static final int TELEPORT_RANGE_PX = config.AgentTuning.intValue(
            "server.agents.capabilities.movement.AgentMovementSkillConfig.TELEPORT_RANGE_PX");
    public static final int TELEPORT_Y_SNAP_PX = config.AgentTuning.intValue(
            "server.agents.capabilities.movement.AgentMovementSkillConfig.TELEPORT_Y_SNAP_PX");
    public static final int TELEPORT_EDGE_COST_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.movement.AgentMovementSkillConfig.TELEPORT_EDGE_COST_MS");
    public static final float FLASH_JUMP_HORIZONTAL_SPEED_PXS = config.AgentTuning.floatValue(
            "server.agents.capabilities.movement.AgentMovementSkillConfig.FLASH_JUMP_HORIZONTAL_SPEED_PXS");
    public static final float FLASH_JUMP_UPWARD_SPEED_PXS = config.AgentTuning.floatValue(
            "server.agents.capabilities.movement.AgentMovementSkillConfig.FLASH_JUMP_UPWARD_SPEED_PXS");
    public static final int MAX_GRAPH_ANCHORS_PER_REGION = config.AgentTuning.intValue(
            "server.agents.capabilities.movement.AgentMovementSkillConfig.MAX_GRAPH_ANCHORS_PER_REGION");
    public static final long SHADOW_LOG_INTERVAL_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.movement.AgentMovementSkillConfig.SHADOW_LOG_INTERVAL_MS");

    private AgentMovementSkillConfig() {
    }
}
