package server.agents.capabilities.build.profiles;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.events.AgentEventPriority;
import server.agents.progression.events.AgentApAssignedEvent;
import server.agents.progression.events.AgentProgressionEventPublisher;

public final class AgentApBuildProfileService {
    private AgentApBuildProfileService() {
    }

    public static AgentApBuildProfile select(AgentRuntimeEntry entry, String profileId) {
        AgentApBuildProfile profile = AgentApBuildProfileRepository.defaultRepository().find(profileId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown Agent AP build profile: " + profileId));
        entry.apBuildProfileState().assign(profile);
        autoAssign(entry, entry.bot());
        return profile;
    }

    /**
     * Applies the independently configured Agent profile. The return value indicates that this
     * layer owns AP allocation, even when no AP can be spent at the current level/job.
     */
    public static boolean autoAssign(AgentRuntimeEntry entry, Character agent) {
        AgentApBuildProfile profile = entry.apBuildProfileState().profile();
        if (profile == null) {
            return false;
        }
        if (agent == null || agent.getLevel() > profile.supportedThroughLevel()
                || !profile.supports(agent.getJob()) || agent.getRemainingAp() < 1) {
            return true;
        }

        Allocation allocation = allocation(profile, agent.getLevel(), agent.getRemainingAp(),
                currentStat(agent, profile.targetStat()));
        if (allocation.spent() > 0) {
            if (agent.assignStrDexIntLuk(
                    allocation.str(), allocation.dex(), allocation.intelligence(), allocation.luk())) {
                if (agent.getId() > 0) {
                    AgentProgressionEventPublisher.publish(entry, new AgentApAssignedEvent(
                                    agent.getId(), System.currentTimeMillis(), agent.getLevel(),
                                    allocation.str(), allocation.dex(), allocation.intelligence(), allocation.luk(),
                                    agent.getRemainingAp(), profile.profileId(),
                                    AgentProgressionEventPublisher.objectiveId(entry)),
                            AgentEventPriority.NORMAL);
                }
            }
        }
        return true;
    }

    static Allocation allocation(AgentApBuildProfile profile, int level, int availableAp, int currentTargetStat) {
        int targetGain = Math.min(Math.max(0, profile.targetAtLevel(level) - currentTargetStat),
                Math.max(0, availableAp));
        int primaryGain = Math.max(0, availableAp) - targetGain;
        int str = 0;
        int dex = 0;
        int intelligence = 0;
        int luk = 0;

        switch (profile.targetStat()) {
            case STR -> str += targetGain;
            case DEX -> dex += targetGain;
            case INT -> intelligence += targetGain;
            case LUK -> luk += targetGain;
        }
        switch (profile.primaryStat()) {
            case STR -> str += primaryGain;
            case DEX -> dex += primaryGain;
            case INT -> intelligence += primaryGain;
            case LUK -> luk += primaryGain;
        }
        return new Allocation(str, dex, intelligence, luk);
    }

    private static int currentStat(Character agent, AgentApBuildProfile.StatType stat) {
        return switch (stat) {
            case STR -> agent.getStr();
            case DEX -> agent.getDex();
            case INT -> agent.getInt();
            case LUK -> agent.getLuk();
        };
    }

    record Allocation(int str, int dex, int intelligence, int luk) {
        int spent() {
            return str + dex + intelligence + luk;
        }
    }
}
