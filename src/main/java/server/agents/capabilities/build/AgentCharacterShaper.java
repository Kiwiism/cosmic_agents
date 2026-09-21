package server.agents.capabilities.build;

import client.Character;
import client.HpMpGrowthPolicy;
import client.Job;
import client.JobProgressionPolicy;
import client.Skill;
import constants.game.GameConstants;
import server.agents.capabilities.build.profiles.AgentSpBuildProfileService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns a low-level Agent into a job at a level the way the game would have: one level at a time
 * so HP/MP/AP grow through the normal formulas, advancing along the legal job chain (a fourth-job
 * target passes through the second and third job), then repairs skipped growth, spends AP by job
 * archetype and SP through the catalog build profiles. Used by the skill probe for throwaway
 * companions and by hosts that provision pre-levelled Agents. Never lowers a level.
 */
public final class AgentCharacterShaper {

    private AgentCharacterShaper() {
    }

    /** Levels and advances {@code agent} toward {@code target} at {@code level}; returns skill levels learned. */
    public static int shapeTo(Character agent, Job target, int level) {
        // 200 is the v83 level cap; the loop bound only guards against a job that can never advance.
        int targetLevel = Math.max(1, Math.min(level, 200));
        List<Job> chain = advancementChain(target);
        int guard = 0;
        while (guard++ < 250) {
            advanceIfLegal(agent, chain);
            if (agent.getLevel() >= targetLevel) {
                break;
            }
            agent.levelUp(false);
        }
        advanceIfLegal(agent, chain);
        repairGrowth(agent);
        assignAp(agent, agent.getJob(), Map.of());
        return AgentSpBuildProfileService.assignOffline(agent);
    }

    /** BEGINNER -> first job -> second job -> third job -> fourth job, as far as {@code target} goes. */
    public static List<Job> advancementChain(Job target) {
        List<Job> chain = new ArrayList<>();
        if (target == null || target == Job.BEGINNER) {
            return chain;
        }
        int id = target.getId();
        int first = id / 100 * 100;
        int second = id / 10 * 10;
        Job firstJob = first > 0 ? Job.getById(first) : null;
        Job secondJob = second != first ? Job.getById(second) : null;
        if (firstJob != null) {
            chain.add(firstJob);
        }
        if (secondJob != null) {
            chain.add(secondJob);
        }
        Job thirdJob = id == second + 2 ? Job.getById(second + 1) : null;
        if (thirdJob != null) {
            chain.add(thirdJob);
        }
        if (id != second) {
            chain.add(target);
        }
        return chain;
    }

    public static void advanceIfLegal(Character agent, List<Job> chain) {
        for (Job next : chain) {
            if (agent.getJob() == next) {
                continue;
            }
            if (JobProgressionPolicy.isLegalAdvancement(agent.getJob(), next, agent.getLevel())) {
                agent.changeJob(next);
            }
        }
    }

    /**
     * Characters that were jumped to a level never received the AP, HP/MP and SP of the skipped
     * levels; top them up to the job's formula for this level.
     */
    public static void repairGrowth(Character agent) {
        int level = agent.getLevel();
        int spentBeyondBase = Math.max(0, agent.getStr() + agent.getDex() + agent.getInt() + agent.getLuk() - 25);
        int expectedRemaining = Math.max(0, 5 * (level - 1) - spentBeyondBase);
        if (agent.getRemainingAp() < expectedRemaining) {
            agent.gainAp(expectedRemaining - agent.getRemainingAp(), true);
        }
        HpMpGrowthPolicy.Growth base = HpMpGrowthPolicy.baseForJobAtLevel(agent.getJob(), level);
        if (agent.getMaxHp() < base.hp()) {
            agent.addMaxHP(base.hp() - agent.getMaxHp());
        }
        if (agent.getMaxMp() < base.mp()) {
            agent.addMaxMP(base.mp() - agent.getMaxMp());
        }
        repairSp(agent);
    }

    /** SP earned is 3 per level past the first-job level plus each advancement's grant, minus levels sitting in skills. */
    public static void repairSp(Character agent) {
        Job job = agent.getJob();
        int branch = GameConstants.getJobBranch(job);
        if (branch == 0) {
            return;
        }
        int firstJobLevel = job.getId() / 100 == 2 ? 8 : 10;
        int earned = 3 * Math.max(0, agent.getLevel() - firstJobLevel);
        for (int b = 1; b <= branch; b++) {
            earned += GameConstants.getChangeJobSpUpgrade(b);
        }
        int spent = 0;
        for (Map.Entry<Skill, Character.SkillEntry> learned : agent.getSkills().entrySet()) {
            if (!learned.getKey().isBeginnerSkill()) {
                spent += Math.max(0, learned.getValue().skillevel);
            }
        }
        int book = GameConstants.getSkillBook(job.getId());
        int expectedRemaining = Math.max(0, earned - spent);
        int remaining = agent.getRemainingSps()[book];
        if (remaining < expectedRemaining) {
            agent.gainSp(expectedRemaining - remaining, book, true);
        }
    }

    /** Spends remaining AP by job archetype unless {@code stats} pins explicit str/dex/int/luk shares. */
    public static void assignAp(Character agent, Job job, Map<String, Integer> stats) {
        int ap = agent.getRemainingAp();
        if (ap <= 0) {
            return;
        }
        int str;
        int dex;
        int int_;
        int luk;
        if (!stats.isEmpty()) {
            int total = Math.max(1, stats.values().stream().mapToInt(Integer::intValue).sum());
            str = ap * stats.getOrDefault("str", 0) / total;
            dex = ap * stats.getOrDefault("dex", 0) / total;
            int_ = ap * stats.getOrDefault("int", 0) / total;
            luk = ap * stats.getOrDefault("luk", 0) / total;
        } else {
            int family = job == null ? 0 : job.getId() / 100;
            switch (family) {
                case 1 -> { str = ap * 4 / 5; dex = ap - str; int_ = 0; luk = 0; }
                case 2 -> { int_ = ap * 4 / 5; luk = ap - int_; str = 0; dex = 0; }
                case 3 -> { dex = ap * 4 / 5; str = ap - dex; int_ = 0; luk = 0; }
                case 4 -> { luk = ap * 4 / 5; dex = ap - luk; str = 0; int_ = 0; }
                case 5 -> { str = ap / 2; dex = ap - str; int_ = 0; luk = 0; }
                default -> { str = ap / 2; dex = ap - str; int_ = 0; luk = 0; }
            }
        }
        int assigned = str + dex + int_ + luk;
        if (assigned < ap) {
            str += ap - assigned;
        }
        agent.assignStrDexIntLuk(str, dex, int_, luk);
    }
}
