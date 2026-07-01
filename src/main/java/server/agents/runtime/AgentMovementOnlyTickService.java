package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;

import java.awt.Point;

/**
 * Agent-owned movement-only tick shell used by tests and movement simulations.
 */
public final class AgentMovementOnlyTickService {
    @FunctionalInterface
    public interface IdleTick {
        boolean tick(BotEntry entry, Character agent);
    }

    @FunctionalInterface
    public interface FollowMapSync {
        boolean sync(BotEntry entry, Character agent, Character leader);
    }

    @FunctionalInterface
    public interface FollowAnchorResolver {
        Character resolve(BotEntry entry, Character leader);
    }

    @FunctionalInterface
    public interface RecoveryTick {
        boolean recover(BotEntry entry, Character agent, Character anchor);
    }

    @FunctionalInterface
    public interface TargetRecoveryTick {
        boolean recover(BotEntry entry, Character agent, Point targetPosition);
    }

    @FunctionalInterface
    public interface AgentTick {
        boolean tick(BotEntry entry, Character agent);
    }

    @FunctionalInterface
    public interface AgentPointSupplier {
        Point get(BotEntry entry);
    }

    @FunctionalInterface
    public interface ShopDelaySupplier {
        int get(BotEntry entry);
    }

    @FunctionalInterface
    public interface FollowIdleFastPath {
        boolean tryFastPath(BotEntry entry, Character agent, Point targetPosition, long nowMs);
    }

    @FunctionalInterface
    public interface MovementCore {
        void step(BotEntry entry, Point targetPosition, boolean runAiTick);
    }

    public record MovementOnlyHooks(IdleTick idleTick,
                                    AgentTick shopVisitPending,
                                    FollowMapSync followMapSync,
                                    FollowAnchorResolver followAnchorResolver,
                                    RecoveryTick partyRecovery,
                                    TargetRecoveryTick targetRecovery,
                                    AgentTick mapChangeHandler,
                                    AgentTick shopVisitTick,
                                    AgentPointSupplier activeShopTargetPosition,
                                    ShopDelaySupplier shopApproachDelayMs,
                                    FollowIdleFastPath followIdleFastPath,
                                    MovementCore movementCore) {
    }

    private AgentMovementOnlyTickService() {
    }

    public static void stepMovementOnly(BotEntry entry,
                                        Point targetPosition,
                                        boolean runAiTick,
                                        long nowMs,
                                        MovementOnlyHooks hooks) {
        if (!AgentBotRuntimeIdentityRuntime.hasBot(entry) || targetPosition == null) {
            return;
        }

        Character agent = AgentBotRuntimeIdentityRuntime.bot(entry);
        Character leader = AgentBotRuntimeIdentityRuntime.owner(entry);

        if (hooks.idleTick().tick(entry, agent)) {
            return;
        }

        if (leader != null && !hooks.shopVisitPending().tick(entry, agent)
                && hooks.followMapSync().sync(entry, agent, leader)) {
            return;
        }

        Character followAnchor = hooks.followAnchorResolver().resolve(entry, leader);
        if (hooks.partyRecovery().recover(entry, agent, followAnchor)) {
            return;
        }
        if (hooks.targetRecovery().recover(entry, agent, targetPosition)) {
            return;
        }

        if (hooks.mapChangeHandler().tick(entry, agent)) {
            return;
        }

        if (hooks.shopVisitPending().tick(entry, agent)) {
            boolean consumed = hooks.shopVisitTick().tick(entry, agent);
            Point activeShopTarget = hooks.activeShopTargetPosition().get(entry);
            if (!consumed && hooks.shopApproachDelayMs().get(entry) > 0) {
                return;
            }
            if (activeShopTarget != null) {
                hooks.movementCore().step(entry, activeShopTarget, runAiTick);
            }
            return;
        }

        if (hooks.followIdleFastPath().tryFastPath(entry, agent, targetPosition, nowMs)) {
            return;
        }

        hooks.movementCore().step(entry, targetPosition, runAiTick);
    }
}
