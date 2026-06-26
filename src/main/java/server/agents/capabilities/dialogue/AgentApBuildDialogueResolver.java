package server.agents.capabilities.dialogue;

import client.Job;
import client.Stat;
import client.processor.stat.AssignAPProcessor;

public final class AgentApBuildDialogueResolver {
    private AgentApBuildDialogueResolver() {
    }

    public enum StatType {
        STR,
        DEX,
        INT,
        LUK
    }

    public record ApBuildChoice(StatType primaryStat, StatType secondaryStat, int secondaryTarget,
                                String confirmMessage, String alreadyMessage) {
    }

    public static ApBuildChoice resolve(Job job, int currentDex, int currentLuk, int currentStr, String message) {
        if (job.isA(Job.WARRIOR) && AgentBuildDialogueClassifier.isPureStrBuildCommand(message)) {
            int effectiveDex = Math.max(minStatFloor(job, Stat.DEX), currentDex);
            return new ApBuildChoice(
                    StatType.STR, StatType.DEX, 4,
                    AgentDialogueReportFormatter.apPureBuildConfirm(
                            AgentDialogueReportFormatter.WARRIOR_DEXLESS_AP_BUILD, effectiveDex),
                    AgentDialogueReportFormatter.apPureBuildAlready(
                            AgentDialogueReportFormatter.WARRIOR_DEXLESS_AP_BUILD));
        }
        if (job.isA(Job.THIEF) && AgentBuildDialogueClassifier.isDexlessBuildCommand(message)) {
            int effectiveDex = Math.max(minStatFloor(job, Stat.DEX), currentDex);
            return new ApBuildChoice(
                    StatType.LUK, StatType.DEX, 4,
                    AgentDialogueReportFormatter.apPureBuildConfirm(
                            AgentDialogueReportFormatter.THIEF_DEXLESS_AP_BUILD, effectiveDex),
                    AgentDialogueReportFormatter.apPureBuildAlready(
                            AgentDialogueReportFormatter.THIEF_DEXLESS_AP_BUILD));
        }
        if (job.isA(Job.MAGICIAN) && AgentBuildDialogueClassifier.isLuklessBuildCommand(message)) {
            int effectiveLuk = Math.max(minStatFloor(job, Stat.LUK), currentLuk);
            return new ApBuildChoice(
                    StatType.INT, StatType.LUK, 4,
                    AgentDialogueReportFormatter.apPureBuildConfirm(
                            AgentDialogueReportFormatter.MAGICIAN_LUKLESS_AP_BUILD, effectiveLuk),
                    AgentDialogueReportFormatter.apPureBuildAlready(
                            AgentDialogueReportFormatter.MAGICIAN_LUKLESS_AP_BUILD));
        }
        if (job.isA(Job.BOWMAN) && AgentBuildDialogueClassifier.isStrlessBuildCommand(message)) {
            int effectiveStr = Math.max(minStatFloor(job, Stat.STR), currentStr);
            return new ApBuildChoice(
                    StatType.DEX, StatType.STR, 4,
                    AgentDialogueReportFormatter.apPureBuildConfirm(
                            AgentDialogueReportFormatter.BOWMAN_STRLESS_AP_BUILD, effectiveStr),
                    AgentDialogueReportFormatter.apPureBuildAlready(
                            AgentDialogueReportFormatter.BOWMAN_STRLESS_AP_BUILD));
        }

        if (job.isA(Job.WARRIOR) || job.isA(Job.THIEF)) {
            Integer dexTarget = AgentBuildDialogueClassifier.matchFixedDexTarget(message);
            if (dexTarget != null) {
                int legalDexTarget = Math.max(minStatFloor(job, Stat.DEX), dexTarget);
                int effectiveDex = Math.max(legalDexTarget, currentDex);
                AgentDialogueReportFormatter.AgentApBuildDialogueProfile dialogueProfile = job.isA(Job.WARRIOR)
                        ? AgentDialogueReportFormatter.WARRIOR_FIXED_DEX_AP_BUILD
                        : AgentDialogueReportFormatter.THIEF_FIXED_DEX_AP_BUILD;
                return new ApBuildChoice(
                        job.isA(Job.WARRIOR) ? StatType.STR : StatType.LUK,
                        StatType.DEX,
                        dexTarget,
                        AgentDialogueReportFormatter.apFixedBuildConfirm(dialogueProfile, effectiveDex),
                        AgentDialogueReportFormatter.apFixedBuildAlready(dialogueProfile, legalDexTarget));
            }
        }
        if (job.isA(Job.MAGICIAN)) {
            Integer lukTarget = AgentBuildDialogueClassifier.matchFixedLukTarget(message);
            if (lukTarget != null) {
                int legalLukTarget = Math.max(minStatFloor(job, Stat.LUK), lukTarget);
                int effectiveLuk = Math.max(legalLukTarget, currentLuk);
                return new ApBuildChoice(
                        StatType.INT,
                        StatType.LUK,
                        lukTarget,
                        AgentDialogueReportFormatter.apFixedBuildConfirm(
                                AgentDialogueReportFormatter.MAGICIAN_FIXED_LUK_AP_BUILD, effectiveLuk),
                        AgentDialogueReportFormatter.apFixedBuildAlready(
                                AgentDialogueReportFormatter.MAGICIAN_FIXED_LUK_AP_BUILD, legalLukTarget));
            }
        }
        if (job.isA(Job.BOWMAN)) {
            Integer strTarget = AgentBuildDialogueClassifier.matchFixedStrTarget(message);
            if (strTarget != null) {
                int legalStrTarget = Math.max(minStatFloor(job, Stat.STR), strTarget);
                int effectiveStr = Math.max(legalStrTarget, currentStr);
                return new ApBuildChoice(
                        StatType.DEX,
                        StatType.STR,
                        strTarget,
                        AgentDialogueReportFormatter.apFixedBuildConfirm(
                                AgentDialogueReportFormatter.BOWMAN_FIXED_STR_AP_BUILD, effectiveStr),
                        AgentDialogueReportFormatter.apFixedBuildAlready(
                                AgentDialogueReportFormatter.BOWMAN_FIXED_STR_AP_BUILD, legalStrTarget));
            }
        }
        return null;
    }

    private static int minStatFloor(Job job, Stat stat) {
        return AssignAPProcessor.getMinStatFloor(job, stat);
    }
}
