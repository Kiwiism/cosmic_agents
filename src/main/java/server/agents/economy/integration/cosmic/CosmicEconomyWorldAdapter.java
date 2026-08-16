package server.agents.economy.integration.cosmic;

import client.Character;
import server.agents.economy.activity.FarmSessionOutcome;
import server.agents.economy.activity.FarmSessionPlan;
import server.agents.economy.activity.EconomyJobFamily;
import server.agents.economy.persistence.EconomyParticipantBindingStore;
import server.agents.economy.persistence.EconomyBootstrapStore;
import server.agents.economy.scenario.EconomyAgentProfile;
import server.agents.economy.scenario.EconomyWorldPort;
import server.agents.economy.session.EconomySessionPort;
import server.economy.EconomyOperationContext;
import server.economy.EconomyOperationMetadata;
import server.economy.EconomyTaxOverride;
import server.agents.runtime.AgentCommerceControlRuntime;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Guarded live adapter; policies cannot bypass physical FM state or Cosmic settlement. */
public final class CosmicEconomyWorldAdapter implements EconomyWorldPort, EconomySessionPort {
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
    private final AdmissionObserver admissionObserver;
    private final ReleaseObserver releaseObserver;
    private final Map<String, Character> bindings = new ConcurrentHashMap<>();
    private final java.util.Set<String> offscreen = ConcurrentHashMap.newKeySet();
    private final Map<String, SessionRecord> activeSessions = new ConcurrentHashMap<>();

    /** Primary production constructor. External activities are composed through their own port. */
    public CosmicEconomyWorldAdapter(UUID runId, int channelId, String configRevision,
                                     String catalogRevision, AgentDirectory agents,
                                     MarketBehavior market, TaxPolicy taxPolicy,
                                     EconomyParticipantBindingStore participantBindings,
                                     EconomyBootstrapStore bootstrapStore,
                                     AdmissionObserver admissionObserver,
                                     ReleaseObserver releaseObserver) {
        this.runId = Objects.requireNonNull(runId); this.channelId = channelId;
        this.configRevision = Objects.requireNonNull(configRevision);
        this.catalogRevision = Objects.requireNonNull(catalogRevision);
        this.agents = Objects.requireNonNull(agents); this.market = Objects.requireNonNull(market);
        this.activity = null; this.presence = null; this.settlement = null;
        this.taxPolicy = Objects.requireNonNull(taxPolicy);
        this.participantBindings = Objects.requireNonNull(participantBindings);
        this.bootstrapStore = Objects.requireNonNull(bootstrapStore);
        this.admissionObserver = Objects.requireNonNull(admissionObserver);
        this.releaseObserver = Objects.requireNonNull(releaseObserver);
    }

    public CosmicEconomyWorldAdapter(UUID runId, int channelId, String configRevision,
                                     String catalogRevision, AgentDirectory agents,
                                     MarketBehavior market, ActivityPlanner activity,
                                     OffscreenPresence presence, CosmicFarmSettlementService settlement,
                                     TaxPolicy taxPolicy, EconomyParticipantBindingStore participantBindings,
                                     EconomyBootstrapStore bootstrapStore) {
        this(runId, channelId, configRevision, catalogRevision, agents, market, activity, presence,
                settlement, taxPolicy, participantBindings, bootstrapStore,
                (profile, character) -> { }, (profile, character) -> { });
    }

    public CosmicEconomyWorldAdapter(UUID runId, int channelId, String configRevision,
                                     String catalogRevision, AgentDirectory agents,
                                     MarketBehavior market, ActivityPlanner activity,
                                     OffscreenPresence presence, CosmicFarmSettlementService settlement,
                                     TaxPolicy taxPolicy, EconomyParticipantBindingStore participantBindings,
                                     EconomyBootstrapStore bootstrapStore, AdmissionObserver admissionObserver) {
        this(runId, channelId, configRevision, catalogRevision, agents, market, activity, presence,
                settlement, taxPolicy, participantBindings, bootstrapStore, admissionObserver,
                (profile, character) -> { });
    }

