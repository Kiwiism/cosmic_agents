package server.agents.population;

import server.agents.registry.AgentResolvedCharacter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Administrative API shared by commands and future control surfaces. */
public final class AgentPopulationAdminService {
    public interface CharacterResolver {
        AgentResolvedCharacter resolve(String name);
    }

    public record WipeResult(int removed, int stopped, List<String> messages) {
    }

    private final AgentPopulationRegistry registry;
    private final AgentPopulationSessionService sessions;
    private final AgentPopulationScheduler scheduler;
    private final AgentPopulationMetrics metrics;
    private final CharacterResolver resolver;
    private final AgentCrewService crews;

    public AgentPopulationAdminService(AgentPopulationRegistry registry,
                                       AgentPopulationSessionService sessions,
                                       AgentPopulationScheduler scheduler,
                                       AgentPopulationMetrics metrics,
                                       CharacterResolver resolver) {
        this.registry = registry;
        this.sessions = sessions;
        this.scheduler = scheduler;
        this.metrics = metrics;
        this.resolver = resolver;
        this.crews = new AgentCrewService(registry);
    }

    public AgentPopulationSnapshot snapshot() { return registry.snapshot(); }
    public AgentPopulationMetrics.Snapshot metrics() { return metrics.snapshot(); }
    public void setEnabled(boolean enabled) throws IOException {
        registry.setEnabled(enabled);
        if (enabled) scheduler.scheduleFastStart();
        else scheduler.cancelFastStart();
    }
    public void setMultiplier(double multiplier) throws IOException { registry.setMultiplier(multiplier); }
    public AgentPopulationReconciler.Result sweep() { return scheduler.sweepNow(); }

    public String add(String name) throws IOException {
        AgentResolvedCharacter resolved = resolver.resolve(name);
        if (resolved == null) return "No character named '" + name + "' exists.";
        if (!sessions.isEligible(resolved.id())) {
            return "'" + resolved.name() + "' is not an Agent-only backing character.";
        }
        return registry.add(new AgentPopulationRecord(resolved.id(), resolved.name(), null))
                ? "'" + resolved.name() + "' is now managed by Agent population."
                : "'" + resolved.name() + "' is already managed.";
    }

    public String remove(String name) throws IOException {
        return registry.remove(name) ? "'" + name + "' is no longer managed."
                : "No managed Agent named '" + name + "'.";
    }

    public int assignCrew(Integer crewId, Collection<String> names) throws IOException {
        return crews.assign(crewId, names);
    }

    public int clearLive() {
        int stopped = 0;
        for (AgentPopulationRecord record : registry.snapshot().agents()) {
            try {
                if (sessions.stop(record)) stopped++;
            } catch (Exception ignored) {
                metrics.recordFailure();
            }
        }
        return stopped;
    }

    public List<String> wipePreview() {
        return registry.snapshot().agents().stream()
                .map(record -> record.name() + " (#" + record.characterId() + ")")
                .toList();
    }

    public WipeResult wipeConfirm() throws IOException {
        int removed = 0;
        int stopped = 0;
        List<String> messages = new ArrayList<>();
        for (AgentPopulationRecord record : List.copyOf(registry.snapshot().agents())) {
            try {
                if (sessions.stop(record)) stopped++;
            } catch (Exception failure) {
                metrics.recordFailure();
            }
            if (sessions.isLive(record.characterId())) {
                messages.add("retained " + record.name() + ": live session did not stop");
                continue;
            }
            registry.remove(record.name());
            removed++;
        }
        messages.add("Backing characters and accounts were not deleted.");
        return new WipeResult(removed, stopped, List.copyOf(messages));
    }
}
