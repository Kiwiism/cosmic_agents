package server.life.simulation;

import server.life.Monster;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** One generation-safe session at most for each live Monster instance. */
public final class MobSimulationRegistry {
    private final ConcurrentMap<Monster, MobSimulationSession> sessions = new ConcurrentHashMap<>();

    public MobSimulationSession get(Monster monster) { return sessions.get(monster); }
    public MobSimulationSession putIfAbsent(Monster monster, MobSimulationSession session) {
        return sessions.putIfAbsent(monster, session);
    }
    public boolean remove(Monster monster, MobSimulationSession session) {
        return sessions.remove(monster, session);
    }
    public Collection<MobSimulationSession> snapshot() { return List.copyOf(sessions.values()); }
    public int size() { return sessions.size(); }
}