    public CosmicEconomyWorldAdapter(UUID runId, int channelId, String configRevision,
                                     String catalogRevision, AgentDirectory agents,
                                     MarketBehavior market, ActivityPlanner activity,
                                     OffscreenPresence presence, CosmicFarmSettlementService settlement,
                                     TaxPolicy taxPolicy, EconomyParticipantBindingStore participantBindings,
                                     EconomyBootstrapStore bootstrapStore, AdmissionObserver admissionObserver,
                                     ReleaseObserver releaseObserver) {
        this.runId = Objects.requireNonNull(runId); this.channelId = channelId;
        this.configRevision = Objects.requireNonNull(configRevision);
        this.catalogRevision = Objects.requireNonNull(catalogRevision);
        this.agents = Objects.requireNonNull(agents); this.market = Objects.requireNonNull(market);
        this.activity = Objects.requireNonNull(activity); this.presence = Objects.requireNonNull(presence);
        this.settlement = Objects.requireNonNull(settlement);
        this.taxPolicy = Objects.requireNonNull(taxPolicy);
        this.participantBindings = Objects.requireNonNull(participantBindings);
        this.bootstrapStore = Objects.requireNonNull(bootstrapStore);
        this.admissionObserver = Objects.requireNonNull(admissionObserver);
        this.releaseObserver = Objects.requireNonNull(releaseObserver);
    }

