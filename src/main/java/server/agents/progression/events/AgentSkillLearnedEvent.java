package server.agents.progression.events;

import server.agents.events.AgentEvent;

/** Skill-level transition emitted after one or more SP have been committed. */
public record AgentSkillLearnedEvent(
        int agentId,
        long occurredAtMs,
        int level,
        int skillId,
        int previousSkillLevel,
        int skillLevel,
        int remainingSp,
        String profileId,
        String objectiveId) implements AgentEvent {
    public static final String TYPE = "progression.skill-learned";

    public AgentSkillLearnedEvent {
        if (agentId <= 0 || occurredAtMs < 0 || level < 1 || skillId <= 0
                || previousSkillLevel < 0 || skillLevel <= previousSkillLevel || remainingSp < 0) {
            throw new IllegalArgumentException("Valid skill transition context is required");
        }
        profileId = profileId == null ? "" : profileId;
        objectiveId = objectiveId == null ? "" : objectiveId;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String dedupeKey() {
        return skillId + ":" + skillLevel;
    }
}
