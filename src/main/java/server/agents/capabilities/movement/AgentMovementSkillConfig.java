package server.agents.capabilities.movement;

/**
 * Tuning owned by navigation-level movement skills.
 *
 * <p>Graphs always describe possible skill movement. Each skill mode decides
 * whether those edges are ignored, compared in shadow, or actively executed.</p>
 */
public final class AgentMovementSkillConfig {
    public static final AgentMovementSkillMode TELEPORT_MODE = AgentMovementSkillMode.parse(
            config.AgentTuning.stringValue(
                    "server.agents.capabilities.movement.AgentMovementSkillConfig.TELEPORT_MODE"),
            "TELEPORT_MODE");
    public static final AgentMovementSkillMode FLASH_JUMP_MODE = AgentMovementSkillMode.parse(
            config.AgentTuning.stringValue(
                    "server.agents.capabilities.movement.AgentMovementSkillConfig.FLASH_JUMP_MODE"),
            "FLASH_JUMP_MODE");
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
