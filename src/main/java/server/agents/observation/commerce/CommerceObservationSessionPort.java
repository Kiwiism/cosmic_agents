package server.agents.observation.commerce;

import client.Character;
import server.agents.economy.scenario.PopulationAdmissionPlanner;
import server.agents.economy.session.CommerceParticipant;
import server.agents.economy.session.EconomySessionPort;
import server.agents.economy.integration.cosmic.CosmicCommerceObservationPresenceAdapter;
import server.maps.MapleMap;
import server.maps.Portal;

import java.awt.Point;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Materializes only the cohort members whose logical Commerce admission is due. */
public final class CommerceObservationSessionPort implements EconomySessionPort {
    private final EconomySessionPort delegate;
    private final Map<String, Character> directory;
    private final int freeMarketEntranceMapId;
    private final CommerceObservationPresencePort presence;
    private final Map<String, OriginalPresence> staged = new ConcurrentHashMap<>();

    public CommerceObservationSessionPort(
            EconomySessionPort delegate,
            Map<String, Character> directory,
            List<PopulationAdmissionPlanner.Admission> admissions,
            Instant logicalStart,
            int freeMarketEntranceMapId) {
        this(delegate, directory, admissions, logicalStart, freeMarketEntranceMapId,
                CosmicCommerceObservationPresenceAdapter.INSTANCE);
    }

    CommerceObservationSessionPort(
            EconomySessionPort delegate,
            Map<String, Character> directory,
            List<PopulationAdmissionPlanner.Admission> admissions,
            Instant logicalStart,
            int freeMarketEntranceMapId,
            CommerceObservationPresencePort presence) {
        this.delegate = java.util.Objects.requireNonNull(delegate, "Commerce sessions");
        this.directory = Map.copyOf(directory);
        this.presence = java.util.Objects.requireNonNull(presence, "Commerce presence");
        if (freeMarketEntranceMapId <= 0) {
            throw new IllegalArgumentException("Free Market entrance map id must be positive");
        }
        this.freeMarketEntranceMapId = freeMarketEntranceMapId;
        try {
            for (PopulationAdmissionPlanner.Admission admission : admissions) {
                stage(admission.agentId());
            }
            admissions.stream()
                    .filter(admission -> !admission.admittedAt().isAfter(logicalStart))
                    .forEach(admission -> materialize(admission.agentId()));
        } catch (RuntimeException failure) {
            restoreUnadmittedCharacters();
            throw failure;
        }
    }

    @Override
    public EntryResult requestEntry(
            CommerceParticipant profile, EntryRequest request, Instant logicalAt) {
        materialize(profile.agentId());
        return delegate.requestEntry(profile, request, logicalAt);
    }

    @Override
    public Directive performMarketCycle(
            UUID sessionId, CommerceParticipant profile, Instant logicalAt) {
        return delegate.performMarketCycle(sessionId, profile, logicalAt);
    }

    @Override
    public ReleaseResult release(
            UUID sessionId, CommerceParticipant profile, Instant logicalAt, String reason) {
        return delegate.release(sessionId, profile, logicalAt, reason);
    }

    @Override public Map<String, Object> snapshotState() { return delegate.snapshotState(); }

    @Override
    public void restoreState(Map<String, Object> state) {
        delegate.restoreState(state);
    }

    @Override
    public void restoreState(
            Map<String, Object> state, Map<String, CommerceParticipant> profiles) {
        profiles.keySet().forEach(this::materialize);
        delegate.restoreState(state, profiles);
    }

    @Override
    public Optional<Presence> sessionPresence(CommerceParticipant profile) {
        return delegate.sessionPresence(profile);
    }

    public void restoreUnadmittedCharacters() {
        List<String> ids = List.copyOf(staged.keySet());
        for (String id : ids) {
            OriginalPresence original = staged.remove(id);
            Character agent = directory.get(id);
            if (original == null || !presence.live(agent)) continue;
            MapleMap map = presence.resolveMap(agent, original.mapId());
            presence.changeMap(agent, map, original.position());
        }
    }

    public int stagedCount() {
        return staged.size();
    }

    private void stage(String agentId) {
        Character agent = requireAgent(agentId);
        if (agent.getMap() == null) {
            throw new IllegalStateException("future Commerce participant has no live map: " + agentId);
        }
        OriginalPresence original = new OriginalPresence(agent.getMapId(),
                agent.getPosition() == null ? new Point() : new Point(agent.getPosition()));
        if (staged.putIfAbsent(agentId, original) != null) return;
        try {
            agent.getMap().removePlayer(agent);
        } catch (RuntimeException failure) {
            staged.remove(agentId);
            throw failure;
        }
    }

    private void materialize(String agentId) {
        OriginalPresence original = staged.remove(agentId);
        if (original == null) return;
        Character agent = requireAgent(agentId);
        try {
            MapleMap entrance = presence.resolveMap(agent, freeMarketEntranceMapId);
            Portal spawn = entrance.getPortal(0);
            if (spawn == null) {
                spawn = entrance.findClosestPlayerSpawnpoint(new Point());
            }
            if (spawn == null) {
                throw new IllegalStateException("Free Market entrance has no spawn portal");
            }
            presence.changeMap(agent, entrance, spawn.getPosition());
        } catch (RuntimeException failure) {
            staged.put(agentId, original);
            throw failure;
        }
    }

    private Character requireAgent(String agentId) {
        Character agent = directory.get(agentId);
        if (!presence.live(agent)) {
            throw new IllegalStateException("Commerce observation participant is not live: " + agentId);
        }
        return agent;
    }

    private record OriginalPresence(int mapId, Point position) {
        private OriginalPresence {
            position = new Point(position);
        }
    }
}
