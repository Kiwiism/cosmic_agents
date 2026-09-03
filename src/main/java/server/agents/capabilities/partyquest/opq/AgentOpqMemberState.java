package server.agents.capabilities.partyquest.opq;

import java.awt.Point;

/** Per-member OPQ state; no room worker may mutate another member's action state. */
public final class AgentOpqMemberState {
    public enum MemberType { AGENT, HUMAN }
    public enum Role {
        LEADER, ENTRANCE_COLLECTOR, SEALED_PLATFORM, WALKWAY_COLLECTOR,
        STORAGE_RUNNER, LOBBY_RUNNER, LOUNGE_RUNNER, WAY_UP_RUNNER,
        PIECE_CARRIER, GARDEN_SEEDER, BOSS_ATTACKER, BOSS_SUMMON_CLEARER, IDLE
    }
    private final int characterId;
    private final MemberType memberType;
    private Role role = Role.IDLE;
    private AgentOpqDefinition.Room assignedRoom;
    private int assignedSubroomMapId;
    private int assignedPlatform;
    private long nextActionAtMs;
    private int committedReactorMapId;
    private int committedReactorObjectId;
    private Point lastPosition;
    private long lastMovementProgressAtMs;
    private int portalRow;
    private int portalChoice;
    private int pendingPortalMapId;
    private int pendingPortalRow = -1;
    private int pendingPortalChoice = -1;
    private int pendingPortalSourceY;
    private int loungeBaselineMapId;
    private int loungeBaselineCount;
    private int bossObjectId;
    private int bossHp = -1;

    public AgentOpqMemberState(int characterId, MemberType memberType) {
        if (characterId <= 0 || memberType == null) throw new IllegalArgumentException("valid OPQ member required");
        this.characterId = characterId;
        this.memberType = memberType;
    }

    public int characterId() { return characterId; }
    public MemberType memberType() { return memberType; }
    public Role role() { return role; }
    public AgentOpqDefinition.Room assignedRoom() { return assignedRoom; }
    public int assignedSubroomMapId() { return assignedSubroomMapId; }
    public int assignedPlatform() { return assignedPlatform; }
    public long nextActionAtMs() { return nextActionAtMs; }
    public int committedReactorMapId() { return committedReactorMapId; }
    public int committedReactorObjectId() { return committedReactorObjectId; }
    public int portalRow() { return portalRow; }
    public int portalChoice() { return portalChoice; }
    public boolean portalObservationPending(int mapId) { return pendingPortalMapId == mapId && pendingPortalRow >= 0; }
    public int pendingPortalRow() { return pendingPortalRow; }
    public int pendingPortalChoice() { return pendingPortalChoice; }
    public int pendingPortalSourceY() { return pendingPortalSourceY; }

    public void assign(Role role, AgentOpqDefinition.Room room, int subroomMapId) {
        if (role == null || subroomMapId < 0) throw new IllegalArgumentException("valid OPQ assignment required");
        if (assignedRoom != room || assignedSubroomMapId != subroomMapId) clearLocalWork();
        this.role = role;
        this.assignedRoom = room;
        this.assignedSubroomMapId = subroomMapId;
    }

    public void assignPlatform(int platform) {
        if (platform < 0 || platform > 2) throw new IllegalArgumentException("OPQ sealed platform must be 0-2");
        assignedPlatform = platform;
    }

    public void deferUntil(long timeMs) { nextActionAtMs = Math.max(0L, timeMs); }
    public void commitReactor(int mapId, int objectId) {
        if (mapId <= 0 || objectId <= 0) throw new IllegalArgumentException("valid OPQ reactor commitment required");
        committedReactorMapId = mapId;
        committedReactorObjectId = objectId;
    }
    public void clearReactor() { committedReactorMapId = 0; committedReactorObjectId = 0; }
    public void advancePortalChoice() { portalChoice++; }
    public void solvePortalRow() { portalRow++; portalChoice = 0; }
    public void beginPortalObservation(int mapId, int row, int choice, int sourceY) {
        pendingPortalMapId = mapId;
        pendingPortalRow = row;
        pendingPortalChoice = choice;
        pendingPortalSourceY = sourceY;
    }
    public void clearPortalObservation() {
        pendingPortalMapId = 0;
        pendingPortalRow = -1;
        pendingPortalChoice = -1;
        pendingPortalSourceY = 0;
    }
    public void beginLoungeCollection(int mapId, int currentCount) {
        if (loungeBaselineMapId != mapId) {
            loungeBaselineMapId = mapId;
            loungeBaselineCount = Math.max(0, currentCount);
        }
    }
    public int loungeCollected(int mapId, int currentCount) {
        return loungeBaselineMapId == mapId ? Math.max(0, currentCount - loungeBaselineCount) : 0;
    }

    public boolean observeBossCombat(int objectId, int hp) {
        if (objectId <= 0 || hp < 0) throw new IllegalArgumentException("valid OPQ boss observation required");
        boolean progress = bossObjectId != objectId || bossHp < 0 || hp < bossHp;
        bossObjectId = objectId;
        bossHp = hp;
        return progress;
    }

    public void clearBossCombat() { bossObjectId = 0; bossHp = -1; }

    public boolean observeMovement(Point position, long nowMs) {
        if (position == null || nowMs < 0L) return false;
        if (lastPosition == null || lastPosition.distanceSq(position) >= 16L * 16L) {
            lastPosition = new Point(position);
            lastMovementProgressAtMs = nowMs;
            return true;
        }
        return false;
    }
    public long movementStalledFor(long nowMs) {
        return lastMovementProgressAtMs == 0L ? 0L : Math.max(0L, nowMs - lastMovementProgressAtMs);
    }

    public void clearLocalWork() {
        nextActionAtMs = 0L;
        clearReactor();
        lastPosition = null;
        lastMovementProgressAtMs = 0L;
        portalRow = 0;
        portalChoice = 0;
        clearPortalObservation();
        loungeBaselineMapId = 0;
        loungeBaselineCount = 0;
        clearBossCombat();
    }
}
