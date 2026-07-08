package server.agents.capabilities.build;

import client.Character;
import client.Job;
import client.Skill;
import client.SkillFactory;
import client.Stat;
import client.processor.stat.AssignAPProcessor;
import constants.game.GameConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import server.agents.capabilities.build.profiles.BowmanBuilds;
import server.agents.capabilities.build.profiles.BuildStep;
import server.agents.capabilities.build.profiles.MageBuilds;
import server.agents.capabilities.build.profiles.ThiefBuilds;
import server.agents.capabilities.build.profiles.WarriorBuilds;
import server.agents.capabilities.dialogue.AgentBuildPromptReporter;
import server.agents.integration.AgentBuildRuntime;
import server.agents.integration.AgentBuildStatusRuntime;
import server.agents.integration.AgentMovementCommandRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;

public final class AgentBuildService {
    public enum StatType {
        STR,
        DEX,
        INT,
        LUK
    }

    /**
     * AP build by job tree: fill the secondary stat up to its target, then dump all remaining AP into the primary stat.
     */
    public static class ApBuild {
        final StatType primaryStat;
        final StatType secondaryStat;
        final int secondaryTarget;

        public ApBuild(StatType primaryStat, StatType secondaryStat, int secondaryTarget) {
            this.primaryStat = primaryStat;
            this.secondaryStat = secondaryStat;
            this.secondaryTarget = Math.max(4, secondaryTarget);
        }

        public StatType primaryStat() {
            return primaryStat;
        }

        public StatType secondaryStat() {
            return secondaryStat;
        }

        public int secondaryTarget() {
            return secondaryTarget;
        }
    }

    /** Stores the AP build, confirms it to the owner, and immediately spends any pending AP. */
    public static void setApBuild(AgentRuntimeEntry entry, ApBuild build, String confirmMsg) {
        AgentBuildStateRuntime.setApBuild(entry, build);
        AgentBuildRuntime.confirmApBuild(entry, confirmMsg);
        autoAssignAp(entry, AgentRuntimeIdentityRuntime.bot(entry));
    }

    /**
     * Returns a prompt asking the owner to choose an AP build, or null if:
     * no AP is pending, a build is already chosen, a prompt was already sent,
     * or the bot is not on a supported branch.
     */
    public static String buildApPrompt(AgentRuntimeEntry entry, Character bot) {
        String prompt = apPromptForJob(bot.getJob());
        if (prompt == null) return null;
        if (AgentBuildStateRuntime.hasApBuild(entry) || AgentBuildStateRuntime.apPromptSent(entry) || bot.getRemainingAp() < 1) return null;
        return requestApBuildPrompt(entry, bot);
    }

    public static String requestApBuildPrompt(AgentRuntimeEntry entry, Character bot) {
        String prompt = apPromptForJob(bot.getJob());
        if (prompt == null) return null;
        AgentBuildStateRuntime.markApPromptSent(entry);
        return prompt;
    }

    /** Spends all remaining AP according to the stored build. */
    public static void autoAssignAp(AgentRuntimeEntry entry, Character bot) {
        ApBuild build = AgentBuildStateRuntime.apBuild(entry);
        if (build == null || bot.getRemainingAp() < 1) return;

        int ap = bot.getRemainingAp();
        int[] gains = new int[StatType.values().length];
        int secondaryNeeded = Math.max(0, build.secondaryTarget - currentStat(bot, build.secondaryStat));
        int secondaryGain = Math.min(secondaryNeeded, ap);
        gains[build.secondaryStat.ordinal()] = secondaryGain;
        ap -= secondaryGain;
        gains[build.primaryStat.ordinal()] += ap;

        if (gains[StatType.STR.ordinal()] > 0
                || gains[StatType.DEX.ordinal()] > 0
                || gains[StatType.INT.ordinal()] > 0
                || gains[StatType.LUK.ordinal()] > 0) {
            bot.assignStrDexIntLuk(
                    gains[StatType.STR.ordinal()],
                    gains[StatType.DEX.ordinal()],
                    gains[StatType.INT.ordinal()],
                    gains[StatType.LUK.ordinal()]
            );
        }
    }

