package server.agents.capabilities.partyquest.lpq;

import java.awt.Point;

/** Per-participant state owned only by an LPQ session. */
public final class AgentLpqMemberState {
    public enum MemberType { AGENT, HUMAN }
    public enum Role {
        EVENT_LEADER, GENERAL, MAGIC_ATTACKER, PHYSICAL_ATTACKER,
        TELEPORT_RUNNER, DARK_SIGHT_RUNNER, RANGED_TRIGGER,
        PLATFORM_HOLDER, PLATFORM_MOVER, BOSS_ATTACKER, NPC_RALLY
    }
    public enum RewardState { PENDING, CLAIMING, CLAIMED, FORFEITED }
    private final int characterId;
    private final MemberType memberType;
    private Role role = Role.GENERAL;
    private int assignedMapId;
    private int assignedPlatform;
    private long nextActionAtMs;
    private int reactorTargetMapId;
    private int reactorTargetObjectId;
    private int reactorApproachMapId;
    private int reactorApproachObjectId;
    private long reactorTargetCommittedAtMs;
    private boolean reactorTargetHitOnce;
    private boolean reactorSpawnCleanupPending;
    private int roomMarkerMapId;
    private boolean roomMarkerDropped;
    private int roomPassBaselineMapId;
    private int roomPassBaselineCount;
    private int roomCombatMapId;
    private int roomCombatTargetObjectId;
    private int roomCombatTargetHp;
    private long roomCombatProgressAtMs;
    private int roomRecoveryMapId;
    private int roomRecoveryMobCount;
    private long roomRecoveryMobHp;
    private int roomRecoveryPassCount;
    private long roomRecoveryProgressAtMs;
    private boolean roomRecoveryAssistApplied;
    private boolean roomRecoveryHardApplied;
    private int roomProgressTelemetryMapId;
    private String roomProgressTelemetrySignature = "";
    private long roomProgressTelemetryAtMs;
    private int roomExitMapId;
    private long roomExitStartedAtMs;
    private int roomTimingMapId;
    private long roomTimingStartedAtMs;
    private int stageFiveCeilingRecoveryMapId;
    private int stageFiveAssistMapId;
    private int stageFiveAssistPassCount;
    private long stageFiveAssistUnobservedMs;
    private long stageFiveAssistLastTickAtMs;
    private boolean stageFiveAssistApplied;
    private int roomExitProtectionMapId;
    private boolean roomExitProtectionPrepared;
    private int roomApproachMapId;
    private Point roomApproachPosition;
    private int traversalSourceMapId;
    private int traversalDestinationMapId;
    private long traversalBestDistanceSq = Long.MAX_VALUE;
    private Point traversalObservedPosition;
    private long traversalProgressAtMs;
    private int destinationApproachStage;
    private int destinationApproachMapId;
    private long destinationApproachBestDistanceSq = Long.MAX_VALUE;
    private Point destinationApproachObservedPosition;
    private long destinationApproachProgressAtMs;
    private long destinationApproachLastRetryAtMs;
    private String announcedIntentKey = "";
    private int passReportStage;
    private int passReportMapId;
    private int passReportCount;
    private int balloonRallyRecoveredStage;
    private int stageTwoRallyBranch;
    private boolean stageFiveDarkSightRunner;
    private int stageFiveUnhandedPasses;
    private RewardState rewardState = RewardState.PENDING;

    public AgentLpqMemberState(int characterId, MemberType memberType) {
        if (characterId <= 0 || memberType == null) {
            throw new IllegalArgumentException("valid LPQ member values are required");
        }
        this.characterId = characterId;
        this.memberType = memberType;
    }

