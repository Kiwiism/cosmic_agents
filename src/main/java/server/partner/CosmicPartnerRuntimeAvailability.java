package server.partner;

import server.agents.integration.AgentCharacterGatewayRuntime;

public final class CosmicPartnerRuntimeAvailability implements PartnerRosterQueryService.RuntimeAvailability {
    private final AdventurerPartnerRepository repository;

    public CosmicPartnerRuntimeAvailability(AdventurerPartnerRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean isOnline(int characterId) {
        return AgentCharacterGatewayRuntime.characters().findOnlineCharacterById(characterId) != null;
    }

    @Override
    public boolean isLeased(int characterId) {
        return ProfileLeaseRegistry.global().isUnavailable(characterId);
    }

    @Override
    public boolean canLoadCanonicalProfile(int characterId) {
        return repository.findCharacter(characterId).isPresent();
    }
}