    public static String respecAp(AgentRuntimeEntry entry, Character bot) {
        if (apPromptForJob(bot.getJob()) == null) {
            return "dont have an ap build for my job yet";
        }
        if (!AgentBuildStateRuntime.hasApBuild(entry)) {
            AgentBuildStateRuntime.clearApBuildPromptState(entry);
            String prompt = requestApBuildPrompt(entry, bot);
            return prompt != null ? prompt : "need your ap build first";
        }

        if (!reallocateAp(entry, bot)) {
            return "couldnt rebuild my ap";
        }

        return "ok, rebuilt my ap using the bot build";
    }

    public static void handleJobAdvance(AgentRuntimeEntry entry, Character bot, Job oldJob, Job newJob) {
        if (oldJob == Job.BEGINNER && oldJob != newJob && AgentBuildStateRuntime.hasApBuild(entry)) {
            reallocateAp(entry, bot);
        }

        autoAssignSp(entry, bot);
        autoAssignAp(entry, bot);
    }

    private static boolean reallocateAp(AgentRuntimeEntry entry, Character bot) {
        int minStr = AssignAPProcessor.getMinStatFloor(bot.getJob(), Stat.STR);
        int minDex = AssignAPProcessor.getMinStatFloor(bot.getJob(), Stat.DEX);
        int minInt = AssignAPProcessor.getMinStatFloor(bot.getJob(), Stat.INT);
        int minLuk = AssignAPProcessor.getMinStatFloor(bot.getJob(), Stat.LUK);

        if (!bot.assignStrDexIntLuk(minStr - bot.getStr(), minDex - bot.getDex(), minInt - bot.getInt(), minLuk - bot.getLuk())) {
            return false;
        }

        autoAssignAp(entry, bot);
        return true;
    }

    /**
     * Returns a prompt asking for the SP build variant, or null if not needed.
     * Currently only Hero has two documented builds.
     */
    public static String buildSpVariantPrompt(AgentRuntimeEntry entry, Character bot) {
        if (bot.getJob() != Job.HERO) return null;
        if (AgentBuildStateRuntime.hasSpVariant(entry) || AgentBuildStateRuntime.spVariantPromptSent(entry) || bot.getRemainingSps()[3] < 1) return null;
        AgentBuildStateRuntime.markSpVariantPromptSent(entry);
        return AgentBuildPromptReporter.heroSpVariantPrompt();
    }

    /**
     * Spends all available SP following the configured build order.
     * Hero SP is held until the owner chooses a variant.
     */
    public static void autoAssignSp(AgentRuntimeEntry entry, Character bot) {
        String spVariant = AgentBuildStateRuntime.spVariant(entry);
        if (bot.getJob() == Job.HERO && spVariant == null) return;

        List<BuildStep> steps = getBuildOrder(bot.getJob(), spVariant);
        if (steps == null) return;

        autoAssignSp(bot, steps);
    }