    public int characterId() { return characterId; }
    public MemberType memberType() { return memberType; }
    public Role role() { return role; }
    public int assignedMapId() { return assignedMapId; }
    public int assignedPlatform() { return assignedPlatform; }
    public long nextActionAtMs() { return nextActionAtMs; }
    public boolean stageFiveDarkSightRunner() { return stageFiveDarkSightRunner; }
    public int stageFiveUnhandedPasses() { return stageFiveUnhandedPasses; }
    public RewardState rewardState() { return rewardState; }
    public boolean rewardClaimed() { return rewardState == RewardState.CLAIMED; }
    public boolean rewardResolved() {
        return rewardState == RewardState.CLAIMED || rewardState == RewardState.FORFEITED;
    }
    boolean beginRewardClaim() {
        if (rewardState != RewardState.PENDING) return false;
        rewardState = RewardState.CLAIMING;
        return true;
    }
    boolean completeRewardClaim() {
        if (rewardState != RewardState.CLAIMING) return false;
        rewardState = RewardState.CLAIMED;
        return true;
    }
    void cancelRewardClaim() {
        if (rewardState == RewardState.CLAIMING) rewardState = RewardState.PENDING;
    }
    void forfeitReward() {
        if (rewardState == RewardState.PENDING) rewardState = RewardState.FORFEITED;
    }
    public int reactorTargetMapId() { return reactorTargetMapId; }
    public int reactorTargetObjectId() { return reactorTargetObjectId; }
    public int reactorApproachMapId() { return reactorApproachMapId; }
    public int reactorApproachObjectId() { return reactorApproachObjectId; }
    public long reactorTargetCommittedAtMs() { return reactorTargetCommittedAtMs; }
    public boolean reactorTargetHitOnce() { return reactorTargetHitOnce; }
    public boolean reactorSpawnCleanupPending() { return reactorSpawnCleanupPending; }
    public boolean roomMarkerDroppedFor(int mapId) {
        return mapId > 0 && roomMarkerMapId == mapId && roomMarkerDropped;
    }

    public int roomPassesCollectedFor(int mapId, int currentPassCount) {
        if (mapId <= 0 || roomPassBaselineMapId != mapId) return 0;
        return Math.max(0, currentPassCount - roomPassBaselineCount);
    }

    public void assign(Role role, int mapId) {
        if (role == null || mapId < 0) throw new IllegalArgumentException("valid LPQ assignment is required");
        if (role == Role.DARK_SIGHT_RUNNER) stageFiveDarkSightRunner = true;
        boolean changed = this.role != role || assignedMapId != mapId;
        if (changed) {
            nextActionAtMs = 0L;
            clearTraversalProgress();
            clearDestinationApproachProgress();
        }
        if (assignedMapId != mapId) {
            clearReactorWork();
            clearRoomMarker();
            clearRoomPassCollection();
            clearRoomCombatProgress();
            clearRoomRecoveryProgress();
            clearRoomProgressTelemetry();
            clearRoomApproachProgress();
            clearRoomExitProgress();
            clearRoomTiming();
        }
        this.role = role;
        this.assignedMapId = mapId;
    }

    /** Atomically discards every prior-stage action, claim, deadline, and progress clock. */
    public void resetForStage(Role baselineRole) {
        if (baselineRole == null) throw new IllegalArgumentException("LPQ baseline role is required");
        role = baselineRole;
        assignedMapId = 0;
        assignedPlatform = 0;
        nextActionAtMs = 0L;
        clearReactorWork();
        clearRoomMarker();
        clearRoomPassCollection();
        clearRoomCombatProgress();
        clearRoomRecoveryProgress();
        clearRoomProgressTelemetry();
        clearRoomExitProgress();
        clearRoomTiming();
        clearRoomApproachProgress();
        clearTraversalProgress();
        clearDestinationApproachProgress();
        announcedIntentKey = "";
        passReportStage = 0;
        passReportMapId = 0;
        passReportCount = 0;
        balloonRallyRecoveredStage = 0;
        stageTwoRallyBranch = 0;
        stageFiveDarkSightRunner = false;
        stageFiveUnhandedPasses = 0;
    }

    public void recordStageFiveEarnedPasses(int count) {
        if (count < 0) throw new IllegalArgumentException("earned Stage 5 passes cannot be negative");
        stageFiveUnhandedPasses = Math.max(stageFiveUnhandedPasses, count);
    }

    public void handOffStageFivePasses(int count) {
        if (count < 0) throw new IllegalArgumentException("handed-off Stage 5 passes cannot be negative");
        stageFiveUnhandedPasses = Math.max(0, stageFiveUnhandedPasses - count);
    }

    public void assignPlatform(int platform) {
        if (platform < 0 || platform > 9) throw new IllegalArgumentException("LPQ platform must be 0-9");
        if (assignedPlatform != platform) clearTraversalProgress();
        assignedPlatform = platform;
    }

    public void deferUntil(long nextActionAtMs) { this.nextActionAtMs = Math.max(0L, nextActionAtMs); }

    public boolean balloonRallyRecoveredFor(int stage) {
        return stage > 0 && balloonRallyRecoveredStage == stage;
    }

    public void markBalloonRallyRecovered(int stage) {
        if (stage <= 0) throw new IllegalArgumentException("valid LPQ regroup stage is required");
        balloonRallyRecoveredStage = stage;
    }

