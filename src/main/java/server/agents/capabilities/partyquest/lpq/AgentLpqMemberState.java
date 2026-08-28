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
    private int roomProgressTelemetryMapId;
    private String roomProgressTelemetrySignature = "";
    private long roomProgressTelemetryAtMs;
    private int roomExitMapId;
    private long roomExitStartedAtMs;
    private int roomExitProtectionMapId;
    private boolean roomExitProtectionPrepared;
    private int roomApproachMapId;
    private Point roomApproachPosition;
    private int traversalSourceMapId;
    private int traversalDestinationMapId;
    private long traversalBestDistanceSq = Long.MAX_VALUE;
    private Point traversalObservedPosition;
    private long traversalProgressAtMs;
    private int npcRallyStage;
    private int npcRallyMapId;
    private long npcRallyBestDistanceSq = Long.MAX_VALUE;
    private Point npcRallyObservedPosition;
    private long npcRallyProgressAtMs;
    private long npcRallyLastRetryAtMs;
    private String announcedIntentKey = "";
    private int passReportStage;
    private int passReportMapId;
    private int passReportCount;
    private int couponRegroupRecoveredStage;
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
        boolean changed = this.role != role || assignedMapId != mapId;
        if (changed) {
            nextActionAtMs = 0L;
            clearTraversalProgress();
            clearNpcRallyProgress();
        }
        if (assignedMapId != mapId) {
            clearReactorWork();
            clearRoomMarker();
            clearRoomPassCollection();
            clearRoomCombatProgress();
            clearRoomProgressTelemetry();
            clearRoomApproachProgress();
            clearRoomExitProgress();
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
        clearRoomProgressTelemetry();
        clearRoomExitProgress();
        clearRoomApproachProgress();
        clearTraversalProgress();
        clearNpcRallyProgress();
        announcedIntentKey = "";
        passReportStage = 0;
        passReportMapId = 0;
        passReportCount = 0;
        couponRegroupRecoveredStage = 0;
    }

    public void assignPlatform(int platform) {
        if (platform < 0 || platform > 9) throw new IllegalArgumentException("LPQ platform must be 0-9");
        if (assignedPlatform != platform) clearTraversalProgress();
        assignedPlatform = platform;
    }

    public void deferUntil(long nextActionAtMs) { this.nextActionAtMs = Math.max(0L, nextActionAtMs); }

    public boolean couponRegroupRecoveredFor(int stage) {
        return stage > 0 && couponRegroupRecoveredStage == stage;
    }

    public void markCouponRegroupRecovered(int stage) {
        if (stage <= 0) throw new IllegalArgumentException("valid LPQ regroup stage is required");
        couponRegroupRecoveredStage = stage;
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
    }

    public void markReactorTargetBroken(boolean waitForSpawnCleanup) {
        reactorTargetMapId = 0;
        reactorTargetObjectId = 0;
        reactorTargetCommittedAtMs = 0L;
        reactorTargetHitOnce = false;
        reactorSpawnCleanupPending = waitForSpawnCleanup;
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

    /** Keeps NPC regroup recovery independent from portal/reactor traversal history. */
    public long observeNpcRallyProgress(int stage, int mapId, long distanceSq, long nowMs) {
        return observeNpcRallyProgress(stage, mapId, null, distanceSq, nowMs);
    }

    public long observeNpcRallyProgress(
            int stage, int mapId, Point position, long distanceSq, long nowMs) {
        if (stage <= 0 || mapId <= 0 || distanceSq < 0L || nowMs < 0L) return 0L;
        boolean newRally = npcRallyStage != stage || npcRallyMapId != mapId
                || npcRallyProgressAtMs == 0L;
        boolean closer = npcRallyBestDistanceSq != Long.MAX_VALUE
                && distanceSq != Long.MAX_VALUE
                && Math.sqrt(npcRallyBestDistanceSq) - Math.sqrt(distanceSq) >= 16.0d;
        boolean moved = position != null && npcRallyObservedPosition != null
                && npcRallyObservedPosition.distanceSq(position) >= 16L * 16L;
        if (newRally || closer || moved) {
            npcRallyStage = stage;
            npcRallyMapId = mapId;
            npcRallyBestDistanceSq = Math.min(npcRallyBestDistanceSq, distanceSq);
            if (newRally) npcRallyBestDistanceSq = distanceSq;
            npcRallyObservedPosition = position == null ? null : new Point(position);
            npcRallyProgressAtMs = nowMs;
            npcRallyLastRetryAtMs = nowMs;
            return 0L;
        }
        return Math.max(0L, nowMs - npcRallyProgressAtMs);
    }

    public boolean claimNpcRallyRetry(long inactiveForMs, long nowMs, long retryIntervalMs) {
        if (inactiveForMs < retryIntervalMs || retryIntervalMs <= 0L || nowMs < 0L) return false;
        if (npcRallyLastRetryAtMs != 0L
                && nowMs - npcRallyLastRetryAtMs < retryIntervalMs) return false;
        npcRallyLastRetryAtMs = nowMs;
        return true;
    }

    public void clearNpcRallyProgress() {
        npcRallyStage = 0;
        npcRallyMapId = 0;
        npcRallyBestDistanceSq = Long.MAX_VALUE;
        npcRallyObservedPosition = null;
        npcRallyProgressAtMs = 0L;
        npcRallyLastRetryAtMs = 0L;
    }
}