    public static String respecSp(AgentRuntimeEntry entry, Character bot) {
        String spVariant = AgentBuildStateRuntime.spVariant(entry);
        if (bot.getJob() == Job.HERO && spVariant == null) {
            return "need your hero build first. say '1h' or '2h'";
        }

        List<Job> buildPath = getSupportedBuildPath(bot.getJob());
        if (buildPath == null) {
            return "dont have an sp respec build for my job yet";
        }

        int[] refundedSp = new int[5];
        List<Skill> skillsToReset = new ArrayList<>();
        for (Map.Entry<Skill, Character.SkillEntry> learned : bot.getSkills().entrySet()) {
            Skill skill = learned.getKey();
            Character.SkillEntry skillEntry = learned.getValue();
            if (skill == null || skillEntry == null || skillEntry.skillevel <= 0) {
                continue;
            }

            int skillId = skill.getId();
            if (skill.isBeginnerSkill() || GameConstants.isHiddenSkills(skillId)) {
                continue;
            }
            if (!GameConstants.isInJobTree(skillId, bot.getJob().getId())) {
                continue;
            }

            refundedSp[GameConstants.getSkillBook(skillId / 10000)] += skillEntry.skillevel;
            skillsToReset.add(skill);
        }

        for (Skill skill : skillsToReset) {
            bot.changeSkillLevel(skill, (byte) 0, bot.getMasterLevel(skill), bot.getSkillExpiration(skill));
        }
        for (int book = 0; book < refundedSp.length; book++) {
            if (refundedSp[book] > 0) {
                bot.gainSp(refundedSp[book], book, false);
            }
        }

        for (Job job : buildPath) {
            List<BuildStep> steps = getBuildOrder(job, spVariant);
            if (steps != null) {
                autoAssignSp(bot, steps);
            }
        }

        return "ok, rebuilt my sp using the bot build";
    }

    private static void autoAssignSp(Character bot, List<BuildStep> steps) {
        for (BuildStep step : steps) {
            Skill skill = SkillFactory.getSkill(step.skillId());
            if (skill == null) continue;

            int book = GameConstants.getSkillBook(step.skillId() / 10000);
            if (bot.getRemainingSps()[book] < 1) continue;

            while (bot.getRemainingSps()[book] > 0) {
                int currentLevel = bot.getSkillLevel(skill);
                if (currentLevel >= step.targetLevel()) break;
                if (!canLevelSkill(bot, skill, currentLevel)) return;

                bot.gainSp(-1, book, false);
                bot.changeSkillLevel(
                        skill,
                        (byte) (currentLevel + 1),
                        bot.getMasterLevel(skill),
                        bot.getSkillExpiration(skill)
                );
            }
        }
    }

    private static boolean canLevelSkill(Character bot, Skill skill, int currentLevel) {
        int cap = skill.isFourthJob() ? bot.getMasterLevel(skill) : skill.getMaxLevel();
        return currentLevel < cap;
    }

    private static List<Job> getSupportedBuildPath(Job job) {
        return switch (job) {
            case WARRIOR -> List.of(Job.WARRIOR);
            case FIGHTER -> List.of(Job.WARRIOR, Job.FIGHTER);
            case CRUSADER -> List.of(Job.WARRIOR, Job.FIGHTER, Job.CRUSADER);
            case HERO -> List.of(Job.WARRIOR, Job.FIGHTER, Job.CRUSADER, Job.HERO);
            case BOWMAN -> List.of(Job.BOWMAN);
            case HUNTER -> List.of(Job.BOWMAN, Job.HUNTER);
            case RANGER -> List.of(Job.BOWMAN, Job.HUNTER, Job.RANGER);
            case BOWMASTER -> List.of(Job.BOWMAN, Job.HUNTER, Job.RANGER, Job.BOWMASTER);
            case THIEF -> List.of(Job.THIEF);
            case ASSASSIN -> List.of(Job.THIEF, Job.ASSASSIN);
            case HERMIT -> List.of(Job.THIEF, Job.ASSASSIN, Job.HERMIT);
            case NIGHTLORD -> List.of(Job.THIEF, Job.ASSASSIN, Job.HERMIT, Job.NIGHTLORD);
            case PAGE -> List.of(Job.WARRIOR, Job.PAGE);
            case WHITEKNIGHT -> List.of(Job.WARRIOR, Job.PAGE, Job.WHITEKNIGHT);
            case SPEARMAN -> List.of(Job.WARRIOR, Job.SPEARMAN);
            case DRAGONKNIGHT -> List.of(Job.WARRIOR, Job.SPEARMAN, Job.DRAGONKNIGHT);
            case MAGICIAN -> List.of(Job.MAGICIAN);
            case CLERIC -> List.of(Job.MAGICIAN, Job.CLERIC);
            case PRIEST -> List.of(Job.MAGICIAN, Job.CLERIC, Job.PRIEST);
            case BISHOP -> List.of(Job.MAGICIAN, Job.CLERIC, Job.PRIEST, Job.BISHOP);
            default -> null;
        };
    }