    int stageTwoRallyBranch() { return stageTwoRallyBranch; }

    void selectStageTwoRallyBranch(int branch) {
        if (branch != 1 && branch != 2) {
            throw new IllegalArgumentException("LPQ Stage 2 rally branch must be left or right");
        }
        if (stageTwoRallyBranch == 0) stageTwoRallyBranch = branch;
    }

    public void commitReactorTarget(int mapId, int objectId) {
        commitReactorTarget(mapId, objectId, System.currentTimeMillis());
    }

    public void commitReactorTarget(int mapId, int objectId, long nowMs) {
        if (mapId <= 0 || objectId <= 0) {
            throw new IllegalArgumentException("valid LPQ reactor target is required");
        }
        reactorTargetMapId = mapId;
        reactorTargetObjectId = objectId;
        reactorTargetHitOnce = false;
        if (reactorTargetCommittedAtMs == 0L) {
            reactorTargetCommittedAtMs = Math.max(0L, nowMs);
        }
        reactorSpawnCleanupPending = false;
        clearReactorApproach();
    }

    public void beginReactorApproach(int mapId, int objectId) {
        if (mapId <= 0 || objectId <= 0 || reactorTargetObjectId != 0) {
            throw new IllegalArgumentException("valid provisional LPQ reactor approach is required");
        }
        reactorApproachMapId = mapId;
        reactorApproachObjectId = objectId;
    }

    public void clearReactorApproach() {
        reactorApproachMapId = 0;
        reactorApproachObjectId = 0;
    }

    public void markReactorTargetBroken(boolean waitForSpawnCleanup) {
        reactorTargetMapId = 0;
        reactorTargetObjectId = 0;
        reactorTargetCommittedAtMs = 0L;
        reactorTargetHitOnce = false;
        reactorSpawnCleanupPending = waitForSpawnCleanup;
        clearReactorApproach();
    }

    public void markReactorTargetHit() {
        if (reactorTargetObjectId <= 0) {
            throw new IllegalStateException("a committed LPQ reactor is required");
        }
        reactorTargetHitOnce = true;
    }

    public void finishReactorSpawnCleanup() {
        reactorSpawnCleanupPending = false;
    }

    public void clearReactorWork() {
        reactorTargetMapId = 0;
        reactorTargetObjectId = 0;
        reactorTargetCommittedAtMs = 0L;
        reactorTargetHitOnce = false;
        reactorSpawnCleanupPending = false;
        clearReactorApproach();
    }

    public void markRoomMarkerDropped(int mapId) {
        if (mapId <= 0 || assignedMapId != mapId) {
            throw new IllegalArgumentException("room marker must match the assigned LPQ room");
        }
        roomMarkerMapId = mapId;
        roomMarkerDropped = true;
    }

    public void clearRoomMarker() {
        roomMarkerMapId = 0;
        roomMarkerDropped = false;
    }

    public void beginRoomPassCollection(int mapId, int currentPassCount) {
        if (mapId <= 0 || assignedMapId != mapId || currentPassCount < 0) {
            throw new IllegalArgumentException("room pass baseline must match the assigned LPQ room");
        }
        roomPassBaselineMapId = mapId;
        roomPassBaselineCount = currentPassCount;
    }

    public void clearRoomPassCollection() {
        roomPassBaselineMapId = 0;
        roomPassBaselineCount = 0;
    }

    /** Returns how long the same room target has gone without authoritative HP progress. */
    public long observeRoomCombatTarget(int mapId, int objectId, int hp, long nowMs) {
        if (mapId <= 0 || objectId <= 0 || hp < 0 || nowMs < 0L) return 0L;
        if (roomCombatMapId != mapId || roomCombatTargetObjectId != objectId
                || hp < roomCombatTargetHp || roomCombatProgressAtMs == 0L) {
            roomCombatMapId = mapId;
            roomCombatTargetObjectId = objectId;
            roomCombatTargetHp = hp;
            roomCombatProgressAtMs = nowMs;
            return 0L;
        }
        roomCombatTargetHp = hp;
        return Math.max(0L, nowMs - roomCombatProgressAtMs);
    }

    public void clearRoomCombatProgress() {
        roomCombatMapId = 0;
        roomCombatTargetObjectId = 0;
        roomCombatTargetHp = 0;
        roomCombatProgressAtMs = 0L;
    }

