package server.agents.capabilities.build.profiles;

/**
 * One step in a skill build order.
 * Spend SP on skillId until it reaches targetLevel, then move to the next step.
 */
public record BuildStep(int skillId, int targetLevel) {
}
