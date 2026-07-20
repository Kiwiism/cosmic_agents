package server.agents.plans.mapleisland.cohort;

import server.agents.plans.mapleisland.cohort.MapleIslandCohortPoolSnapshot.Agent;
import server.agents.plans.mapleisland.cohort.MapleIslandCohortPoolSnapshot.LeaseState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;

/** Offline maintenance operation that redistributes durable pool character names. */
public final class MapleIslandCohortPoolRenamer {
    public record Rename(int characterId, int accountId, String oldName, String newName) {
        Rename reverse() {
            return new Rename(characterId, accountId, newName, oldName);
        }
    }

    public record Result(int total, int renamed, String firstName, String lastName) {
    }

    public interface Hooks {
        Integer characterIdByName(String characterName) throws Exception;

        void renameCharacters(List<Rename> renames) throws Exception;
    }

    private final MapleIslandCohortPoolRegistry registry;
    private final Hooks hooks;
    private final IntPredicate isCharacterLive;

    public MapleIslandCohortPoolRenamer(MapleIslandCohortPoolRegistry registry,
                                        Hooks hooks,
                                        IntPredicate isCharacterLive) {
        this.registry = registry;
        this.hooks = hooks;
        this.isCharacterLive = isCharacterLive;
    }

    public Result renameAll() throws Exception {
        List<Agent> agents = registry.snapshot().agents();
        for (Agent agent : agents) {
            if (agent.leaseState() != LeaseState.AVAILABLE || isCharacterLive.test(agent.characterId())) {
                throw new IllegalStateException(
                        "Stop the cohort and wait for every pool character to be available before renaming");
            }
        }
        if (agents.isEmpty()) {
            return new Result(0, 0, "", "");
        }

        Set<Integer> poolCharacterIds = agents.stream()
                .map(Agent::characterId)
                .collect(Collectors.toSet());
        List<String> targets = availableCatalogNames(agents.size(), poolCharacterIds);
        List<Rename> changes = new ArrayList<>();
        Map<Integer, String> namesByCharacterId = new LinkedHashMap<>();
        for (int index = 0; index < agents.size(); index++) {
            Agent agent = agents.get(index);
            String target = targets.get(index);
            if (!agent.name().equals(target)) {
                changes.add(new Rename(agent.characterId(), agent.accountId(), agent.name(), target));
                namesByCharacterId.put(agent.characterId(), target);
            }
        }
        if (!changes.isEmpty()) {
            hooks.renameCharacters(changes);
            try {
                registry.renameAgents(namesByCharacterId);
            } catch (IOException poolFailure) {
                try {
                    hooks.renameCharacters(changes.stream().map(Rename::reverse).toList());
                } catch (Exception rollbackFailure) {
                    poolFailure.addSuppressed(rollbackFailure);
                }
                throw poolFailure;
            }
        }
        return new Result(agents.size(), changes.size(), targets.get(0), targets.get(targets.size() - 1));
    }

    private List<String> availableCatalogNames(int count, Set<Integer> poolCharacterIds) throws Exception {
        List<String> result = new ArrayList<>(count);
        for (String candidate : MapleIslandCohortNameCatalog.candidates()) {
            Integer ownerId = hooks.characterIdByName(candidate);
            if (ownerId == null || poolCharacterIds.contains(ownerId)) {
                result.add(candidate);
                if (result.size() == count) {
                    return result;
                }
            }
        }
        throw new IllegalStateException("Not enough unused cohort names are available for " + count + " characters");
    }
}