    @Override
    public synchronized EntryResult requestEntry(EconomyAgentProfile profile, EntryRequest request,
                                                 Instant logicalAt) {
        Objects.requireNonNull(profile); Objects.requireNonNull(request); Objects.requireNonNull(logicalAt);
        SessionRecord active = activeSessions.get(profile.agentId());
        if (active != null) return EntryResult.accepted(active.sessionId(), active.expiresAt(),
                "IDEMPOTENT_ACTIVE_SESSION");
        Character agent = agents.resolve(profile.agentId());
        if (agent == null || agent.getClient() == null || agent.getClient().getChannel() != channelId)
            return EntryResult.deferred("AGENT_NOT_LIVE_ON_CONFIGURED_CHANNEL", logicalAt.plusSeconds(5));
        if (!profile.jobFamily().equals(EconomyJobFamily.of(agent)))
            return EntryResult.rejected("PROFILE_JOB_MISMATCH");
        if (offscreen.contains(profile.agentId()))
            return EntryResult.deferred("EXTERNAL_ACTIVITY_STILL_OWNS_AGENT", logicalAt.plusSeconds(5));
        int map = agent.getMapId();
        if (map != 910000000 && (map < 910000001 || map > 910000022))
            return EntryResult.deferred("AGENT_HAS_NOT_REACHED_FREE_MARKET", logicalAt.plusSeconds(5));
        admit(profile, logicalAt);
        UUID sessionId = UUID.nameUUIDFromBytes((runId + ":" + profile.agentId() + ":"
                + request.requestId()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Instant expiresAt = logicalAt.plus(request.maximumDuration());
        SessionRecord created = new SessionRecord(sessionId, request.requestId(), logicalAt,
                expiresAt, request.maximumIdleDuration(), market.progressRevision(profile.agentId()), logicalAt);
        SessionRecord previous = activeSessions.putIfAbsent(profile.agentId(), created);
        SessionRecord result = previous == null ? created : previous;
        return EntryResult.accepted(result.sessionId(), result.expiresAt(),
                previous == null ? "ACCEPTED" : "IDEMPOTENT_ACTIVE_SESSION");
    }

    @Override
    public synchronized Directive performMarketCycle(UUID sessionId, EconomyAgentProfile profile,
                                                     Instant logicalAt) {
        SessionRecord active = activeSessions.get(profile.agentId());
        if (active == null || !active.sessionId().equals(sessionId))
            throw new IllegalStateException("economy session is not active for " + profile.agentId());
        Character agent = bound(profile.agentId());
        long currentRevision = market.progressRevision(profile.agentId());
        if (currentRevision != active.lastProgressRevision) {
            active.lastProgressRevision = currentRevision;
            active.lastProgressAt = logicalAt;
        }
        boolean idleExpired = !active.maximumIdleDuration.isZero()
                && agent.getPlayerShop() == null
                && !logicalAt.isBefore(active.lastProgressAt.plus(active.maximumIdleDuration));
        boolean maximumExpired = !logicalAt.isBefore(active.expiresAt());
        MarketDirective legacy = maximumExpired || idleExpired
                ? EconomyOperationContext.with(metadata(profile, logicalAt, "SESSION_DEADLINE", null),
                () -> market.drainForRelease(agent, profile, logicalAt))
                : performMarketCycle(profile, logicalAt);
        long afterRevision = market.progressRevision(profile.agentId());
        if (afterRevision != active.lastProgressRevision) {
            active.lastProgressRevision = afterRevision;
            active.lastProgressAt = logicalAt;
        }
        if (legacy.startActivityAt().isPresent())
            return Directive.release(legacy.startActivityAt().orElseThrow(), idleExpired
                    ? "SESSION_IDLE_TIMEOUT" : maximumExpired
                    ? "SESSION_MAXIMUM_DURATION" : "MARKET_GOALS_COMPLETE");
        Instant revisit = legacy.revisitMarketAt().orElseGet(() -> logicalAt.plusSeconds(1));
        return Directive.revisit(revisit, legacy.externalActionPending(), "ECONOMIC_WORK_REMAINS");
    }

    @Override
    public synchronized ReleaseResult release(UUID sessionId, EconomyAgentProfile profile,
                                              Instant logicalAt, String reason) {
        SessionRecord active = activeSessions.get(profile.agentId());
        if (active == null) return ReleaseResult.released("IDEMPOTENT_ALREADY_RELEASED");
        if (!active.sessionId().equals(sessionId)) return ReleaseResult.rejected("SESSION_ID_MISMATCH");
        Character agent = bound(profile.agentId());
        if (agent.getPlayerShop() != null || agent.getHiredMerchant() != null || agent.getTrade() != null)
            return ReleaseResult.deferred("ACTIVE_COMMERCE_MUST_DRAIN", logicalAt.plusSeconds(1));
        if (!activeSessions.remove(profile.agentId(), active))
            return ReleaseResult.deferred("CONCURRENT_SESSION_TRANSITION", logicalAt.plusSeconds(1));
        releaseObserver.released(profile, agent);
        return ReleaseResult.released(reason == null || reason.isBlank() ? "RELEASED" : reason);
    }

    @Override
    public void admit(EconomyAgentProfile profile, Instant logicalAt) {
        Character agent = agents.resolve(profile.agentId());
        requireLiveFm(agent, true);
        if (!profile.jobFamily().equals(EconomyJobFamily.of(agent)))
            throw new IllegalStateException("economy profile job does not match bound Cosmic character: profile="
                    + profile.jobFamily() + " character=" + EconomyJobFamily.of(agent));
        Character existing = bindings.putIfAbsent(profile.agentId(), agent);
        if (existing != null && existing.getId() != agent.getId())
            throw new IllegalStateException("economy agent already bound to a different character: "
                    + profile.agentId());
        try {
            participantBindings.bind(runId, profile.agentId(), agent.getId(), logicalAt);
            bootstrapStore.recordImported(runId, profile.agentId(), logicalAt, configRevision,
                    catalogRevision, CosmicEconomyBootstrapSnapshot.capture(agent));
            admissionObserver.admitted(profile, agent);
        } catch (RuntimeException failure) {
            if (existing == null) bindings.remove(profile.agentId(), agent);
            throw failure;
        }
    }

    @Override
    public MarketDirective performMarketCycle(EconomyAgentProfile profile, Instant logicalAt) {
        Character agent = bound(profile.agentId());
        // The entrance is part of the configured venue. A newly admitted or
        // returning agent must be allowed to begin the physical room-browsing flow here.
        requireLiveFm(agent, true);
        if (offscreen.contains(profile.agentId())) throw new IllegalStateException("offscreen agent cannot trade");
        EconomyOperationMetadata metadata = metadata(profile, logicalAt, "MARKET_CYCLE", null);
        if (AgentCommerceControlRuntime.claimed(agent.getId()))
            AgentCommerceControlRuntime.attribute(agent.getId(), metadata);
        return EconomyOperationContext.with(metadata,
                () -> market.perform(agent, profile, logicalAt));
    }

    @Override
    public FarmSessionPlan planOffscreenActivity(EconomyAgentProfile profile, Instant logicalAt) {
        requireLegacyActivityComposition();
        Character agent = bound(profile.agentId());
        requireLiveFm(agent, false);
        if (agent.getPlayerShop() != null || agent.getHiredMerchant() != null || agent.getTrade() != null)
            throw new IllegalStateException("agent must close shop and trade before offscreen activity");
        return activity.plan(agent, profile, logicalAt);
    }

    @Override
    public void leaveFreeMarket(EconomyAgentProfile profile, FarmSessionPlan plan, Instant logicalAt) {
        requireLegacyActivityComposition();
        Character agent = bound(profile.agentId());
        requireLiveFm(agent, false);
        if (!offscreen.add(profile.agentId())) throw new IllegalStateException("agent is already offscreen");
        try { presence.leaveVisibleFreeMarket(agent, logicalAt); }
        catch (RuntimeException failure) { offscreen.remove(profile.agentId()); throw failure; }
    }

    @Override
    public FarmSessionOutcome settleOffscreenActivity(EconomyAgentProfile profile, FarmSessionOutcome outcome,
                                                      Instant logicalAt, LongSupplier deterministicGameplayRandom) {
        requireLegacyActivityComposition();
        if (!offscreen.contains(profile.agentId())) throw new IllegalStateException("agent is still market-visible");
        Character agent = bound(profile.agentId());
        return EconomyOperationContext.with(metadata(profile, logicalAt, "FARM_RESULT", outcome.sessionId()),
                () -> settlement.settle(agent, outcome, deterministicGameplayRandom));
    }

    @Override
    public void returnThroughFreeMarketEntrance(EconomyAgentProfile profile, Instant logicalAt) {
        requireLegacyActivityComposition();
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
                "activeSessions", activeSessions.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, entry -> sessionMap(entry.getValue()))),
                "market", market.snapshotState());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void restoreState(Map<String, Object> state) {
        restoreState(state, Map.of());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void restoreState(Map<String, Object> state, Map<String, EconomyAgentProfile> profiles) {
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
            EconomyAgentProfile profile = profiles.get(id);
            if (profile == null)
                throw new IllegalStateException("checkpoint profile is missing for bound agent: " + id);
            if (!profile.jobFamily().equals(EconomyJobFamily.of(agent)))
                throw new IllegalStateException("checkpoint profile job does not match live character: " + id);
            admissionObserver.admitted(profile, agent);
        }
        for (Object idValue : (java.util.List<Object>) state.get("offscreenAgentIds")) {
            String id = idValue.toString();
            if (!bindings.containsKey(id)) throw new IllegalStateException("offscreen checkpoint agent is unbound: " + id);
            offscreen.add(id);
            presence.restoreDetached(bindings.get(id));
            EconomyAgentProfile profile = profiles.get(id);
            if (profile != null) releaseObserver.released(profile, bindings.get(id));
        }
        Object encodedSessions = state.get("activeSessions");
        if (encodedSessions instanceof Map<?, ?> values) values.forEach((agentId, encoded) -> {
            @SuppressWarnings("unchecked") Map<String, Object> value = (Map<String, Object>) encoded;
            activeSessions.put(agentId.toString(), sessionFrom(value));
        });
        else bindings.keySet().stream().filter(id -> !offscreen.contains(id)).forEach(id -> {
            UUID sessionId = UUID.nameUUIDFromBytes((runId + ":" + id + ":restored-session")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            activeSessions.put(id, new SessionRecord(sessionId, sessionId, Instant.EPOCH,
                    Instant.MAX, java.time.Duration.ZERO, market.progressRevision(id), Instant.EPOCH));
        });
        bindings.forEach((id, character) -> {
            if (!activeSessions.containsKey(id)) {
                EconomyAgentProfile profile = profiles.get(id);
                if (profile != null) releaseObserver.released(profile, character);
            }
        });
        market.restoreState((Map<String, Object>) state.get("market"));
    }

    @Override
    public Optional<EconomyWorldPort.Presence> currentPresence(EconomyAgentProfile profile) {
        Character agent = bindings.get(profile.agentId());
        if (agent == null) return Optional.empty();
        java.awt.Point position = agent.getPosition();
        return Optional.of(new EconomyWorldPort.Presence(agent.getMapId(), position.x, position.y,
                !offscreen.contains(profile.agentId())));
    }

    @Override
    public Optional<EconomySessionPort.Presence> sessionPresence(EconomyAgentProfile profile) {
        Character agent = bindings.get(profile.agentId());
        if (agent == null) return Optional.empty();
        java.awt.Point position = agent.getPosition();
        return Optional.of(new EconomySessionPort.Presence(agent.getMapId(), position.x, position.y,
                !offscreen.contains(profile.agentId())));
    }

    private Character bound(String id) {
        Character value = bindings.get(id);
        if (value == null) throw new IllegalStateException("economy agent is not admitted: " + id);
        return value;
    }

    private void requireLegacyActivityComposition() {
        if (activity == null || presence == null || settlement == null)
            throw new UnsupportedOperationException(
                    "external activity is owned by ExternalAgentActivityPort in production");
    }

    private void requireLiveFm(Character agent, boolean entranceAllowed) {
        if (agent == null || agent.getClient() == null || agent.getClient().getChannel() != channelId)
            throw new IllegalStateException("agent is not live on the configured channel");
        int map = agent.getMapId();
        boolean valid = map >= 910000001 && map <= 910000022 || entranceAllowed && map == 910000000;
        if (!valid) throw new IllegalStateException("agent is outside the configured Free Market venue: id="
                + agent.getId() + " map=" + map + " entranceAllowed=" + entranceAllowed);
    }

    private EconomyOperationMetadata metadata(EconomyAgentProfile profile, Instant logicalAt,
                                               String reason, String activityId) {
        String decision = runId + ":" + profile.agentId() + ":" + logicalAt + ":" + reason;
        return new EconomyOperationMetadata(runId, logicalAt, decision, activityId, configRevision,
                catalogRevision, reason, true, false, taxPolicy.at(logicalAt));
    }

    @FunctionalInterface public interface AgentDirectory { Character resolve(String logicalAgentId); }
    @FunctionalInterface public interface AdmissionObserver {
        void admitted(EconomyAgentProfile profile, Character character);
    }
    @FunctionalInterface public interface ReleaseObserver {
        void released(EconomyAgentProfile profile, Character character);
    }
    public interface MarketBehavior {
        MarketDirective perform(Character agent, EconomyAgentProfile profile, Instant logicalAt);
        default MarketDirective drainForRelease(Character agent, EconomyAgentProfile profile,
                                                Instant logicalAt) {
            return perform(agent, profile, logicalAt);
        }
        /** Monotonic per-agent revision for meaningful economic work, excluding cosmetic idling. */
        default long progressRevision(String agentId) { return 0; }
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
        default void restoreDetached(Character agent) { }
    }
    @FunctionalInterface public interface TaxPolicy { EconomyTaxOverride at(Instant logicalAt); }

    private static Map<String, Object> sessionMap(SessionRecord session) {
        return Map.of("sessionId", session.sessionId().toString(),
                "requestId", session.requestId().toString(), "enteredAt", session.enteredAt().toString(),
                "expiresAt", session.expiresAt().toString(),
                "maximumIdleMillis", session.maximumIdleDuration().toMillis(),
                "lastProgressRevision", session.lastProgressRevision,
                "lastProgressAt", session.lastProgressAt.toString());
    }

    private static SessionRecord sessionFrom(Map<String, Object> value) {
        return new SessionRecord(UUID.fromString(value.get("sessionId").toString()),
                UUID.fromString(value.get("requestId").toString()),
                Instant.parse(value.get("enteredAt").toString()),
                Instant.parse(value.get("expiresAt").toString()),
                java.time.Duration.ofMillis(((Number) value.get("maximumIdleMillis")).longValue()),
                ((Number) value.getOrDefault("lastProgressRevision", 0)).longValue(),
                Instant.parse(value.getOrDefault("lastProgressAt", value.get("enteredAt")).toString()));
    }

    private static final class SessionRecord {
        private final UUID sessionId;
        private final UUID requestId;
        private final Instant enteredAt;
        private final Instant expiresAt;
        private final java.time.Duration maximumIdleDuration;
        private long lastProgressRevision;
        private Instant lastProgressAt;

        private SessionRecord(UUID sessionId, UUID requestId, Instant enteredAt, Instant expiresAt,
                              java.time.Duration maximumIdleDuration, long lastProgressRevision,
                              Instant lastProgressAt) {
            this.sessionId = sessionId; this.requestId = requestId; this.enteredAt = enteredAt;
            this.expiresAt = expiresAt; this.maximumIdleDuration = maximumIdleDuration;
            this.lastProgressRevision = lastProgressRevision; this.lastProgressAt = lastProgressAt;
        }
        private UUID sessionId() { return sessionId; }
        private UUID requestId() { return requestId; }
        private Instant enteredAt() { return enteredAt; }
        private Instant expiresAt() { return expiresAt; }
        private java.time.Duration maximumIdleDuration() { return maximumIdleDuration; }
    }
}
