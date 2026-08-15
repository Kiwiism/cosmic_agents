package server.agents.simulation.activity;

import client.Character;
import server.agents.economy.activity.FarmSessionOutcome;
import server.agents.economy.activity.FarmSessionPlan;
import server.agents.economy.activity.RuleExactFarmResolver;
import server.agents.economy.integration.cosmic.EconomyParticipantRegistry;
import server.agents.economy.scenario.EconomyAgentProfile;
import server.agents.economy.scenario.NamedRandomStreams;
import server.economy.EconomyOperationContext;
import server.economy.EconomyOperationMetadata;
import server.economy.EconomyTaxOverride;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Cosmic implementation of activity ownership outside the economy session.
 * The economy receives only the resulting inventory/meso/level state on a later fresh admission.
 */
public final class CosmicExternalAgentActivityAdapter implements ExternalAgentActivityPort {
    private final UUID runId;
    private final String configRevision;
    private final String catalogRevision;
    private final EconomyParticipantRegistry participants;
    private final Planner planner;
    private final RuleExactFarmResolver resolver;
    private final Presence presence;
    private final Settlement settlement;
    private final Function<Instant, EconomyTaxOverride> taxes;
    private final Set<String> offscreen = ConcurrentHashMap.newKeySet();

    public CosmicExternalAgentActivityAdapter(UUID runId, String configRevision, String catalogRevision,
                                              EconomyParticipantRegistry participants, Planner planner,
                                              RuleExactFarmResolver resolver, Presence presence,
                                              Settlement settlement,
                                              Function<Instant, EconomyTaxOverride> taxes) {
        this.runId = Objects.requireNonNull(runId); this.configRevision = Objects.requireNonNull(configRevision);
        this.catalogRevision = Objects.requireNonNull(catalogRevision);
        this.participants = Objects.requireNonNull(participants); this.planner = Objects.requireNonNull(planner);
        this.resolver = Objects.requireNonNull(resolver); this.presence = Objects.requireNonNull(presence);
        this.settlement = Objects.requireNonNull(settlement); this.taxes = Objects.requireNonNull(taxes);
    }

    @Override public FarmSessionPlan plan(EconomyAgentProfile profile, Instant at) {
        Character agent = bound(profile.agentId());
        requireFreeMarket(agent);
        if (participants.byLogicalId(profile.agentId()).isPresent())
            throw new IllegalStateException("economy session must release before external activity planning");
        if (agent.getPlayerShop() != null || agent.getHiredMerchant() != null || agent.getTrade() != null)
            throw new IllegalStateException("active commerce must drain before external activity");
        return planner.plan(agent, profile, at);
    }

    @Override public FarmSessionOutcome resolve(FarmSessionPlan plan, NamedRandomStreams random) {
        return resolver.resolve(plan, random);
    }

    @Override public void begin(EconomyAgentProfile profile, FarmSessionPlan plan, Instant at) {
        Character agent = bound(profile.agentId());
        requireFreeMarket(agent);
        if (!offscreen.add(profile.agentId()))
            throw new IllegalStateException("external activity already owns agent " + profile.agentId());
        try { presence.leave(agent, at); }
        catch (RuntimeException failure) { offscreen.remove(profile.agentId()); throw failure; }
    }

    @Override public FarmSessionOutcome settle(EconomyAgentProfile profile, FarmSessionOutcome outcome,
                                               Instant at, LongSupplier random) {
        if (!offscreen.contains(profile.agentId()))
            throw new IllegalStateException("external activity does not own agent " + profile.agentId());
        return EconomyOperationContext.with(metadata(profile, at, outcome.sessionId()),
                () -> settlement.settle(bound(profile.agentId()), outcome, random));
    }

    @Override public void returnToEconomyEntrance(EconomyAgentProfile profile, Instant at) {
        if (!offscreen.remove(profile.agentId()))
            throw new IllegalStateException("external activity does not own agent " + profile.agentId());
        Character agent = bound(profile.agentId());
        try {
            presence.enterEconomyEntrance(agent, at);
            if (agent.getMapId() != 910000000)
                throw new IllegalStateException("agent did not return through the FM entrance");
        } catch (RuntimeException failure) {
            offscreen.add(profile.agentId());
            throw failure;
        }
    }

    @Override public Map<String, Object> snapshotState() {
        return Map.of("schemaVersion", 1, "offscreenAgentIds", offscreen.stream().sorted().toList());
    }

    @Override public void restoreState(Map<String, Object> state, Map<String, EconomyAgentProfile> profiles) {
        if (state == null || state.isEmpty()) return;
        if (((Number) state.get("schemaVersion")).intValue() != 1)
            throw new IllegalStateException("unsupported external activity checkpoint schema");
        Object values = state.get("offscreenAgentIds");
        if (!(values instanceof java.util.List<?> ids)) return;
        for (Object value : ids) {
            String id = value.toString();
            if (!profiles.containsKey(id) || participants.boundCharacter(id) == null)
                throw new IllegalStateException("external activity checkpoint agent is not bound: " + id);
            offscreen.add(id); presence.restoreDetached(bound(id));
        }
    }

    private Character bound(String agentId) {
        Character value = participants.boundCharacter(agentId);
        if (value == null) throw new IllegalStateException("agent is not durably bound: " + agentId);
        return value;
    }

    private static void requireFreeMarket(Character agent) {
        int map = agent.getMapId();
        if (map < 910000000 || map > 910000022)
            throw new IllegalStateException("agent must leave from the configured Free Market venue");
    }

    private EconomyOperationMetadata metadata(EconomyAgentProfile profile, Instant at, String activityId) {
        String decision = runId + ":" + profile.agentId() + ':' + at + ":EXTERNAL_ACTIVITY";
        return new EconomyOperationMetadata(runId, at, decision, activityId, configRevision,
                catalogRevision, "EXTERNAL_ACTIVITY_RESULT", true, false, taxes.apply(at));
    }

    @FunctionalInterface public interface Planner {
        FarmSessionPlan plan(Character agent, EconomyAgentProfile profile, Instant at);
    }
    public interface Presence {
        void leave(Character agent, Instant at);
        void enterEconomyEntrance(Character agent, Instant at);
        default void restoreDetached(Character agent) { }
    }
    @FunctionalInterface public interface Settlement {
        FarmSessionOutcome settle(Character agent, FarmSessionOutcome outcome, LongSupplier random);
    }
}
