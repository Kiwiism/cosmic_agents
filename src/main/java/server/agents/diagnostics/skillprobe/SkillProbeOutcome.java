package server.agents.diagnostics.skillprobe;

import java.util.List;

/** One probed skill: what the classifier thinks it is, what happened when the Agent tried it. */
public record SkillProbeOutcome(int skillId,
                                String skillName,
                                int skillLevel,
                                int maxLevel,
                                String bucket,
                                Kind kind,
                                Stage stage,
                                String detail,
                                int hitLines,
                                int missLines,
                                long mobHpDelta,
                                List<String> autobanSignals,
                                long elapsedMs) {
    public enum Kind { ATTACK, BUFF, HEAL, SUMMON, DEBUFF, UTILITY, MOVEMENT, PASSIVE, PASSIVE_OR_OTHER }

    /** Where the attempt stopped. {@code OK} means the server accepted and applied it. */
    public enum Stage {
        OK,
        NOT_PROBED,
        PLAN_SKILL_MISSING,
        PLAN_SKILL_COOLDOWN,
        PLAN_CANNOT_PAY_COST,
        PLAN_WEAPON_INCOMPATIBLE,
        PLAN_COMBO_ORBS_REQUIRED,
        PLAN_INSUFFICIENT_AMMO,
        PLAN_NO_HITBOX,
        PLAN_TARGET_UNREACHABLE,
        PLAN_RANGED_ROUTE_BLOCKED,
        PLAN_REJECTED_UNKNOWN,
        EXEC_DEFERRED,
        EXEC_REJECTED,
        EXEC_HANDLER_REJECTED,
        EXEC_AUTOBAN_SIGNAL,
        EXEC_NO_DAMAGE,
        BUFF_NOT_CAST,
        BUFF_NOT_APPLIED,
        /** An active skill no Agent runtime can execute yet: a capability gap, not a passive. */
        UNSUPPORTED,
        ERROR
    }

    public SkillProbeOutcome {
        autobanSignals = List.copyOf(autobanSignals);
        detail = detail == null ? "" : detail;
    }

    public boolean ok() {
        return stage == Stage.OK;
    }

    public boolean probed() {
        return stage != Stage.NOT_PROBED;
    }

    public static SkillProbeOutcome notProbed(int skillId, String name, int level, int maxLevel,
                                              String bucket, Kind kind, String detail) {
        return new SkillProbeOutcome(skillId, name, level, maxLevel, bucket, kind, Stage.NOT_PROBED,
                detail, 0, 0, 0L, List.of(), 0L);
    }
}
