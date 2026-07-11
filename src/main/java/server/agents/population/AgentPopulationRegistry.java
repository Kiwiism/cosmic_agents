package server.agents.population;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Thread-safe source of truth for the explicitly managed Agent roster. */
public final class AgentPopulationRegistry {
    private final AgentPopulationStore store;
    private AgentPopulationSnapshot state;

    public AgentPopulationRegistry(AgentPopulationStore store) throws IOException {
        this.store = store;
        this.state = store.load();
        ensureUnique(state.agents());
    }

    public synchronized AgentPopulationSnapshot snapshot() {
        return state;
    }

    public synchronized boolean add(AgentPopulationRecord record) throws IOException {
        if (state.agents().stream().anyMatch(existing -> existing.characterId() == record.characterId()
                || existing.name().equalsIgnoreCase(record.name()))) {
            return false;
        }
        List<AgentPopulationRecord> agents = new java.util.ArrayList<>(state.agents());
        agents.add(record);
        persist(new AgentPopulationSnapshot(state.enabled(), state.multiplier(), agents));
        return true;
    }

    public synchronized boolean remove(String name) throws IOException {
        List<AgentPopulationRecord> agents = state.agents().stream()
                .filter(record -> !record.name().equalsIgnoreCase(name))
                .toList();
        if (agents.size() == state.agents().size()) {
            return false;
        }
        persist(new AgentPopulationSnapshot(state.enabled(), state.multiplier(), agents));
        return true;
    }

    public synchronized boolean setCrew(String name, Integer crewId) throws IOException {
        boolean[] found = {false};
        List<AgentPopulationRecord> agents = state.agents().stream().map(record -> {
            if (record.name().equalsIgnoreCase(name)) {
                found[0] = true;
                return record.withCrew(crewId);
            }
            return record;
        }).toList();
        if (found[0]) {
            persist(new AgentPopulationSnapshot(state.enabled(), state.multiplier(), agents));
        }
        return found[0];
    }

    public synchronized void setEnabled(boolean enabled) throws IOException {
        persist(new AgentPopulationSnapshot(enabled, state.multiplier(), state.agents()));
    }

    public synchronized void setMultiplier(double multiplier) throws IOException {
        persist(new AgentPopulationSnapshot(state.enabled(), multiplier, state.agents()));
    }

    public synchronized void clear() throws IOException {
        persist(new AgentPopulationSnapshot(state.enabled(), state.multiplier(), List.of()));
    }

    private void persist(AgentPopulationSnapshot next) throws IOException {
        store.save(next);
        state = next;
    }

    private static void ensureUnique(List<AgentPopulationRecord> records) throws IOException {
        Map<Integer, String> ids = new LinkedHashMap<>();
        Map<String, Integer> names = new LinkedHashMap<>();
        for (AgentPopulationRecord record : records) {
            String oldName = ids.putIfAbsent(record.characterId(), record.name());
            Integer oldId = names.putIfAbsent(record.name().toLowerCase(Locale.ROOT), record.characterId());
            if (oldName != null || oldId != null) {
                throw new IOException("Duplicate Agent population record: " + record.name());
            }
        }
    }
}
