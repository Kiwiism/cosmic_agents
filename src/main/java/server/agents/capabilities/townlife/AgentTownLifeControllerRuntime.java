package server.agents.capabilities.townlife;

import client.Character;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.personality.AgentPersonalityProfile;
import server.agents.personality.AgentPersonalityState;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Validates optional high-level policy proposals and falls back to deterministic
 * TownLife. Controllers must be non-blocking; a future LLM adapter should place
 * asynchronous proposals in a bounded cache rather than call a model here.
 */
public final class AgentTownLifeControllerRuntime {
    private static final AgentTownLifeControllerRegistry CONTROLLERS =
            new AgentTownLifeControllerRegistry();

    private AgentTownLifeControllerRuntime() {
    }

    public static void installExternalController(AgentTownLifeController controller) {
        CONTROLLERS.installFallback(controller);
    }

    public static void installExternalController(int townMapId, AgentTownLifeController controller) {
        CONTROLLERS.install(townMapId, controller);
    }

    public static void clearExternalController() {
        CONTROLLERS.clear();
    }

    public static void clearExternalController(int townMapId) {
        CONTROLLERS.clear(townMapId);
    }

    static AgentTownLifeController controllerForTest(int townMapId) {
        return CONTROLLERS.resolve(townMapId);
    }

    public static void setSupportLevel(AgentRuntimeEntry entry, AgentTownLifeSupportLevel level) {
        if (entry != null) {
            entry.capabilityStates().require(AgentTownLifeControlState.STATE_KEY).setSupportLevel(level);
        }
    }

    public static AgentTownLifeSupportLevel supportLevel(AgentRuntimeEntry entry) {
        return entry == null ? AgentTownLifeSupportLevel.DETERMINISTIC
                : entry.capabilityStates().require(AgentTownLifeControlState.STATE_KEY).supportLevel();
    }

    static AgentTownLifeDecision choose(AgentRuntimeEntry entry,
                                        Character agent,
                                        AgentTownLifeState state,
                                        long nowMs) {
        AgentTownLifeState.Activity fallback = AgentTownLifeActivityPolicy.choose(entry, agent, state);
        AgentTownLifeProfile profile = AgentTownLifeProfileRepository.defaultRepository()
                .require(state.townMapId());
        AgentTownLifeDecision deterministic = deterministic(agent, state, profile, fallback);
        AgentTownLifeControlState control = entry.capabilityStates()
                .require(AgentTownLifeControlState.STATE_KEY);
        if (control.supportLevel() != AgentTownLifeSupportLevel.DIALOGUE_AND_DECISION) {
            return deterministic;
        }
        AgentTownLifeDecisionContext context = context(entry, agent, state, profile, nowMs);
        try {
            return CONTROLLERS.resolve(state.townMapId()).propose(context)
                    .filter(proposal -> valid(proposal, agent, profile, nowMs))
                    .map(proposal -> new AgentTownLifeDecision(
                            proposal.activity(), proposal.venueId(), proposal.targetAgentId(),
                            proposal.encounterType(),
                            "external:" + (proposal.source().isBlank() ? "controller" : proposal.source()),
                            proposal.correlationId()))
                    .orElse(deterministic);
        } catch (RuntimeException ignored) {
            return deterministic;
        }
    }

