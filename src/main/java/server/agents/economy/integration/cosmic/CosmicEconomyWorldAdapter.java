package server.agents.economy.integration.cosmic;

import client.Character;
import server.agents.economy.activity.FarmSessionOutcome;
import server.agents.economy.activity.FarmSessionPlan;
import server.agents.economy.persistence.EconomyParticipantBindingStore;
import server.agents.economy.persistence.EconomyBootstrapStore;
import server.agents.economy.scenario.EconomyAgentProfile;
import server.agents.economy.scenario.EconomyWorldPort;
import server.economy.EconomyOperationContext;
import server.economy.EconomyOperationMetadata;
import server.economy.EconomyTaxOverride;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Guarded live adapter; policies cannot bypass physical FM state or Cosmic settlement. */
public final class CosmicEconomyWorldAdapter implements EconomyWorldPort {
    private final UUID runId;
    private final int channelId;
    private final String configRevision;
    private final String catalogRevision;
    private final AgentDirectory agents;
    private final MarketBehavior market;
    private final ActivityPlanner activity;
    private final OffscreenPresence presence;
    private final CosmicFarmSettlementService settlement;
    private final TaxPolicy taxPolicy;
    private final EconomyParticipantBindingStore participantBindings;
    private final EconomyBootstrapStore bootstrapStore;
    private final Map<String, Character> bindings = new ConcurrentHashMap<>();
    private final java.util.Set<String> offscreen = ConcurrentHashMap.newKeySet();

    public CosmicEconomyWorldAdapter(UUID runId, int channelId, String configRevision,
                                     String catalogRevision, AgentDirectory agents,
                                     MarketBehavior market, ActivityPlanner activity,
                                     OffscreenPresence presence, CosmicFarmSettlementService settlement,
                                     TaxPolicy taxPolicy, EconomyParticipantBindingStore participantBindings,
                                     EconomyBootstrapStore bootstrapStore) {
        this.runId = Objects.requireNonNull(runId); this.channelId = channelId;
        this.configRevision = Objects.requireNonNull(configRevision);
        this.catalogRevision = Objects.requireNonNull(catalogRevision);
        this.agents = Objects.requireNonNull(agents); this.market = Objects.requireNonNull(market);
        this.activity = Objects.requireNonNull(activity); this.presence = Objects.requireNonNull(presence);
        this.settlement = Objects.requireNonNull(settlement);
        this.taxPolicy = Objects.requireNonNull(taxPolicy);
        this.participantBindings = Objects.requireNonNull(participantBindings);
        this.bootstrapStore = Objects.requireNonNull(bootstrapStore);
    }

    @Override
    public void admit(EconomyAgentProfile profile, Instant logicalAt) {
        Character agent = agents.resolve(profile.agentId());
        requireLiveFm(agent, true);
        if (bindings.putIfAbsent(profile.agentId(), agent) != null)
            throw new IllegalStateException("economy agent already bound: " + profile.agentId());
        try {
            participantBindings.bind(runId, profile.agentId(), agent.getId(), logicalAt);
            bootstrapStore.recordImported(runId, profile.agentId(), logicalAt, configRevision,
                    catalogRevision, CosmicEconomyBootstrapSnapshot.capture(agent));
        } catch (RuntimeException failure) {
            bindings.remove(profile.agentId(), agent);
            throw failure;
        }
    }

    @Override
    public MarketDirective performMarketCycle(EconomyAgentProfile profile, Instant logicalAt) {
        Character agent = bound(profile.agentId());
        requireLiveFm(agent, false);
        if (offscreen.contains(profile.agentId())) throw new IllegalStateException("offscreen agent cannot trade");
        return EconomyOperationContext.with(metadata(profile, logicalAt, "MARKET_CYCLE", null),
                () -> market.perform(agent, profile, logicalAt));
    }

    @Override
    public FarmSessionPlan planOffscreenActivity(EconomyAgentProfile profile, Instant logicalAt) {
        Character agent = bound(profile.agentId());
        requireLiveFm(agent, false);
        if (agent.getPlayerShop() != null || agent.getHiredMerchant() != null || agent.getTrade() != null)
            throw new IllegalStateException("agent must close shop and trade before offscreen activity");
        return activity.plan(agent, profile, logicalAt);
    }

    @Override
    public void leaveFreeMarket(EconomyAgentProfile profile, FarmSessionPlan plan, Instant logicalAt) {
        Character agent = bound(profile.agentId());
        requireLiveFm(agent, false);
        if (!offscreen.add(profile.agentId())) throw new IllegalStateException("agent is already offscreen");
        try { presence.leaveVisibleFreeMarket(agent, logicalAt); }
        catch (RuntimeException failure) { offscreen.remove(profile.agentId()); throw failure; }
    }

