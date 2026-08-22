package client.command;

import client.Job;
import constants.game.GameConstants;

import java.util.Arrays;

/** Pure calculations used by AP/SP normalization commands. */
public final class StatResetCalculator {
    private static final int[] EVAN_ADVANCEMENT_LEVELS = {10, 20, 30, 40, 50, 60, 80, 100, 120, 160};

    private StatResetCalculator() {
    }

    public static int availableAp(Job job, int level, int hpMpApUsed, boolean useStartingAp4) {
        int jobBranch = advancementCount(job);
        int advancementAp = 0;
        if (jobBranch > 0) {
            advancementAp = useStartingAp4 ? 4 : 0;
            advancementAp += (jobBranch - 1) * (GameConstants.isCygnus(job.getId()) ? 7 : 5);
        }

        int levelAp = Math.max(0, level - 1) * 5;
        if (!useStartingAp4) levelAp += 9;
        if (GameConstants.isCygnus(job.getId())) {
            levelAp += Math.max(0, Math.min(level - 11, 7)) * 2;
            levelAp += Math.max(0, Math.min(level - 18, 59));
        }
        return Math.max(0, levelAp + advancementAp - Math.max(0, hpMpApUsed));
    }

    public static int[] availableSp(Job job, int level) {
        int[] pools = new int[10];
        if (GameConstants.hasSPTable(job)) {
            int currentBook = job == Job.EVAN ? -1 : GameConstants.getSkillBook(job.getId());
            for (int book = 0; book <= currentBook; book++) {
                int startLevel = EVAN_ADVANCEMENT_LEVELS[book];
                int endLevel = book < currentBook ? EVAN_ADVANCEMENT_LEVELS[book + 1] : level;
                pools[book] = Math.max(0, 3 + 3 * Math.max(0, endLevel - startLevel));
            }
            return pools;
        }

        int jobBranch = advancementCount(job);
        if (jobBranch > 0) {
            int firstJobLevel = job.isA(Job.MAGICIAN) || job.isA(Job.BLAZEWIZARD1) ? 8 : 10;
            pools[0] = Math.max(0, 3 * Math.max(0, level - firstJobLevel)
                    + GameConstants.getChangeJobSpUpgrade(jobBranch));
        }
        return pools;
    }

    public static int totalSp(Job job, int level) {
        return Arrays.stream(availableSp(job, level)).sum();
    }

    static int advancementCount(Job job) {
        if (job == Job.EVAN) return 0;
        if (job.isA(Job.EVAN1)) return GameConstants.getSkillBook(job.getId()) + 1;
        return GameConstants.getJobBranch(job);
    }
}
