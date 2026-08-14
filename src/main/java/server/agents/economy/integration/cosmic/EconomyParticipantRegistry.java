package server.agents.economy.integration.cosmic;

import client.Character;
import server.agents.economy.scenario.EconomyAgentProfile;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Run-scoped identity bridge; only admitted agents are classified as economic participants. */
public final class EconomyParticipantRegistry implements CosmicEconomyWorldAdapter.AgentDirectory,
        CosmicPublicTradeNegotiator.ParticipantDirectory {
    private final Function<String, Character> source;
    private final Map<String, CosmicPublicTradeNegotiator.Participant> byLogical = new ConcurrentHashMap<>();
    private final Map<Integer, CosmicPublicTradeNegotiator.Participant> byCharacter = new ConcurrentHashMap<>();

    public EconomyParticipantRegistry(Function<String, Character> source) {
        this.source = Objects.requireNonNull(source);
    }

    @Override public Character resolve(String logicalAgentId) { return source.apply(logicalAgentId); }

    public void admitted(EconomyAgentProfile profile, Character character) {
        CosmicPublicTradeNegotiator.Participant participant =
                new CosmicPublicTradeNegotiator.Participant(character, profile);
        CosmicPublicTradeNegotiator.Participant logicalPrevious = byLogical.putIfAbsent(profile.agentId(), participant);
        CosmicPublicTradeNegotiator.Participant characterPrevious = byCharacter.putIfAbsent(character.getId(), participant);
        if (logicalPrevious != null || characterPrevious != null) {
            if (logicalPrevious == null) byLogical.remove(profile.agentId(), participant);
            if (characterPrevious == null) byCharacter.remove(character.getId(), participant);
            throw new IllegalStateException("economy participant identity is already bound");
        }
    }

    @Override public Optional<CosmicPublicTradeNegotiator.Participant> byCharacterId(int characterId) {
        return Optional.ofNullable(byCharacter.get(characterId));
    }
    public Character admittedCharacter(String logicalAgentId) {
        CosmicPublicTradeNegotiator.Participant value = byLogical.get(logicalAgentId);
        return value == null ? null : value.character();
    }
    public boolean isAdmittedCharacter(int characterId) { return byCharacter.containsKey(characterId); }
}