    /** Returns time since mob HP/count or collected-pass count last improved in this room. */
    public long observeRoomRecoveryProgress(int mapId, int mobCount, long mobHp,
                                            int passCount, long nowMs) {
        if (mapId <= 0 || mobCount < 0 || mobHp < 0L || passCount < 0 || nowMs < 0L) return 0L;
        if (roomRecoveryMapId != mapId || roomRecoveryProgressAtMs == 0L) {
            roomRecoveryMapId = mapId;
            roomRecoveryMobCount = mobCount;
            roomRecoveryMobHp = mobHp;
            roomRecoveryPassCount = passCount;
            roomRecoveryProgressAtMs = nowMs;
            roomRecoveryAssistApplied = false;
            roomRecoveryHardApplied = false;
            return 0L;
        }
        boolean progressed = mobCount < roomRecoveryMobCount
                || mobHp < roomRecoveryMobHp
                || passCount > roomRecoveryPassCount;
        roomRecoveryMobCount = mobCount;
        roomRecoveryMobHp = mobHp;
        roomRecoveryPassCount = passCount;
        if (progressed) roomRecoveryProgressAtMs = nowMs;
        return Math.max(0L, nowMs - roomRecoveryProgressAtMs);
    }

    public boolean roomRecoveryAssistAppliedFor(int mapId) {
        return mapId > 0 && roomRecoveryMapId == mapId && roomRecoveryAssistApplied;
    }

    public void markRoomRecoveryAssistApplied(int mapId) {
        if (mapId <= 0 || roomRecoveryMapId != mapId) {
            throw new IllegalArgumentException("room recovery assist must match the active LPQ room");
        }
        roomRecoveryAssistApplied = true;
    }

    public boolean roomRecoveryHardAppliedFor(int mapId) {
        return mapId > 0 && roomRecoveryMapId == mapId && roomRecoveryHardApplied;
    }

    public void markRoomRecoveryHardApplied(int mapId) {
        if (mapId <= 0 || roomRecoveryMapId != mapId) {
            throw new IllegalArgumentException("hard room recovery must match the active LPQ room");
        }
        roomRecoveryHardApplied = true;
    }

    public void clearRoomRecoveryProgress() {
        roomRecoveryMapId = 0;
        roomRecoveryMobCount = 0;
        roomRecoveryMobHp = 0L;
        roomRecoveryPassCount = 0;
        roomRecoveryProgressAtMs = 0L;
        roomRecoveryAssistApplied = false;
        roomRecoveryHardApplied = false;
    }

    public boolean shouldReportRoomProgress(int mapId, String signature,
                                            long nowMs, long intervalMs) {
        if (mapId <= 0 || signature == null || nowMs < 0L) return false;
        boolean changed = roomProgressTelemetryMapId != mapId
                || !signature.equals(roomProgressTelemetrySignature);
        boolean intervalElapsed = nowMs - roomProgressTelemetryAtMs >= Math.max(1L, intervalMs);
        if (!changed && !intervalElapsed) return false;
        roomProgressTelemetryMapId = mapId;
        roomProgressTelemetrySignature = signature;
        roomProgressTelemetryAtMs = nowMs;
        return true;
    }

    public void clearRoomProgressTelemetry() {
        roomProgressTelemetryMapId = 0;
        roomProgressTelemetrySignature = "";
        roomProgressTelemetryAtMs = 0L;
    }

    /** Establishes the completed-room context used while preparing a protected exit. */
    public void beginRoomExit(int mapId, long nowMs) {
        if (mapId <= 0) throw new IllegalArgumentException("valid LPQ room exit is required");
        if (roomExitMapId != mapId) {
            roomExitMapId = mapId;
            roomExitStartedAtMs = Math.max(0L, nowMs);
            roomExitProtectionMapId = 0;
            roomExitProtectionPrepared = false;
        }
    }

    public long roomExitElapsed(int mapId, long nowMs) {
        if (mapId <= 0 || roomExitMapId != mapId || roomExitStartedAtMs <= 0L) return 0L;
        return Math.max(0L, nowMs - roomExitStartedAtMs);
    }

    public boolean roomExitProtectionPreparedFor(int mapId) {
        return mapId > 0 && roomExitProtectionMapId == mapId && roomExitProtectionPrepared;
    }

    public void markRoomExitProtectionPrepared(int mapId) {
        if (mapId <= 0 || roomExitMapId != mapId) {
            throw new IllegalArgumentException("room exit protection must match the completed LPQ room");
        }
        roomExitProtectionMapId = mapId;
        roomExitProtectionPrepared = true;
    }

