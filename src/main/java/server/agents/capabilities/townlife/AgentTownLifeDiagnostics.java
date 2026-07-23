package server.agents.capabilities.townlife;

import client.Character;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Read-only operational view of TownLife state, authored profiles, and safety counters. */
public final class AgentTownLifeDiagnostics {
    private static final int MAX_LINES = 40;

    private AgentTownLifeDiagnostics() {
    }

    public static List<String> lines(String[] params) {
        String verb = params == null || params.length == 0
                ? "status" : params[0].toLowerCase(java.util.Locale.ROOT);
        return bounded(switch (verb) {
            case "status" -> status();
            case "agent" -> agent(params);
            case "venues" -> venues(params);
            case "encounters" -> encounters();
            case "fidelity" -> fidelity();
            case "metrics" -> metrics();
            case "validate" -> validateProfiles();
            default -> List.of("Usage: !townlife status|agent <ign>|venues [mapId]"
                    + "|encounters|fidelity|metrics|validate");
        });
    }

    private static List<String> status() {
        List<AgentRuntimeEntry> entries = activeEntries();
        Map<AgentTownLifeState.Stage, Long> stages = entries.stream()
                .map(entry -> state(entry).stage())
                .collect(Collectors.groupingBy(value -> value,
                        () -> new EnumMap<>(AgentTownLifeState.Stage.class), Collectors.counting()));
        return List.of("TownLife active=" + entries.size()
                        + " profiles=" + AgentTownLifeProfileRepository.defaultRepository().profiles().size(),
                "Stages: " + stages,
                "Active encounters=" + entries.stream()
                        .filter(AgentTownLifeEncounterCoordinator::active).count());
    }

    private static List<String> agent(String[] params) {
        if (params.length < 2) {
            return List.of("Usage: !townlife agent <ign>");
        }
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentName(params[1]);
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        AgentTownLifeState state = entry == null ? null
                : entry.capabilityStates().find(AgentTownLifeState.STATE_KEY).orElse(null);
        if (agent == null || state == null) {
            return List.of("TownLife Agent not found: " + params[1]);
        }
        AgentTownLifeEncounterState.Snapshot encounter = entry.capabilityStates()
                .require(AgentTownLifeEncounterState.STATE_KEY).snapshot();
        List<String> lines = new ArrayList<>();
        lines.add("TownLife " + agent.getName() + " id=" + agent.getId()
                + " map=" + agent.getMapId() + " enabled=" + state.enabled());
        lines.add("stage=" + state.stage() + " activity=" + state.activity()
                + " venue=" + state.venueId() + " fidelity=" + state.fidelity());
        lines.add("role=" + state.role() + " district=" + state.homeDistrict()
                + " platform=" + state.platformPreference()
                + " controller=" + AgentTownLifeControllerRuntime.supportLevel(entry));
        lines.add("decision=" + state.decisionSource() + " target=" + state.targetCharacterId()
                + " destination=" + state.destinationKey());
        lines.add("relationships=" + state.memory().relationshipSummariesSnapshot().size()
                + " encounter=" + (encounter.active()
                ? encounter.type() + "/" + encounter.phase() + "/" + encounter.encounterId()
                : "none"));
        return lines;
    }

    private static List<String> venues(String[] params) {
        int mapId = params.length >= 2 ? Integer.parseInt(params[1]) : 104000000;
        AgentTownLifeProfile profile = AgentTownLifeProfileRepository.defaultRepository().find(mapId)
                .orElse(null);
        if (profile == null) {
            return List.of("No TownLife profile for map " + mapId);
        }
        List<String> result = new ArrayList<>();
        result.add(profile.profileId() + " venues=" + profile.venues().size()
                + " trafficZones=" + profile.trafficZones().size());
        for (AgentTownLifeProfile.Venue venue : profile.venues()) {
            result.add(venue.id() + " capacity=" + venue.capacity()
                    + " district=" + venue.district() + " affords=" + venue.affordances());
        }
        return result;
    }

    private static List<String> encounters() {
        return activeEntries().stream()
                .map(entry -> Map.entry(entry, entry.capabilityStates()
                        .require(AgentTownLifeEncounterState.STATE_KEY).snapshot()))
                .filter(pair -> pair.getValue().active()
                        && pair.getValue().role() == AgentTownLifeEncounterState.Role.INITIATOR)
                .map(pair -> AgentRuntimeIdentityRuntime.botName(pair.getKey()) + " "
                        + pair.getValue().type() + "/" + pair.getValue().phase()
                        + " venue=" + pair.getValue().venueId()
                        + " members=" + pair.getValue().participantAgentIds())
                .toList();
    }

    private static List<String> fidelity() {
        Map<AgentTownLifeFidelity, Long> counts = activeEntries().stream()
                .map(entry -> state(entry).fidelity())
                .collect(Collectors.groupingBy(value -> value,
                        () -> new EnumMap<>(AgentTownLifeFidelity.class), Collectors.counting()));
        return List.of("TownLife fidelity: " + counts);
    }

    private static List<String> metrics() {
        AgentTownLifeMetrics.Snapshot metrics = AgentTownLifeMetrics.snapshot();
        return List.of(
                "TownLife activity phases=" + metrics.activityPhases(),
                "encounter phases=" + metrics.encounterPhases(),
                "reservations=" + metrics.reservationAttempts()
                        + " failures=" + metrics.reservationFailures()
                        + " navigationAbandons=" + metrics.navigationAbandons(),
                "groups=" + metrics.encounterGroups()
                        + " participants=" + metrics.encounterParticipants()
                        + " maxGroup=" + metrics.maxEncounterSize()
                        + " fidelityTransitions=" + metrics.fidelityTransitions(),
                "venue selections=" + metrics.venueSelections());
    }

    private static List<String> validateProfiles() {
        return AgentTownLifeProfileRepository.defaultRepository().profiles().stream()
                .sorted(Comparator.comparingInt(AgentTownLifeProfile::mapId))
                .map(AgentTownLifeProfileValidator::validate)
                .map(result -> result.profileId() + " map=" + result.mapId()
                        + " valid=" + result.valid() + " errors=" + result.errors()
                        + " warnings=" + result.warnings())
                .toList();
    }

    private static List<AgentRuntimeEntry> activeEntries() {
        return AgentRuntimeRegistry.activeEntriesSnapshot().stream()
                .filter(AgentTownLifeRuntime::active)
                .toList();
    }

    private static AgentTownLifeState state(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
    }

    private static List<String> bounded(List<String> lines) {
        if (lines.isEmpty()) {
            return List.of("No matching TownLife state.");
        }
        if (lines.size() <= MAX_LINES) {
            return List.copyOf(lines);
        }
        List<String> result = new ArrayList<>(lines.subList(0, MAX_LINES));
        result.add("... " + (lines.size() - MAX_LINES) + " more");
        return List.copyOf(result);
    }
}
