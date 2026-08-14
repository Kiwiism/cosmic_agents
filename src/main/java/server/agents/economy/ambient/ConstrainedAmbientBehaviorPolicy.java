package server.agents.economy.ambient;

import server.agents.economy.scenario.NamedRandomStreams;

import java.util.Optional;

/** Small detachable placeholder inspired by existing town-life behavior. */
public final class ConstrainedAmbientBehaviorPolicy implements AmbientBehaviorPolicy {
    private final int maximumConsecutiveActions;
    private final NamedRandomStreams random;

    public ConstrainedAmbientBehaviorPolicy(int maximumConsecutiveActions, NamedRandomStreams random) {
        if (maximumConsecutiveActions < 0) throw new IllegalArgumentException();
        this.maximumConsecutiveActions = maximumConsecutiveActions;
        this.random = random;
    }

    @Override
    public Optional<AmbientAction> choose(Context context) {
        if (context.negotiating() || context.consecutiveActions() >= maximumConsecutiveActions)
            return Optional.empty();
        var stream = random.stream("ambient.behavior");
        double draw = stream.nextDouble();
        if (context.hasChair() && draw < .20)
            return Optional.of(new AmbientAction(AmbientAction.Type.SIT, null, "owned-chair available"));
        if (!context.ownsOpenStall() && draw < .35)
            return Optional.of(new AmbientAction(AmbientAction.Type.SHORT_WALK, null, "local fidget"));
        if (draw < .55) return Optional.of(new AmbientAction(AmbientAction.Type.FIDGET, null, "idle variety"));
        return Optional.empty();
    }
}
