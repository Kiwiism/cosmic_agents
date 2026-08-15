package server.agents.economy.integration.cosmic;

import client.Character;
import server.agents.economy.scenario.EconomyAgentProfile;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Run-scoped identity bridge with separate durable binding and active-session membership. */
public final class EconomyParticipantRegistry implements CosmicEconomyWorldAdapter.AgentDirectory,
        CosmicPublicTradeNegotiator.ParticipantDirectory {
    private final Function<String, Character> source;
    private final Map<String, CosmicPublicTradeNegotiator.Participant> byLogical = new ConcurrentHashMap<>();
    private final Map<Integer, CosmicPublicTradeNegotiator.Participant> byCharacter = new ConcurrentHashMap<>();
    private final java.util.Set<String> activeLogicalIds = ConcurrentHashMap.newKeySet();

    public EconomyParticipantRegistry(Function<String, Character> source) {
        this.source = Objects.requireNonNull(source);
    }

    @Override public Character resolve(String logicalAgentId) { return source.apply(logicalAgentId); }

    public void admitted(EconomyAgentProfile profile, Character character) {
        CosmicPublicTradeNegotiator.Participant participant =
                new CosmicPublicTradeNegotiator.Participant(character, profile);
        CosmicPublicTradeNegotiator.Participant logicalPrevious = byLogical.putIfAbsent(profile.agentId(), participant);
        CosmicPublicTradeNegotiator.Participant characterPrevious = byCharacter.putIfAbsent(character.getId(), participant);
        boolean sameLogical = logicalPrevious == null || logicalPrevious.character().getId() == character.getId();
        boolean sameCharacter = characterPrevious == null
                || characterPrevious.profile().agentId().equals(profile.agentId());
        if (!sameLogical || !sameCharacter) {
            if (logicalPrevious == null) byLogical.remove(profile.agentId(), participant);
            if (characterPrevious == null) byCharacter.remove(character.getId(), participant);
            throw new IllegalStateException("economy participant identity is already bound");
        }
        activeLogicalIds.add(profile.agentId());
    }

    public void released(EconomyAgentProfile profile, Character character) {
        CosmicPublicTradeNegotiator.Participant bound = byLogical.get(profile.agentId());
        if (bound == null || bound.character().getId() != character.getId())
            throw new IllegalStateException("economy participant release does not match its binding");
        activeLogicalIds.remove(profile.agentId());
    }

    @Override public Optional<CosmicPublicTradeNegotiator.Participant> byCharacterId(int characterId) {
        CosmicPublicTradeNegotiator.Participant value = byCharacter.get(characterId);
        return value == null || !activeLogicalIds.contains(value.profile().agentId())
                ? Optional.empty() : Optional.of(value);
    }
    public Optional<CosmicPublicTradeNegotiator.Participant> byLogicalId(String logicalAgentId) {
        CosmicPublicTradeNegotiator.Participant value = byLogical.get(logicalAgentId);
        return value == null || !activeLogicalIds.contains(logicalAgentId)
                ? Optional.empty() : Optional.of(value);
    }
    public Optional<CosmicPublicTradeNegotiator.Participant> byBoundCharacterId(int characterId) {
        return Optional.ofNullable(byCharacter.get(characterId));
    }
    public Character boundCharacter(String logicalAgentId) {
        CosmicPublicTradeNegotiator.Participant value = byLogical.get(logicalAgentId);
        return value == null ? null : value.character();
    }
    public Character admittedCharacter(String logicalAgentId) {
        CosmicPublicTradeNegotiator.Participant value = byLogical.get(logicalAgentId);
        return value == null || !activeLogicalIds.contains(logicalAgentId) ? null : value.character();
    }
    public boolean isAdmittedCharacter(int characterId) { return byCharacterId(characterId).isPresent(); }
    public boolean isBoundCharacter(int characterId) { return byCharacter.containsKey(characterId); }
    public boolean isBoundAgent(String logicalAgentId) { return byLogical.containsKey(logicalAgentId); }
    public boolean matchesBinding(String logicalAgentId, int characterId) {
        CosmicPublicTradeNegotiator.Participant value = byLogical.get(logicalAgentId);
        return value != null && value.character().getId() == characterId;
    }
}
