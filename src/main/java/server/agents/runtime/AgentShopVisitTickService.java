package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotShopStateRuntime;
import server.bots.BotEntry;

import java.awt.Point;

public final class AgentShopVisitTickService {
    private AgentShopVisitTickService() {
    }

    public record Result(boolean consumedTick, Point targetPos) {
    }

    public record Hooks(ShopVisitTick shopVisitTick,
                        MovementCore movementCore) {
    }

    @FunctionalInterface
    public interface ShopVisitTick {
        boolean tick(BotEntry entry, Character agent);
    }

    @FunctionalInterface
    public interface MovementCore {
        void step(BotEntry entry, Point targetPosition, boolean runAiTick);
    }

    public static Result tickShopVisitIfPending(BotEntry entry,
                                                Character agent,
                                                boolean runAiTick,
                                                Hooks hooks) {
        if (!AgentBotShopStateRuntime.shopVisitPending(entry)) {
            return new Result(false, null);
        }

        boolean consumed = hooks.shopVisitTick().tick(entry, agent);
        Point targetPos = AgentBotShopStateRuntime.activeShopTargetPosition(entry);
        if (!consumed && AgentBotShopStateRuntime.shopApproachDelayMs(entry) > 0) {
            return new Result(true, targetPos);
        }
        if (targetPos != null) {
            hooks.movementCore().step(entry, targetPos, runAiTick);
        }
        return new Result(true, targetPos);
    }
}
