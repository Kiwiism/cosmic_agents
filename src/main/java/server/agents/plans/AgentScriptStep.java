package server.agents.plans;

import java.util.function.Consumer;
import java.util.function.Predicate;

public final class AgentScriptStep {
    private final Consumer<AgentScriptContext> enter;
    private final Consumer<AgentScriptContext> tick;
    private final Predicate<AgentScriptContext> complete;

    private AgentScriptStep(Consumer<AgentScriptContext> enter,
                            Consumer<AgentScriptContext> tick,
                            Predicate<AgentScriptContext> complete) {
        this.enter = enter;
        this.tick = tick;
        this.complete = complete;
    }

    public static AgentScriptStep of(Consumer<AgentScriptContext> enter,
                                     Consumer<AgentScriptContext> tick,
                                     Predicate<AgentScriptContext> complete) {
        return new AgentScriptStep(enter, tick, complete);
    }

    public static AgentScriptStep action(Consumer<AgentScriptContext> action) {
        return new AgentScriptStep(action, null, ctx -> true);
    }

    public static AgentScriptStep waitFor(long ms) {
        return new AgentScriptStep(ctx -> ctx.waitMs(ms), null, AgentScriptContext::waitDone);
    }

    public void enter(AgentScriptContext ctx) {
        if (enter != null) {
            enter.accept(ctx);
        }
    }

    public void tick(AgentScriptContext ctx) {
        if (tick != null) {
            tick.accept(ctx);
        }
    }

    public boolean complete(AgentScriptContext ctx) {
        return complete == null || complete.test(ctx);
    }
}
