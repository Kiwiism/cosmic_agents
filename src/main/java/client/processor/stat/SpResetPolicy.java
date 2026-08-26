package client.processor.stat;

import client.Job;
import constants.game.GameConstants;
import constants.skills.Magician;
import constants.skills.Warrior;

/** Packet-independent validation for one-point SP Reset transfers. */
public final class SpResetPolicy {
    private SpResetPolicy() {
    }

    public static boolean isValidTransfer(int resetItemId, int skillFromId, int skillToId, Job characterJob) {
        if (characterJob == null || skillFromId == skillToId) {
            return false;
        }
        int resetTier = resetItemId - 5_050_000;
        if (resetTier < 1 || resetTier > 4) {
            return false;
        }
        Job fromJob = Job.getById(skillFromId / 10_000);
        Job toJob = Job.getById(skillToId / 10_000);
        return fromJob != null
                && toJob != null
                && GameConstants.getJobBranch(fromJob) == resetTier
                && GameConstants.getJobBranch(toJob) == resetTier
                && GameConstants.isInJobTree(skillFromId, characterJob.getId())
                && GameConstants.isInJobTree(skillToId, characterJob.getId());
    }

    public static boolean preservesHpMpPassivePrerequisites(
            int skillFromId, int sourceLevel, int warriorPassiveLevel, int magicianPassiveLevel) {
        if (skillFromId == Warrior.IMPROVED_HPREC && sourceLevel <= 5 && warriorPassiveLevel > 0) {
            return false;
        }
        return skillFromId != Magician.IMPROVED_MP_RECOVERY
                || sourceLevel > 5
                || magicianPassiveLevel <= 0;
    }
}