    public void clearRoomExitProgress() {
        roomExitMapId = 0;
        roomExitStartedAtMs = 0L;
        roomExitProtectionMapId = 0;
        roomExitProtectionPrepared = false;
    }

    public boolean beginRoomTiming(int mapId, long nowMs) {
        if (mapId <= 0 || nowMs < 0L) return false;
        if (roomTimingMapId == mapId && roomTimingStartedAtMs > 0L) return false;
        roomTimingMapId = mapId;
        roomTimingStartedAtMs = nowMs;
        return true;
    }

    public long finishRoomTiming(int mapId, long nowMs) {
        if (mapId <= 0 || roomTimingMapId != mapId || roomTimingStartedAtMs <= 0L) return -1L;
        long elapsedMs = Math.max(0L, nowMs - roomTimingStartedAtMs);
        clearRoomTiming();
        return elapsedMs;
    }

    public long roomTimingElapsed(int mapId, long nowMs) {
        if (mapId <= 0 || roomTimingMapId != mapId || roomTimingStartedAtMs <= 0L) return 0L;
        return Math.max(0L, nowMs - roomTimingStartedAtMs);
    }

    public boolean stageFiveCeilingRecoveryAppliedFor(int mapId) {
        return mapId > 0 && stageFiveCeilingRecoveryMapId == mapId;
    }

    public void markStageFiveCeilingRecoveryApplied(int mapId) {
        if (mapId <= 0 || roomTimingMapId != mapId) {
            throw new IllegalArgumentException("Stage 5 ceiling recovery must match the active room");
        }
        stageFiveCeilingRecoveryMapId = mapId;
    }

    public void clearRoomTiming() {
        roomTimingMapId = 0;
        roomTimingStartedAtMs = 0L;
        stageFiveCeilingRecoveryMapId = 0;
        clearRoomRecoveryProgress();
        clearStageFiveAssist();
    }

    public long observeStageFiveAssist(
            int mapId, int roomPassCount, boolean observed, long nowMs) {
        if (mapId <= 0 || roomPassCount < 0 || nowMs < 0L) return 0L;
        if (stageFiveAssistMapId != mapId
                || stageFiveAssistPassCount != roomPassCount) {
            stageFiveAssistMapId = mapId;
            stageFiveAssistPassCount = roomPassCount;
            stageFiveAssistUnobservedMs = 0L;
            stageFiveAssistLastTickAtMs = nowMs;
            stageFiveAssistApplied = false;
            return 0L;
        }
        if (stageFiveAssistLastTickAtMs > 0L && !observed) {
            stageFiveAssistUnobservedMs += Math.max(0L, nowMs - stageFiveAssistLastTickAtMs);
        }
        stageFiveAssistLastTickAtMs = nowMs;
        return stageFiveAssistUnobservedMs;
    }

    public boolean stageFiveAssistApplied() { return stageFiveAssistApplied; }
    public void markStageFiveAssistApplied() { stageFiveAssistApplied = true; }

    public void clearStageFiveAssist() {
        stageFiveAssistMapId = 0;
        stageFiveAssistPassCount = 0;
        stageFiveAssistUnobservedMs = 0L;
        stageFiveAssistLastTickAtMs = 0L;
        stageFiveAssistApplied = false;
    }

    public boolean claimIntentAnnouncement(String key) {
        if (key == null || key.isBlank() || key.equals(announcedIntentKey)) return false;
        announcedIntentKey = key;
        return true;
    }

    public boolean shouldReportPassProgress(int stage, int mapId, int count, int quota) {
        if (stage <= 0 || mapId <= 0 || count <= 0 || quota <= 0) return false;
        if (passReportStage != stage || passReportMapId != mapId) {
            passReportStage = stage;
            passReportMapId = mapId;
            passReportCount = 0;
        }
        if (count <= passReportCount) return false;
        boolean due = quota <= 4 || passReportCount == 0 || count >= quota
                || count / 5 > passReportCount / 5;
        if (due) passReportCount = count;
        return due;
    }

    /** Heartbeats a reservation only when the assigned member really moved toward its room. */
    public boolean observeRoomApproachProgress(int mapId, Point position) {
        if (mapId <= 0 || assignedMapId != mapId || position == null) return false;
        if (roomApproachMapId != mapId || roomApproachPosition == null
                || roomApproachPosition.distanceSq(position) >= 16L * 16L) {
            roomApproachMapId = mapId;
            roomApproachPosition = new Point(position);
            return true;
        }
        return false;
    }

