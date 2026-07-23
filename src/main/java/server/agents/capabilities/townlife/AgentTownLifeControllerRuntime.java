package server.agents.capabilities.townlife;

import client.Character;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.personality.AgentPersonalityProfile;
import server.agents.personality.AgentPersonalityState;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.util.Optional;

/**
 * Validates optional high-level policy proposals and falls back to deterministic
 * TownLife. Controllers must be non-blocking; a future LLM adapter should place
 * asynchronous proposals in a bounded cache rather than call a model here.
 */
public final class AgentTownLifeControllerRuntime {
    private static final AgentTownLifeController NO_EXTERNAL_CONTROLLER = context -> Optional.empty();
    private static volatile AgentTownLifeController externalController = NO_EXTERNAL_CONTROLLER;

    private AgentTownLifeControllerRuntime() {
    }

    public static void installExternalController(AgentTownLifeController controller) {
        externalController = controller == null ? NO_EXTERNAL_CONTROLLER : controller;
    }

    public static void clearExternalController() {
        externalController = NO_EXTERNAL_CONTROLLER;
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
            return externalController.propose(context)
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
        var townEntries = AgentRuntimeRegistry.activeEntriesSnapshot().stream()
                .filter(AgentTownLifeRuntime::active)
                .map(AgentRuntimeIdentityRuntime::bot)
                .filter(candidate -> candidate != null && candidate.getMapId() == agent.getMapId())
                .toList();
        int population = townEntries.size();
        AgentTownLifeEncounterState.Snapshot encounter = entry.capabilityStates()
                .require(AgentTownLifeEncounterState.STATE_KEY).snapshot();
        AgentTownLifeDecisionContext.EncounterView encounterView = encounter.active()
                ? new AgentTownLifeDecisionContext.EncounterView(
                true, encounter.type().name(), encounter.phase().name(),
                encounter.peerAgentId(), encounter.turnOwnerAgentId())
                : AgentTownLifeDecisionContext.EncounterView.none();
        return new AgentTownLifeDecisionContext(
                agent.getId(), state.townMapId(), profile.profileId(), state.stage(), state.activity(),
                state.venueId(), state.role(),
                state.homeDistrict(), state.platformPreference(), population,
                agent.getMap() != null && AgentMapGatewayRuntime.map().isObservedByPlayer(agent.getMap()),
                personality, state.memory().recentActivitiesSnapshot(), encounterView,
                profile.venues().stream().map(venue -> new AgentTownLifeDecisionContext.VenueView(
                        venue.id(), venue.label(), venue.district(), venue.platformKind(),
                        venue.capacity(), venueOccupancy(townEntries, venue.id()), venue.affordances())).toList(),
                nowMs, state.sequence());
    }

    private static AgentTownLifeDecision deterministic(Character agent,
                                                       AgentTownLifeState state,
                                                       AgentTownLifeProfile profile,
                                                       AgentTownLifeState.Activity activity) {
        if (activity == AgentTownLifeState.Activity.SOCIAL
                || activity == AgentTownLifeState.Activity.WEAPON_FLOURISH) {
            return AgentTownLifeDecision.deterministic(activity);
        }
        var venues = profile.venuesFor(activity);
        if (venues.isEmpty()) {
            return AgentTownLifeDecision.deterministic(activity);
        }
        var preferred = venues.stream()
                .filter(venue -> state.preferredDistrict() == AgentTownLifeState.District.ANY
                        || venue.district() == state.preferredDistrict())
                .toList();
        var candidates = preferred.isEmpty() ? venues : preferred;
        int index = AgentTownLifeRolePolicy.variation(
                agent.getId(), state.sequence(), candidates.size(), 313);
        return AgentTownLifeDecision.deterministic(activity, candidates.get(index).id());
    }

    private static int venueOccupancy(java.util.List<Character> townAgents, String venueId) {
        return (int) townAgents.stream()
                .map(AgentRuntimeRegistry::findByCharacterInstance)
                .filter(java.util.Objects::nonNull)
                .map(candidate -> candidate.capabilityStates().find(AgentTownLifeState.STATE_KEY).orElse(null))
                .filter(java.util.Objects::nonNull)
                .filter(candidate -> venueId.equals(candidate.venueId()))
                .count();
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
        if (proposal.activity() != AgentTownLifeState.Activity.SOCIAL
                && proposal.activity() != AgentTownLifeState.Activity.WEAPON_FLOURISH) {
            return false;
        }
        AgentRuntimeEntry targetEntry = AgentRuntimeRegistry.findByAgentCharacterId(
                proposal.targetAgentId());
        Character target = AgentRuntimeIdentityRuntime.bot(targetEntry);
        if (!validEncounterType(proposal)
                || target == null || target == agent || target.getMapId() != agent.getMapId()
                || !AgentTownLifeRuntime.active(targetEntry)) {
            return false;
        }
        if (proposal.venueId().isBlank()) {
            return true;
        }
        AgentTownLifeProfile.Venue venue = profile.venue(proposal.venueId()).orElseThrow();
        return venue.spots().stream().anyMatch(spot ->
                spot.point().distanceSq(target.getPosition()) <= 220L * 220L);
    }

    private static boolean validEncounterType(AgentTownLifeDirective proposal) {
        if (proposal.activity() == AgentTownLifeState.Activity.SOCIAL) {
            return proposal.encounterType() == null
                    || proposal.encounterType() == AgentTownLifeEncounterState.Type.SOCIAL_CHAT;
        }
        if (proposal.activity() == AgentTownLifeState.Activity.WEAPON_FLOURISH) {
            return proposal.encounterType() == null
                    || proposal.encounterType() == AgentTownLifeEncounterState.Type.PLAYFUL_SPARRING;
        }
        return proposal.encounterType() == null;
    }
}