    @Override
    public void settleOffscreenActivity(EconomyAgentProfile profile, FarmSessionOutcome outcome,
                                        Instant logicalAt, LongSupplier deterministicGameplayRandom) {
        if (!offscreen.contains(profile.agentId())) throw new IllegalStateException("agent is still market-visible");
        Character agent = bound(profile.agentId());
        EconomyOperationContext.with(metadata(profile, logicalAt, "FARM_RESULT", outcome.sessionId()),
                () -> settlement.settle(agent, outcome, deterministicGameplayRandom));
    }

    @Override
    public void returnThroughFreeMarketEntrance(EconomyAgentProfile profile, Instant logicalAt) {
        if (!offscreen.remove(profile.agentId())) throw new IllegalStateException("agent is not offscreen");
        Character agent = bound(profile.agentId());
        try {
            presence.enterFreeMarketEntrance(agent, logicalAt);
            if (agent.getMapId() != 910000000)
                throw new IllegalStateException("agent did not return through FM entrance");
        } catch (RuntimeException failure) {
            offscreen.add(profile.agentId());
            throw failure;
        }
    }

    @Override
    public Map<String, Object> snapshotState() {
        return Map.of("schemaVersion", 1, "boundAgentIds", bindings.keySet().stream().sorted().toList(),
                "offscreenAgentIds", offscreen.stream().sorted().toList(),
                "market", market.snapshotState());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void restoreState(Map<String, Object> state) {
        if (state == null || state.isEmpty()) return; // older coordinator checkpoints
        if (((Number) state.get("schemaVersion")).intValue() != 1)
            throw new IllegalStateException("unsupported Cosmic world checkpoint schema");
        if (!bindings.isEmpty() || !offscreen.isEmpty())
            throw new IllegalStateException("Cosmic world state is already initialized");
        for (Object idValue : (java.util.List<Object>) state.get("boundAgentIds")) {
            String id = idValue.toString();
            Character agent = agents.resolve(id);
            if (agent == null || agent.getClient() == null || agent.getClient().getChannel() != channelId)
                throw new IllegalStateException("checkpoint agent is not live on the configured channel: " + id);
            bindings.put(id, agent);
        }
        for (Object idValue : (java.util.List<Object>) state.get("offscreenAgentIds")) {
            String id = idValue.toString();
            if (!bindings.containsKey(id)) throw new IllegalStateException("offscreen checkpoint agent is unbound: " + id);
            offscreen.add(id);
        }
        market.restoreState((Map<String, Object>) state.get("market"));
    }

    private Character bound(String id) {
        Character value = bindings.get(id);
        if (value == null) throw new IllegalStateException("economy agent is not admitted: " + id);
        return value;
    }

    private void requireLiveFm(Character agent, boolean entranceAllowed) {
        if (agent == null || agent.getClient() == null || agent.getClient().getChannel() != channelId)
            throw new IllegalStateException("agent is not live on the configured channel");
        int map = agent.getMapId();
        boolean valid = map >= 910000001 && map <= 910000022 || entranceAllowed && map == 910000000;
        if (!valid) throw new IllegalStateException("agent is outside the configured Free Market venue");
    }

    private EconomyOperationMetadata metadata(EconomyAgentProfile profile, Instant logicalAt,
                                               String reason, String activityId) {
        String decision = runId + ":" + profile.agentId() + ":" + logicalAt + ":" + reason;
        return new EconomyOperationMetadata(runId, logicalAt, decision, activityId, configRevision,
                catalogRevision, reason, true, false, taxPolicy.at(logicalAt));
    }

    @FunctionalInterface public interface AgentDirectory { Character resolve(String logicalAgentId); }
    public interface MarketBehavior {
        MarketDirective perform(Character agent, EconomyAgentProfile profile, Instant logicalAt);
        default Map<String, Object> snapshotState() { return Map.of(); }
        default void restoreState(Map<String, Object> state) {
            if (state != null && !state.isEmpty())
                throw new IllegalStateException("market behavior does not support checkpoint state");
        }
    }
    @FunctionalInterface public interface ActivityPlanner {
        FarmSessionPlan plan(Character agent, EconomyAgentProfile profile, Instant logicalAt);
    }
    public interface OffscreenPresence {
        void leaveVisibleFreeMarket(Character agent, Instant logicalAt);
        void enterFreeMarketEntrance(Character agent, Instant logicalAt);
    }
    @FunctionalInterface public interface TaxPolicy { EconomyTaxOverride at(Instant logicalAt); }
}