    public void clearRoomApproachProgress() {
        roomApproachMapId = 0;
        roomApproachPosition = null;
    }

    /** Returns time since this member last made measurable progress toward an authored exit. */
    public long observeTraversalProgress(int sourceMapId, int destinationMapId,
                                         long distanceSq, long nowMs) {
        return observeTraversalProgress(
                sourceMapId, destinationMapId, null, distanceSq, nowMs);
    }

    public long observeTraversalProgress(int sourceMapId, int destinationMapId,
                                         Point position, long distanceSq, long nowMs) {
        if (sourceMapId <= 0 || destinationMapId <= 0 || distanceSq < 0L || nowMs < 0L) {
            return 0L;
        }
        boolean newTraversal = traversalSourceMapId != sourceMapId
                || traversalDestinationMapId != destinationMapId
                || traversalProgressAtMs == 0L;
        boolean closer = traversalBestDistanceSq != Long.MAX_VALUE
                && distanceSq != Long.MAX_VALUE
                && Math.sqrt(traversalBestDistanceSq) - Math.sqrt(distanceSq) >= 16.0d;
        boolean moved = position != null && traversalObservedPosition != null
                && traversalObservedPosition.distanceSq(position) >= 16L * 16L;
        if (newTraversal || closer || moved) {
            traversalSourceMapId = sourceMapId;
            traversalDestinationMapId = destinationMapId;
            traversalBestDistanceSq = Math.min(traversalBestDistanceSq, distanceSq);
            if (newTraversal) traversalBestDistanceSq = distanceSq;
            traversalObservedPosition = position == null ? null : new Point(position);
            traversalProgressAtMs = nowMs;
            return 0L;
        }
        return Math.max(0L, nowMs - traversalProgressAtMs);
    }

    public void clearTraversalProgress() {
        traversalSourceMapId = 0;
        traversalDestinationMapId = 0;
        traversalBestDistanceSq = Long.MAX_VALUE;
        traversalObservedPosition = null;
        traversalProgressAtMs = 0L;
    }

    /** Keeps a terminal NPC approach independent from portal/reactor traversal history. */
    public long observeDestinationApproachProgress(
            int stage, int mapId, Point position, long distanceSq, long nowMs) {
        if (stage <= 0 || mapId <= 0 || distanceSq < 0L || nowMs < 0L) return 0L;
        boolean newApproach = destinationApproachStage != stage
                || destinationApproachMapId != mapId
                || destinationApproachProgressAtMs == 0L;
        boolean closer = destinationApproachBestDistanceSq != Long.MAX_VALUE
                && distanceSq != Long.MAX_VALUE
                && Math.sqrt(destinationApproachBestDistanceSq) - Math.sqrt(distanceSq) >= 16.0d;
        boolean moved = position != null && destinationApproachObservedPosition != null
                && destinationApproachObservedPosition.distanceSq(position) >= 16L * 16L;
        if (newApproach || closer || moved) {
            destinationApproachStage = stage;
            destinationApproachMapId = mapId;
            destinationApproachBestDistanceSq = Math.min(
                    destinationApproachBestDistanceSq, distanceSq);
            if (newApproach) destinationApproachBestDistanceSq = distanceSq;
            destinationApproachObservedPosition = position == null ? null : new Point(position);
            destinationApproachProgressAtMs = nowMs;
            destinationApproachLastRetryAtMs = nowMs;
            return 0L;
        }
        return Math.max(0L, nowMs - destinationApproachProgressAtMs);
    }

    public boolean claimDestinationApproachRetry(
            long inactiveForMs, long nowMs, long retryIntervalMs) {
        if (inactiveForMs < retryIntervalMs || retryIntervalMs <= 0L || nowMs < 0L) return false;
        if (destinationApproachLastRetryAtMs != 0L
                && nowMs - destinationApproachLastRetryAtMs < retryIntervalMs) return false;
        destinationApproachLastRetryAtMs = nowMs;
        return true;
    }

    public void clearDestinationApproachProgress() {
        destinationApproachStage = 0;
        destinationApproachMapId = 0;
        destinationApproachBestDistanceSq = Long.MAX_VALUE;
        destinationApproachObservedPosition = null;
        destinationApproachProgressAtMs = 0L;
        destinationApproachLastRetryAtMs = 0L;
    }
}
