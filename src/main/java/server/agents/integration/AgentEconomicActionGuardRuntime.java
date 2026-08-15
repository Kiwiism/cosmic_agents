package server.agents.integration;

import client.Character;
import client.inventory.InventoryType;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

/** Minimal integration contract between real mutation gateways and the optional economy owner. */
public final class AgentEconomicActionGuardRuntime {
    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();
    private static volatile NpcSaleGuard npcSaleGuard = NpcSaleGuard.allowAll();
    private AgentEconomicActionGuardRuntime() { }

    public static void install(NpcSaleGuard guard) { npcSaleGuard = Objects.requireNonNull(guard); }
    public static void clear() {
        npcSaleGuard = NpcSaleGuard.allowAll();
        AgentEconomyRuntime.clear();
    }

    public static Decision claimNpcSale(Character agent, InventoryType type, short slot,
                                        int itemId, short quantity) {
        Context context = CONTEXT.get();
        return npcSaleGuard.claim(agent, type, slot, itemId, quantity,
                context == null ? "WORLD_NPC" : context.venue,
                context == null ? Instant.now() : context.logicalAt);
    }

    public static <T> T withNpcSaleContext(Instant logicalAt, String venue, Supplier<T> action) {
        if (CONTEXT.get() != null) throw new IllegalStateException("economic action context is already active");
        CONTEXT.set(new Context(Objects.requireNonNull(logicalAt), Objects.requireNonNull(venue)));
        try { return action.get(); }
        finally { CONTEXT.remove(); }
    }

    @FunctionalInterface
    public interface NpcSaleGuard {
        Decision claim(Character agent, InventoryType type, short slot, int itemId, short quantity,
                       String venue, Instant logicalAt);
        static NpcSaleGuard allowAll() {
            return (agent, type, slot, itemId, quantity, venue, at) -> Decision.allowed("NO_ECONOMY_OWNER");
        }
    }

    public record Decision(boolean allowed, String reason) {
        public static Decision allowed(String reason) { return new Decision(true, reason); }
        public static Decision denied(String reason) { return new Decision(false, reason); }
    }
    private record Context(Instant logicalAt, String venue) { }
}