    private static AgentTownLifeDecisionContext context(AgentRuntimeEntry entry,
                                                        Character agent,
                                                        AgentTownLifeState state,
                                                        AgentTownLifeProfile profile,
                                                        long nowMs) {
        AgentTownLifeDecisionContext.PersonalityView personality = entry.capabilityStates()
                .find(AgentPersonalityState.STATE_KEY)
                .map(AgentPersonalityState::profile)
                .map(AgentTownLifeControllerRuntime::personalityView)
                .orElseGet(AgentTownLifeDecisionContext.PersonalityView::neutral);
        var townEntries = AgentTownLifePopulationRuntime.sameTown(agent);
        int population = townEntries.size();
        Map<String, Long> venueOccupancy = townEntries.stream()
                .map(AgentTownLifePopulationPort.AgentView::venueId)
                .filter(venueId -> venueId != null && !venueId.isBlank())
                .collect(Collectors.groupingBy(venueId -> venueId, Collectors.counting()));
        AgentTownLifeEncounterState.Snapshot encounter = entry.capabilityStates()
                .require(AgentTownLifeEncounterState.STATE_KEY).snapshot();
        AgentTownLifeDecisionContext.EncounterView encounterView = encounter.active()
                ? new AgentTownLifeDecisionContext.EncounterView(
                true, encounter.type().name(), encounter.phase().name(),
                encounter.peerAgentId(), encounter.turnOwnerAgentId(),
                encounter.participantAgentIds())
                : AgentTownLifeDecisionContext.EncounterView.none();
        return new AgentTownLifeDecisionContext(
                agent.getId(), state.townMapId(), profile.profileId(), state.stage(),
                state.visitPhase(), state.visitPurpose(), state.visitReason(),
                state.fidelity(), state.activity(),
                state.venueId(), state.role(),
                state.homeDistrict(), state.platformPreference(), population,
                agent.getMap() != null && AgentMapGatewayRuntime.map().isObservedByPlayer(agent.getMap()),
                personality, state.memory().recentActivitiesSnapshot(),
                state.memory().relationshipSummariesSnapshot().stream()
                        .map(relationship -> new AgentTownLifeDecisionContext.RelationshipView(
                                relationship.peerAgentId(), relationship.encounters(),
                                relationship.completed(), relationship.declined(),
                                relationship.lastEncounterAtMs(),
                                relationship.lastType() == null ? ""
                                        : relationship.lastType().name()))
                        .toList(),
                encounterView,
                profile.trafficZones().stream()
                        .filter(AgentTownLifeProfile.TrafficZone::excludesOccupancy)
                        .map(zone -> new AgentTownLifeDecisionContext.TrafficZoneView(
                                zone.id(), zone.type(), zone.minX(), zone.minY(),
                                zone.maxX(), zone.maxY()))
                        .toList(),
                profile.venues().stream().map(venue -> new AgentTownLifeDecisionContext.VenueView(
                        venue.id(), venue.label(), venue.district(), venue.platformKind(),
                        venue.capacity(), Math.toIntExact(venueOccupancy.getOrDefault(venue.id(), 0L)),
                        venue.affordances())).toList(),
                nowMs, state.sequence());
    }

    private static AgentTownLifeDecision deterministic(Character agent,
                                                       AgentTownLifeState state,
                                                       AgentTownLifeProfile profile,
                                                       AgentTownLifeState.Activity activity) {
        // Roaming is physical-map driven. Leaving the venue blank lets the destination service
        // distribute Agents across every reachable platform instead of pinning them to the small
        // authored venue list.
        if (activity == AgentTownLifeState.Activity.STROLL) {
            return AgentTownLifeDecision.deterministic(activity);
        }
        var venues = profile.venuesFor(activity);
        if (venues.isEmpty()) {
            return AgentTownLifeDecision.deterministic(activity);
        }
        var exact = venues.stream()
                .filter(venue -> districtMatches(state, venue))
                .filter(venue -> platformMatches(state, venue))
                .toList();
        var district = venues.stream()
                .filter(venue -> districtMatches(state, venue))
                .toList();
        var platform = venues.stream()
                .filter(venue -> platformMatches(state, venue))
                .toList();
        var candidates = !exact.isEmpty() ? exact
                : !district.isEmpty() ? district
                : !platform.isEmpty() ? platform : venues;
        Map<String, Long> occupancy = AgentTownLifePopulationRuntime.sameTown(agent).stream()
                .map(AgentTownLifePopulationPort.AgentView::venueId)
                .filter(id -> !id.isBlank())
                .collect(Collectors.groupingBy(id -> id, Collectors.counting()));
        AgentTownLifeProfile.Venue selected = candidates.stream()
                .filter(venue -> belowHardCapacity(profile, venue, occupancy))
                .max(java.util.Comparator.comparingInt(venue -> venueScore(
                        agent, state, profile, venue, occupancy)))
                .orElse(candidates.getFirst());
        return AgentTownLifeDecision.deterministic(activity, selected.id());
    }

    private static boolean belowHardCapacity(AgentTownLifeProfile profile,
                                             AgentTownLifeProfile.Venue venue,
                                             Map<String, Long> occupancy) {
        int hardCapacity = profile.hotspotForVenue(venue.id())
                .map(AgentTownLifeProfile.Hotspot::hardCapacity).orElse(venue.capacity());
        return occupancy.getOrDefault(venue.id(), 0L) < hardCapacity;
    }