    private static List<BuildStep> getBuildOrder(Job job, String variant) {
        List<BuildStep> warriorBuild = WarriorBuilds.getBuildOrder(job, variant);
        if (warriorBuild != null) {
            return warriorBuild;
        }
        List<BuildStep> bowmanBuild = BowmanBuilds.getBuildOrder(job);
        if (bowmanBuild != null) {
            return bowmanBuild;
        }
        List<BuildStep> thiefBuild = ThiefBuilds.getBuildOrder(job);
        if (thiefBuild != null) {
            return thiefBuild;
        }
        return MageBuilds.getBuildOrder(job);
    }

    private static String apPromptForJob(Job job) {
        return AgentBuildPromptReporter.apPromptForJob(job);
    }

    private static int currentStat(Character bot, StatType statType) {
        return switch (statType) {
            case STR -> bot.getStr();
            case DEX -> bot.getDex();
            case INT -> bot.getInt();
            case LUK -> bot.getLuk();
        };
    }

    /**
     * Detects level-up and sends prompts before spending SP/AP so gating can apply.
     */
    public static void checkLevelUp(AgentRuntimeEntry entry, Character bot) {
        int lvl = bot.getLevel();
        if (AgentBuildStateRuntime.lastKnownLevel(entry) == lvl) return;

        int prev = AgentBuildStateRuntime.lastKnownLevel(entry);
        AgentBuildStateRuntime.setLastKnownLevel(entry, lvl);
        if (prev == -1) {
            autoAssignSp(entry, bot);
            autoAssignAp(entry, bot);
            return;
        }

        if (lvl == 8 || lvl == 10 || lvl == 30 || lvl == 70 || lvl == 120) {
            AgentMovementCommandRuntime.followOwner(entry);
            AgentBuildStatusRuntime.checkBuildStatus(entry, bot);
        }

        autoAssignSp(entry, bot);
        autoAssignAp(entry, bot);
    }

    /** Returns the next job-advancement prompt, or null if none is pending. */
    public static String buildJobPrompt(AgentRuntimeEntry entry, Character bot) {
        int lvl = bot.getLevel();
        Job job = bot.getJob();
        int prompted = AgentBuildStateRuntime.jobPromptSent(entry);

        if (job == Job.BEGINNER) {
            if (lvl >= 10 && prompted < 10) {
                AgentBuildStateRuntime.setJobPromptSent(entry, 10);
                return AgentBuildPromptReporter.beginnerJobPrompt(lvl);
            } else if (lvl >= 8 && prompted < 8) {
                AgentBuildStateRuntime.setJobPromptSent(entry, 8);
                return AgentBuildPromptReporter.beginnerJobPrompt(lvl);
            }
            return null;
        }

        if (lvl >= 30 && prompted < 30) {
            String msg = AgentBuildPromptReporter.secondJobPrompt(job);
            if (msg != null) {
                AgentBuildStateRuntime.setJobPromptSent(entry, 30);
                return msg;
            }
        }

        if (lvl >= 70 && prompted < 70) {
            String msg = AgentBuildPromptReporter.thirdJobPrompt(job);
            if (msg != null) {
                AgentBuildStateRuntime.setJobPromptSent(entry, 70);
                return msg;
            }
        }

        if (lvl >= 120 && prompted < 120) {
            String msg = AgentBuildPromptReporter.fourthJobPrompt(job);
            if (msg != null) {
                AgentBuildStateRuntime.setJobPromptSent(entry, 120);
                return msg;
            }
        }

        return null;
    }
}
