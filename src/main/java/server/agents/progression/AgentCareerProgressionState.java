package server.agents.progression;

import server.agents.runtime.state.AgentCapabilityStateKey;

public final class AgentCareerProgressionState {
    public enum Stage {
        WAITING_FOR_MAPLE_ISLAND,
        TRAVEL_TO_LITH,
        COMPLETE_BIGGS_AT_OLAF,
        COMPLETE_OLAF_LESSON,
        START_CAREER_PATH,
        TRAVEL_TO_PRE_JOB_GRIND,
        GRIND_TO_JOB_LEVEL,
        RETURN_TO_LITH_FOR_TAXI,
        TAKE_TAXI,
        ENTER_INSTRUCTOR_ROOM,
        COMPLETE_CAREER_PATH,
        ADVANCE_FIRST_JOB,
        TRAVEL_TO_INITIAL_SHOP,
        INITIAL_SHOPPING,
        RETURN_TO_INSTRUCTOR,
        INSTRUCTOR_TRAINING,
        HOME_QUEST_PACK,
        POST_HOME_DECISION,
        ROTATION_QUEST_PACK,
        GRIND_TO_MILESTONE,
        FINAL_RETURN_TO_INSTRUCTOR,
        COMPLETE,
        BLOCKED
    }

    public enum RunMode {
        LEVEL15,
        LEVEL15_WITH_INITIAL_SHOP
    }

    public static final AgentCapabilityStateKey<AgentCareerProgressionState> STATE_KEY =
            new AgentCapabilityStateKey<>("progression.career", AgentCareerProgressionState.class,
                    AgentCareerProgressionState::new);

    private AgentCareerBuildBundle bundle;
    private Stage stage = Stage.WAITING_FOR_MAPLE_ISLAND;
    private RunMode runMode = RunMode.LEVEL15;
    private String startVariantId = "lv10";
    private int trainingQuestIndex;
    private int questPackIndex;
    private long nextActionAtMs;
    private String blockReason = "";
    private long revision;
    private long persistedRevision = -1L;

    public synchronized AgentCareerBuildBundle bundle() {
        return bundle;
    }

    public synchronized void assign(AgentCareerBuildBundle bundle) {
        if (this.bundle != null && bundle != null
                && this.bundle.bundleId().equals(bundle.bundleId())
                && this.bundle.bundleVersion() == bundle.bundleVersion()) {
            return;
        }
        this.bundle = bundle;
        revision++;
    }

    public synchronized void reset(AgentCareerBuildBundle bundle,
                                   RunMode runMode,
                                   Stage stage,
                                   long nextActionAtMs) {
        reset(bundle, runMode, "lv10", stage, nextActionAtMs);
    }

    public synchronized void reset(AgentCareerBuildBundle bundle,
                                   RunMode runMode,
                                   String startVariantId,
                                   Stage stage,
                                   long nextActionAtMs) {
        this.bundle = bundle;
        this.runMode = runMode == null ? RunMode.LEVEL15 : runMode;
        this.startVariantId = startVariantId == null || startVariantId.isBlank()
                ? "lv10" : startVariantId;
        this.stage = stage;
        this.trainingQuestIndex = 0;
        this.questPackIndex = 0;
        this.nextActionAtMs = Math.max(0L, nextActionAtMs);
        this.blockReason = "";
        revision++;
    }

    public synchronized RunMode runMode() {
        return runMode;
    }

    public synchronized String startVariantId() {
        return startVariantId;
    }

    public synchronized Stage stage() {
        return stage;
    }

    public synchronized void stage(Stage stage, long nextActionAtMs) {
        if (this.stage == stage && this.nextActionAtMs == nextActionAtMs && blockReason.isEmpty()) {
            return;
        }
        this.stage = stage;
        this.nextActionAtMs = nextActionAtMs;
        blockReason = "";
        revision++;
    }

    public synchronized int trainingQuestIndex() {
        return trainingQuestIndex;
    }

    public synchronized void trainingQuestIndex(int value) {
        int next = Math.max(0, value);
        if (trainingQuestIndex != next) {
            trainingQuestIndex = next;
            revision++;
        }
    }

    public synchronized int questPackIndex() {
        return questPackIndex;
    }

    public synchronized void questPackIndex(int value) {
        int next = Math.max(0, value);
        if (questPackIndex != next) {
            questPackIndex = next;
            revision++;
        }
    }

    public synchronized boolean ready(long nowMs) {
        return nowMs >= nextActionAtMs;
    }

    public synchronized void block(String reason) {
        stage = Stage.BLOCKED;
        blockReason = reason == null ? "" : reason;
        revision++;
    }

    public synchronized String blockReason() {
        return blockReason;
    }

    synchronized AgentCareerProgressionCheckpoint pendingCheckpoint(int characterId, long nowMs) {
        if (bundle == null || revision == persistedRevision) {
            return null;
        }
        return new AgentCareerProgressionCheckpoint(2, characterId,
                bundle.bundleId(), bundle.bundleVersion(), runMode, startVariantId, stage,
                trainingQuestIndex, questPackIndex, nextActionAtMs, blockReason, revision, nowMs);
    }

    synchronized void markPersisted(long savedRevision) {
        persistedRevision = Math.max(persistedRevision, savedRevision);
    }

    synchronized void restore(AgentCareerBuildBundle bundle,
                              AgentCareerProgressionCheckpoint checkpoint) {
        this.bundle = bundle;
        this.runMode = checkpoint.runMode();
        this.startVariantId = checkpoint.startVariantId();
        this.stage = checkpoint.stage();
        this.trainingQuestIndex = checkpoint.trainingQuestIndex();
        this.questPackIndex = checkpoint.questPackIndex();
        this.nextActionAtMs = checkpoint.nextActionAtMs();
        this.blockReason = checkpoint.blockReason();
        this.revision = checkpoint.stateRevision();
        this.persistedRevision = checkpoint.stateRevision();
    }
}
