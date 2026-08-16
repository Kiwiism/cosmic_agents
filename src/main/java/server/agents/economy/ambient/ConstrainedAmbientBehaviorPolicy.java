package server.agents.economy.ambient;

import server.agents.economy.scenario.NamedRandomStreams;
import server.agents.economy.scenario.EconomyEngineConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Small detachable placeholder inspired by existing town-life behavior. */
public final class ConstrainedAmbientBehaviorPolicy implements AmbientBehaviorPolicy {
    private final int maximumConsecutiveActions;
    private final NamedRandomStreams random;
    private final Map<String, EconomyEngineConfig.AmbientModule> modules;

    public ConstrainedAmbientBehaviorPolicy(int maximumConsecutiveActions, NamedRandomStreams random) {
        this(maximumConsecutiveActions, random, defaultModules());
    }

    public ConstrainedAmbientBehaviorPolicy(int maximumConsecutiveActions, NamedRandomStreams random,
                                            Map<String, EconomyEngineConfig.AmbientModule> modules) {
        if (maximumConsecutiveActions < 0) throw new IllegalArgumentException();
        this.maximumConsecutiveActions = maximumConsecutiveActions;
        this.random = random;
        this.modules = Map.copyOf(modules);
    }

    @Override
    public Optional<AmbientAction> choose(Context context) {
        if (context.negotiating() || context.consecutiveActions() >= maximumConsecutiveActions)
            return Optional.empty();
        List<WeightedAction> choices = new ArrayList<>();
        add(choices, "idle", AmbientAction.Type.IDLE, "configured idle");
        add(choices, "fidget", AmbientAction.Type.FIDGET, "configured fidget");
        if (!context.ownsOpenStall()) add(choices, "walk", AmbientAction.Type.SHORT_WALK, "configured local walk");
        if (context.seated()) add(choices, "sit", AmbientAction.Type.STAND, "end owned-chair rest");
        else if (context.hasChair()) add(choices, "sit", AmbientAction.Type.SIT, "owned-chair available");
        int total = choices.stream().mapToInt(WeightedAction::weight).sum();
        if (total <= 0) return Optional.empty();
        int draw = random.stream("ambient.behavior." + context.agentId()).nextInt(total);
        for (WeightedAction choice : choices) {
            if (draw < choice.weight()) return Optional.of(choice.action());
            draw -= choice.weight();
        }
        return Optional.empty();
    }

    private void add(List<WeightedAction> choices, String moduleName, AmbientAction.Type type, String reason) {
        EconomyEngineConfig.AmbientModule module = modules.get(moduleName);
        if (module != null && module.enabled && module.weight > 0)
            choices.add(new WeightedAction(module.weight, new AmbientAction(type, null, reason)));
    }

    private static Map<String, EconomyEngineConfig.AmbientModule> defaultModules() {
        return Map.of("idle", module(30), "walk", module(20), "sit", module(15), "fidget", module(25));
    }
    private static EconomyEngineConfig.AmbientModule module(int weight) {
        EconomyEngineConfig.AmbientModule value = new EconomyEngineConfig.AmbientModule();
        value.enabled = true; value.weight = weight; return value;
    }
    private record WeightedAction(int weight, AmbientAction action) { }
}
