package server.agents.capabilities.behavior;

import client.QuestStatus;
import server.agents.events.AgentEvent;
import server.agents.events.AgentEventListener;
import server.agents.personality.AgentPersonalityProfile;
import server.agents.personality.AgentPersonalityState;
import server.agents.runtime.AgentForegroundPauseRuntime;
import server.agents.progression.events.AgentQuestStateChangedEvent;
import server.agents.runtime.AgentRuntimeEntry;

/** Selects a stable personality response when Pio awards the first Relaxer. */
public final class AgentPioRelaxerInterludeEventListener implements AgentEventListener<AgentEvent> {
    public static final int PIO_QUEST_ID = 1008;
    private static final long REST_MIN_MS = config.AgentTuning.longValue("server.agents.capabilities.behavior.AgentPioRelaxerInterludeEventListener.REST_MIN_MS");
    private static final long REST_MAX_MS = config.AgentTuning.longValue("server.agents.capabilities.behavior.AgentPioRelaxerInterludeEventListener.REST_MAX_MS");
    private static final long PLAYFUL_MIN_MS = config.AgentTuning.longValue("server.agents.capabilities.behavior.AgentPioRelaxerInterludeEventListener.PLAYFUL_MIN_MS");
    private static final long PLAYFUL_MAX_MS = config.AgentTuning.longValue("server.agents.capabilities.behavior.AgentPioRelaxerInterludeEventListener.PLAYFUL_MAX_MS");
    private static final int PLAYFUL_ACTIVITY_MIN = config.AgentTuning.intValue(
            "server.agents.capabilities.behavior.AgentPioRelaxerInterludeEventListener.PLAYFUL_ACTIVITY_MIN");
    private static final int PLAYFUL_EXPRESSIVENESS_MIN = config.AgentTuning.intValue(
            "server.agents.capabilities.behavior.AgentPioRelaxerInterludeEventListener.PLAYFUL_EXPRESSIVENESS_MIN");
    private static final int REST_PATIENCE_MIN = config.AgentTuning.intValue(
            "server.agents.capabilities.behavior.AgentPioRelaxerInterludeEventListener.REST_PATIENCE_MIN");
    private static final int REST_ACTIVITY_MAX = config.AgentTuning.intValue(
            "server.agents.capabilities.behavior.AgentPioRelaxerInterludeEventListener.REST_ACTIVITY_MAX");
    private static final long DURATION_DOMAIN = 0x50494F2D43484149L;

    private final AgentRuntimeEntry entry;

    public AgentPioRelaxerInterludeEventListener(AgentRuntimeEntry entry) {
        this.entry = entry;
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        if (!(event instanceof AgentQuestStateChangedEvent quest)
                || quest.questId() != PIO_QUEST_ID
                || quest.status() != QuestStatus.Status.COMPLETED.getId()) {
            return;
        }
        AgentPersonalityState personality = entry.capabilityStates()
                .require(AgentPersonalityState.STATE_KEY);
        if (!personality.presentationEnabled()) {
            return;
        }
        AgentPioRelaxerInterludeState.Mode mode = mode(personality.profile());
        if (mode == AgentPioRelaxerInterludeState.Mode.NONE) {
            return;
        }
        long durationMs = durationMs(mode, personality.behaviorSeed());
        if (entry.capabilityStates().require(AgentPioRelaxerInterludeState.STATE_KEY)
                .request(mode, durationMs, quest.occurredAtMs())) {
            AgentForegroundPauseRuntime.pause(
                    entry, AgentPioRelaxerInterludeRuntime.PAUSE_REASON, quest.occurredAtMs());
        }
    }

    static AgentPioRelaxerInterludeState.Mode mode(AgentPersonalityProfile profile) {
        if (profile == null) {
            return AgentPioRelaxerInterludeState.Mode.NONE;
        }
        AgentPersonalityProfile.Traits traits = profile.traits();
        if (traits.activity() >= PLAYFUL_ACTIVITY_MIN
                && traits.expressiveness() >= PLAYFUL_EXPRESSIVENESS_MIN) {
            return AgentPioRelaxerInterludeState.Mode.PLAYFUL;
        }
        if (traits.patience() >= REST_PATIENCE_MIN
                && traits.activity() <= REST_ACTIVITY_MAX) {
            return AgentPioRelaxerInterludeState.Mode.REST;
        }
        return AgentPioRelaxerInterludeState.Mode.NONE;
    }

    static long durationMs(AgentPioRelaxerInterludeState.Mode mode, long seed) {
        long minimum = mode == AgentPioRelaxerInterludeState.Mode.PLAYFUL
                ? PLAYFUL_MIN_MS : REST_MIN_MS;
        long maximum = mode == AgentPioRelaxerInterludeState.Mode.PLAYFUL
                ? PLAYFUL_MAX_MS : REST_MAX_MS;
        return minimum + Long.remainderUnsigned(mix(seed ^ DURATION_DOMAIN), maximum - minimum + 1L);
    }

    private static long mix(long value) {
        long mixed = value ^ (value >>> 33);
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        return mixed ^ (mixed >>> 33);
    }
}