    private static int venueScore(Character agent,
                                  AgentTownLifeState state,
                                  AgentTownLifeProfile profile,
                                  AgentTownLifeProfile.Venue venue,
                                  Map<String, Long> occupancy) {
        AgentTownLifeProfile.Hotspot hotspot = profile.hotspotForVenue(venue.id()).orElse(null);
        int score = hotspot == null ? 100 : hotspot.attractionWeight();
        long count = occupancy.getOrDefault(venue.id(), 0L);
        int softCapacity = hotspot == null ? Math.max(1, venue.capacity() / 2)
                : hotspot.softCapacity();
        score -= Math.toIntExact(Math.min(100L, count * 12L));
        if (count >= softCapacity) {
            score -= 35;
        }
        if (!venue.spots().isEmpty() && agent.getPosition() != null) {
            score -= (int) Math.min(40L,
                    Math.round(agent.getPosition().distance(venue.spots().getFirst().point()) / 250.0d));
        }
        score += AgentTownLifeRolePolicy.variation(
                agent.getId(), state.sequence(), 17, 313 + venue.id().hashCode());
        return score;
    }

    private static boolean districtMatches(AgentTownLifeState state,
                                           AgentTownLifeProfile.Venue venue) {
        return state.preferredDistrict() == AgentTownLifeState.District.ANY
                || venue.district() == state.preferredDistrict();
    }

    private static boolean platformMatches(AgentTownLifeState state,
                                           AgentTownLifeProfile.Venue venue) {
        return state.platformPreference() == AgentTownLifeState.PlatformKind.ANY
                || venue.platformKind() == state.platformPreference();
    }

    private static AgentTownLifeDecisionContext.PersonalityView personalityView(
            AgentPersonalityProfile profile) {
        if (profile == null || profile.traits() == null) {
            return AgentTownLifeDecisionContext.PersonalityView.neutral();
        }
        AgentPersonalityProfile.Traits traits = profile.traits();
        return new AgentTownLifeDecisionContext.PersonalityView(
                traits.patience(), traits.activity(), traits.curiosity(), traits.sociability(),
                traits.routinePreference(), traits.expressiveness());
    }

    private static boolean valid(AgentTownLifeDirective proposal,
                                 Character agent,
                                 AgentTownLifeProfile profile,
                                 long nowMs) {
        if (proposal == null || proposal.validUntilMs() < nowMs) {
            return false;
        }
        if (!proposal.venueId().isBlank()) {
            AgentTownLifeProfile.Venue venue = profile.venue(proposal.venueId()).orElse(null);
            if (venue == null || !venue.supports(proposal.activity())) {
                return false;
            }
        }
        if (proposal.targetAgentId() <= 0) {
            return validEncounterType(proposal);
        }
        if (proposal.activity() != AgentTownLifeState.Activity.SOCIALIZE
                && proposal.activity() != AgentTownLifeState.Activity.SHOW_OFF) {
            return false;
        }
        AgentRuntimeEntry targetEntry = AgentRuntimeRegistry.findByAgentCharacterId(
                proposal.targetAgentId());
        Character target = AgentRuntimeIdentityRuntime.bot(targetEntry);
        if (!validEncounterType(proposal)
                || target == null || target == agent || !sameScope(agent, target)
                || !AgentTownLifeRuntime.active(targetEntry)
                || AgentTownLifeEncounterCoordinator.active(targetEntry)) {
            return false;
        }
        return true;
    }

    private static boolean sameScope(Character first, Character second) {
        return first != null && second != null
                && first.getWorld() == second.getWorld()
                && first.getMapId() == second.getMapId()
                && AgentClientGatewayRuntime.clients().hasClient(first)
                && AgentClientGatewayRuntime.clients().hasClient(second)
                && AgentClientGatewayRuntime.clients().channel(first)
                == AgentClientGatewayRuntime.clients().channel(second);
    }

    private static boolean validEncounterType(AgentTownLifeDirective proposal) {
        if (proposal.activity() == AgentTownLifeState.Activity.SOCIALIZE) {
            return proposal.encounterType() == null
                    || proposal.encounterType() == AgentTownLifeEncounterState.Type.SOCIAL_CHAT;
        }
        if (proposal.activity() == AgentTownLifeState.Activity.SHOW_OFF) {
            return proposal.encounterType() == null
                    || proposal.encounterType() == AgentTownLifeEncounterState.Type.PLAYFUL_SPARRING;
        }
        return proposal.encounterType() == null;
    }
}
