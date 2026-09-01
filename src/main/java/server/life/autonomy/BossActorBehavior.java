package server.life.autonomy;

import client.Character;
import server.life.Monster;

import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** Mob-specific selection policy; execution and scheduling remain generic. */
public interface BossActorBehavior {
    int mobId();

    Optional<SelectedAction> select(
            Monster monster,
            List<Character> targets,
            ServerMobActionCatalog.MonsterActions actions,
            RandomGenerator random);

    default boolean autoStartOnSpawn() {
        return false;
    }

    /** Whether this encounter must remain server-owned even with a capable human client present. */
    default boolean forceServerAuthority() {
        return false;
    }

    /** Whether an ordinary unsupported summon may be promoted to generic server combat. */
    default boolean allowServerTakeoverForOrdinarySummons() {
        return true;
    }

    /** Whether this actor may attack only the Agent that currently owns its aggro. */
    default boolean usesPrimaryAggroTargetOnly() {
        return false;
    }

    /** Whether sticky-server combat should share the Agent-owned roaming physics loop. */
    default boolean usesServerMobPhysics() {
        return false;
    }

    record SelectedAction(BossAction action, Character primaryTarget,
                          Boolean facingLeftOverride) {
        public SelectedAction(BossAction action, Character primaryTarget) {
            this(action, primaryTarget, null);
        }
    }
}
