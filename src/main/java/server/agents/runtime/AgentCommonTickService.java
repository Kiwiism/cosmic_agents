package server.agents.runtime;

import server.agents.monitoring.AgentPerformanceMonitor;

import client.Character;

import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Agent-owned ordering for common per-tick systems.
 */
public final class AgentCommonTickService {
    public record CommonTickHooks(BiConsumer<AgentRuntimeEntry, Character> tickMobDamage,
                                  BiPredicate<AgentRuntimeEntry, Character> isDead,
                                  BiConsumer<AgentRuntimeEntry, Character> enterDeadState,
                                  Consumer<Character> releaseControlledMonsters,
                                  BiConsumer<AgentRuntimeEntry, Character> tickPassiveLoot,
                                  BiConsumer<AgentRuntimeEntry, Character> tickPotionCheck,
                                  BiConsumer<AgentRuntimeEntry, Character> tickPassiveRecovery,
                                  BiConsumer<AgentRuntimeEntry, Character> tickCriticalSurvivalBuff,
                                  BiConsumer<AgentRuntimeEntry, Character> checkLevelUp,
                                  TriConsumer<AgentRuntimeEntry, Character, Character> tickAfkCheck,
                                  BiConsumer<AgentRuntimeEntry, Character> tickTrade,
                                  BiConsumer<AgentRuntimeEntry, Character> tickManualTrade,
                                  TriConsumer<AgentRuntimeEntry, Character, Character> tickPartyQuest,
                                  Consumer<AgentRuntimeEntry> tickScriptTasks,
                                  Predicate<AgentRuntimeEntry> isNpcLocked,
                                  Consumer<AgentRuntimeEntry> tickActionLock,
                                  BiConsumer<AgentRuntimeEntry, Character> rebuildSkillCache,
                                  BiConsumer<AgentRuntimeEntry, Character> tickSupportHealing,
                                  BiConsumer<AgentRuntimeEntry, Character> tickCombatBuffs,
                                  BiConsumer<AgentRuntimeEntry, Character> tickBuffPots,
                                  Predicate<AgentRuntimeEntry> tickActionLocked) {
    }

    @FunctionalInterface
    public interface TriConsumer<A, B, C> {
        void accept(A first, B second, C third);
    }

    private AgentCommonTickService() {
    }

    public static boolean runCommonTickSystems(AgentRuntimeEntry entry,
                                               Character agent,
                                               Character leader,
                                               boolean runAiTick,
                                               CommonTickHooks hooks) {
        boolean perf = AgentPerformanceMonitor.enabled();
        long startedAt = perf ? System.nanoTime() : 0L;
        hooks.tickMobDamage().accept(entry, agent);
        if (perf) AgentPerformanceMonitor.record("common-mob-damage", System.nanoTime() - startedAt);

        if (agent.getHp() <= 0) {
            if (!hooks.isDead().test(entry, agent)) {
                hooks.enterDeadState().accept(entry, agent);
            }
            return true;
        }

        if (perf) startedAt = System.nanoTime();
        hooks.releaseControlledMonsters().accept(agent);
        if (perf) AgentPerformanceMonitor.record("common-release-mob", System.nanoTime() - startedAt);

        boolean agentManagedProfile = !entry.isPartnerManaged();

        if (agentManagedProfile && agent.getTrade() == null) {
            if (perf) startedAt = System.nanoTime();
            hooks.tickPassiveLoot().accept(entry, agent);
            if (perf) AgentPerformanceMonitor.record("common-passive-loot", System.nanoTime() - startedAt);
        }

        if (perf) startedAt = System.nanoTime();
        hooks.tickPotionCheck().accept(entry, agent);
        if (perf) AgentPerformanceMonitor.record("common-potion-check", System.nanoTime() - startedAt);

        if (perf) startedAt = System.nanoTime();
        hooks.tickPassiveRecovery().accept(entry, agent);
        if (perf) AgentPerformanceMonitor.record("common-passive-recovery", System.nanoTime() - startedAt);

        if (perf) startedAt = System.nanoTime();
        hooks.tickCriticalSurvivalBuff().accept(entry, agent);
        if (perf) AgentPerformanceMonitor.record("common-critical-survival-buff", System.nanoTime() - startedAt);

        if (agentManagedProfile) {
            if (perf) startedAt = System.nanoTime();
            hooks.checkLevelUp().accept(entry, agent);
            if (perf) AgentPerformanceMonitor.record("common-build-levelup", System.nanoTime() - startedAt);
        }

        if (perf) startedAt = System.nanoTime();
        hooks.tickAfkCheck().accept(entry, agent, leader);
        if (perf) AgentPerformanceMonitor.record("common-afk-check", System.nanoTime() - startedAt);

        if (agentManagedProfile) {
            if (perf) startedAt = System.nanoTime();
            hooks.tickTrade().accept(entry, agent);
            if (perf) AgentPerformanceMonitor.record("common-trade", System.nanoTime() - startedAt);
        }

        if (perf) startedAt = System.nanoTime();
        hooks.tickManualTrade().accept(entry, agent);
        if (perf) AgentPerformanceMonitor.record("common-manual-trade", System.nanoTime() - startedAt);

        if (agentManagedProfile) {
            if (perf) startedAt = System.nanoTime();
            hooks.tickPartyQuest().accept(entry, agent, leader);
            if (perf) AgentPerformanceMonitor.record("common-pq-hooks", System.nanoTime() - startedAt);

            if (perf) startedAt = System.nanoTime();
            hooks.tickScriptTasks().accept(entry);
            if (perf) AgentPerformanceMonitor.record("common-script-tasks", System.nanoTime() - startedAt);
        }

        if (hooks.isNpcLocked().test(entry)) {
            return true;
        }

        if (perf) startedAt = System.nanoTime();
        hooks.tickActionLock().accept(entry);
        if (perf) AgentPerformanceMonitor.record("common-action-lock", System.nanoTime() - startedAt);

        if (runAiTick) {
            if (perf) startedAt = System.nanoTime();
            hooks.rebuildSkillCache().accept(entry, agent);
            if (perf) AgentPerformanceMonitor.record("common-skill-cache", System.nanoTime() - startedAt);

            if (perf) startedAt = System.nanoTime();
            hooks.tickSupportHealing().accept(entry, agent);
            if (perf) AgentPerformanceMonitor.record("common-support-heal", System.nanoTime() - startedAt);

            if (perf) startedAt = System.nanoTime();
            hooks.tickCombatBuffs().accept(entry, agent);
            if (perf) AgentPerformanceMonitor.record("common-combat-buffs", System.nanoTime() - startedAt);

            if (agentManagedProfile) {
                if (perf) startedAt = System.nanoTime();
                hooks.tickBuffPots().accept(entry, agent);
                if (perf) AgentPerformanceMonitor.record("common-buff-pots", System.nanoTime() - startedAt);
            }
        }
        return hooks.tickActionLocked().test(entry);
    }
}
