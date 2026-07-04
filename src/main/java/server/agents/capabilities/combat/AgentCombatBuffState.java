package server.agents.capabilities.combat;

import java.util.HashMap;
import java.util.Map;

/**
 * Runtime toggles and cooldowns for automated skill buffs and support buffs.
 */
public final class AgentCombatBuffState {
    private final Map<Integer, Long> nextBuffAt = new HashMap<>();
    private final Map<Integer, Long> nextSupportBuffAt = new HashMap<>();
    private boolean supportHealsEnabled = true;
    private boolean skillBuffsEnabled = true;

    public boolean supportHealsEnabled() {
        return supportHealsEnabled;
    }

    public void setSupportHealsEnabled(boolean supportHealsEnabled) {
        this.supportHealsEnabled = supportHealsEnabled;
    }

    public boolean skillBuffsEnabled() {
        return skillBuffsEnabled;
    }

    public void setSkillBuffsEnabled(boolean skillBuffsEnabled) {
        this.skillBuffsEnabled = skillBuffsEnabled;
    }

    public long nextBuffAt(int skillId) {
        return nextBuffAt.getOrDefault(skillId, 0L);
    }

    public void ensureNextBuffAt(int skillId, long nextAt) {
        nextBuffAt.putIfAbsent(skillId, nextAt);
    }

    public void setNextBuffAt(int skillId, long nextAt) {
        nextBuffAt.put(skillId, nextAt);
    }

    public long nextSupportBuffAt(int skillId) {
        return nextSupportBuffAt.getOrDefault(skillId, 0L);
    }

    public boolean supportBuffOnCooldown(int skillId, long nowMs) {
        return nowMs < nextSupportBuffAt(skillId);
    }

    public void setNextSupportBuffAt(int skillId, long nextAt) {
        nextSupportBuffAt.put(skillId, nextAt);
    }
}
