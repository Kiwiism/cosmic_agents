package server.bots;

import java.util.function.Consumer;
import java.util.function.Predicate;

public final class BotScriptStep {
    private final Consumer<BotScriptContext> enter;
    private final Consumer<BotScriptContext> tick;
    private final Predicate<BotScriptContext> complete;

    private BotScriptStep(Consumer<BotScriptContext> enter,
                          Consumer<BotScriptContext> tick,
                          Predicate<BotScriptContext> complete) {
        this.enter = enter;
        this.tick = tick;
        this.complete = complete;
    }

    public static BotScriptStep of(Consumer<BotScriptContext> enter,
                                   Consumer<BotScriptContext> tick,
                                   Predicate<BotScriptContext> complete) {
        return new BotScriptStep(enter, tick, complete);
    }

    public static BotScriptStep action(Consumer<BotScriptContext> action) {
        return new BotScriptStep(action, null, ctx -> true);
    }

    public static BotScriptStep waitFor(long ms) {
        return new BotScriptStep(ctx -> ctx.waitMs(ms), null, BotScriptContext::waitDone);
    }

    void enter(BotScriptContext ctx) {
        if (enter != null) {
            enter.accept(ctx);
        }
    }

    void tick(BotScriptContext ctx) {
        if (tick != null) {
            tick.accept(ctx);
        }
    }

    boolean complete(BotScriptContext ctx) {
        return complete == null || complete.test(ctx);
    }
}
