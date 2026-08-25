package server.agents.capabilities.partyquest.lpq;

/** Per-participant state owned only by an LPQ session. */
public final class AgentLpqMemberState {
    public enum MemberType { AGENT, HUMAN }
    public enum Role {
        EVENT_LEADER, GENERAL, MAGIC_ATTACKER, PHYSICAL_ATTACKER,
        TELEPORT_RUNNER, DARK_SIGHT_RUNNER, RANGED_TRIGGER,
        PLATFORM_HOLDER, PLATFORM_MOVER, BOSS_ATTACKER
    }

    private final int characterId;
    private final MemberType memberType;
    private Role role = Role.GENERAL;
    private int assignedMapId;
    private int assignedPlatform;
    private long nextActionAtMs;

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

    public void assign(Role role, int mapId) {
        if (role == null || mapId < 0) throw new IllegalArgumentException("valid LPQ assignment is required");
        this.role = role;
        this.assignedMapId = mapId;
    }

    public void assignPlatform(int platform) {
        if (platform < 0 || platform > 9) throw new IllegalArgumentException("LPQ platform must be 0-9");
        assignedPlatform = platform;
    }

    public void deferUntil(long nextActionAtMs) { this.nextActionAtMs = Math.max(0L, nextActionAtMs); }
}
