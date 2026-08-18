package server.agents.capabilities.mobcontrol;

/** Monster-reaction and simulation tuning owned by the mob-control capability. */
public final class AgentMobPhysicsConfig {
    public static final Config cfg = new Config();

    private AgentMobPhysicsConfig() {
    }

    public static Config config() {
        return cfg;
    }

    public static class Config {
        public float KNOCKBACK_HSPEED = config.AgentTuning.floatValue(
                "server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.KNOCKBACK_HSPEED");
        public float KNOCKBACK_VFORCE = config.AgentTuning.floatValue(
                "server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.KNOCKBACK_VFORCE");
        public AgentMobReactionMode AGENT_MOB_REACTION_MODE = AgentMobReactionMode.parse(
                config.AgentYamlConfig.config.agent.AGENT_MOB_REACTION_MODE);
        public int MOB_PHYSICS_PUBLICATION_INTERVAL_MS =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_PUBLICATION_INTERVAL_MS;
        public int MOB_PHYSICS_MAX_CATCH_UP_STEPS =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_MAX_CATCH_UP_STEPS;
        public int MOB_PHYSICS_STOP_DISTANCE_X =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_STOP_DISTANCE_X;
        public int MOB_PHYSICS_RESUME_DISTANCE_X =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_RESUME_DISTANCE_X;
        public int MOB_PHYSICS_FLY_DEAD_ZONE_X =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_FLY_DEAD_ZONE_X;
        public int MOB_PHYSICS_FLY_DEAD_ZONE_Y =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_FLY_DEAD_ZONE_Y;
        public int MOB_PHYSICS_JUMP_COOLDOWN_MS =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_JUMP_COOLDOWN_MS;
        public int MOB_PHYSICS_JUMP_COOLDOWN_JITTER_MS =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_JUMP_COOLDOWN_JITTER_MS;
        public int MOB_PHYSICS_JUMP_TARGET_HEIGHT =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_JUMP_TARGET_HEIGHT;
        public int MOB_PHYSICS_MAX_SAFE_EDGE_PX =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_MAX_SAFE_EDGE_PX;
        public int MOB_PHYSICS_LEFT_EDGE_INSET_PX =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_LEFT_EDGE_INSET_PX;
        public int MOB_PHYSICS_RIGHT_EDGE_INSET_PX =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_RIGHT_EDGE_INSET_PX;
        public int MOB_PHYSICS_SPEED_PERCENT =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_SPEED_PERCENT;
        public int MOB_PHYSICS_BEHAVIOR_JITTER_MS =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_BEHAVIOR_JITTER_MS;
        public int MOB_PHYSICS_DIRECTION_REACTION_MAX_MS =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_DIRECTION_REACTION_MAX_MS;
        public int MOB_PHYSICS_EDGE_RETREAT_CHANCE_PERCENT =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_EDGE_RETREAT_CHANCE_PERCENT;
        public int MOB_PHYSICS_EDGE_IDLE_MIN_MS =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_EDGE_IDLE_MIN_MS;
        public int MOB_PHYSICS_EDGE_IDLE_MAX_MS =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_EDGE_IDLE_MAX_MS;
        public int MOB_PHYSICS_EDGE_RETREAT_MIN_MS =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_EDGE_RETREAT_MIN_MS;
        public int MOB_PHYSICS_EDGE_RETREAT_MAX_MS =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_EDGE_RETREAT_MAX_MS;
        public int MOB_PHYSICS_RETREAT_MIN_DISTANCE_PX =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_RETREAT_MIN_DISTANCE_PX;
        public int MOB_PHYSICS_RETREAT_MAX_DISTANCE_PX =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_RETREAT_MAX_DISTANCE_PX;
        public int MOB_PHYSICS_STUCK_DETECT_MS =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_STUCK_DETECT_MS;
        public int MOB_PHYSICS_STUCK_RETREAT_CHANCE_PERCENT =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_STUCK_RETREAT_CHANCE_PERCENT;
        public int MOB_PHYSICS_KNOCKBACK_PERCENT =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_KNOCKBACK_PERCENT;
        public int MOB_PHYSICS_FLINCH_RECOVERY_MS =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_FLINCH_RECOVERY_MS;
        public int MOB_PHYSICS_POST_FLINCH_CHASE_RAMP_MS =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_POST_FLINCH_CHASE_RAMP_MS;
        public int MOB_PHYSICS_IMPACT_DELAY_PERCENT =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_IMPACT_DELAY_PERCENT;
        public int MOB_PHYSICS_IMPACT_DELAY_OFFSET_MS =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_IMPACT_DELAY_OFFSET_MS;
        public boolean MOB_PHYSICS_DIAGNOSTIC_LOGGING =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_DIAGNOSTIC_LOGGING;
        public boolean MOB_PHYSICS_VIRTUAL_OBSERVER_STRESS =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_VIRTUAL_OBSERVER_STRESS;
        public int MOB_PHYSICS_OBSERVER_WARMUP_MS =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_OBSERVER_WARMUP_MS;
        public int MOB_PHYSICS_AGGRO_TIMEOUT_MS =
                config.AgentYamlConfig.config.agent.AGENT_MOB_PHYSICS_AGGRO_TIMEOUT_MS;
    }
}
