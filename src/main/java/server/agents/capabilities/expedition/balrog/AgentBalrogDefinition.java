package server.agents.capabilities.expedition.balrog;

import server.agents.field.AgentBalrogTestFixtureService;

import java.util.Set;

/** Authoritative IDs and live-test limits for the Easy Balrog expedition. */
public final class AgentBalrogDefinition {
    public static final int RECRUIT_MAP = 105100100;
    public static final int BATTLE_MAP = 105100400;
    public static final int CLEAR_MAP = 105100401;
    public static final int ENTRY_NPC = 1061014;
    public static final int PARTY_SIZE = 6;
    public static final int LEVEL = AgentBalrogTestFixtureService.LEVEL;
    public static final int BODY_MOB = 8830007;
    public static final int RELEASE_SEAL_MOB = 8830013;
    public static final Set<Integer> CLAW_MOBS = Set.of(8830008, 8830009);
    public static final Set<Integer> COMBAT_MOBS = AgentBalrogTestFixtureService.BALROG_COMBAT_MOBS;

    private AgentBalrogDefinition() {
    }
}
