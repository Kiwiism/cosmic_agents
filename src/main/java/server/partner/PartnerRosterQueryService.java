package server.partner;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PartnerRosterQueryService {
    private final AdventurerPartnerRepository repository;
    private final RuntimeAvailability runtimeAvailability;

    public PartnerRosterQueryService(AdventurerPartnerRepository repository,
                                     RuntimeAvailability runtimeAvailability) {
        this.repository = repository;
        this.runtimeAvailability = runtimeAvailability;
    }

    public List<PartnerRosterEntry> listRoster(int accountId, int worldId, int currentCharacterId) {
        Optional<PartnerLink> currentLink = repository.findActiveLinkForCharacter(currentCharacterId);
        List<PartnerRosterEntry> result = new ArrayList<>();
        for (PartnerRosterCandidate candidate
                : repository.findRosterCandidates(accountId, worldId, currentCharacterId)) {
            String rejection = rejectionReason(
                    candidate, accountId, worldId, currentCharacterId, currentLink);
            result.add(rejection == null
                    ? PartnerRosterEntry.eligible(
                            candidate.characterId(), candidate.name(), candidate.level(), candidate.jobId())
                    : PartnerRosterEntry.rejected(
                            candidate.characterId(), candidate.name(), candidate.level(), candidate.jobId(), rejection));
        }
        return List.copyOf(result);
    }

    public Optional<String> activationRejectionReason(int accountId,
                                                      int worldId,
                                                      int currentCharacterId,
                                                      PartnerRosterCandidate candidate,
                                                      PartnerLink expectedLink) {
        if (candidate == null || candidate.characterId() == currentCharacterId) {
            return Optional.of("The selected Partner profile is invalid.");
        }
        if (candidate.accountId() != accountId || candidate.worldId() != worldId) {
            return Optional.of("The registered Partner must share the account and world.");
        }
        if (candidate.pendingWorldTransfer()) {
            return Optional.of("The registered Partner has a pending world transfer.");
        }
        Optional<PartnerLink> candidateLink = repository.findActiveLinkForCharacter(candidate.characterId());
        if (candidateLink.isEmpty() || candidateLink.get().id() != expectedLink.id()) {
            return Optional.of("The Partner registration changed before activation.");
        }
        if (runtimeAvailability.isOnline(candidate.characterId())) {
            return Optional.of("The registered Partner is currently online.");
        }
        if (runtimeAvailability.isLeased(candidate.characterId())) {
            return Optional.of("The Partner profile is already leased.");
        }
        if (!runtimeAvailability.canLoadCanonicalProfile(candidate.characterId())) {
            return Optional.of("The Partner's canonical profile could not be loaded.");
        }
        return Optional.empty();
    }

    public boolean isOnline(int characterId) {
        return runtimeAvailability.isOnline(characterId);
    }

    private String rejectionReason(PartnerRosterCandidate candidate,
                                   int accountId,
                                   int worldId,
                                   int currentCharacterId,
                                   Optional<PartnerLink> currentLink) {
        if (candidate.characterId() == currentCharacterId) {
            return "This is the currently controlled character.";
        }
        if (candidate.accountId() != accountId) {
            return "This character belongs to another account.";
        }
        if (candidate.worldId() != worldId) {
            return "This character belongs to another world.";
        }
        if (candidate.pendingWorldTransfer()) {
            return "This character has a pending world transfer.";
        }
        if (currentLink.isPresent()) {
            return currentLink.get().contains(candidate.characterId())
                    ? "This character is already your registered Partner."
                    : "Unregister your current Partner before selecting another.";
        }
        Optional<PartnerLink> candidateLink = repository.findActiveLinkForCharacter(candidate.characterId());
        if (candidateLink.isPresent()) {
            return "This character already participates in another Partner pair.";
        }
        if (runtimeAvailability.isOnline(candidate.characterId())) {
            return "This character is currently online.";
        }
        if (runtimeAvailability.isLeased(candidate.characterId())) {
            return "This character is leased to another active Partner session.";
        }
        if (!runtimeAvailability.canLoadCanonicalProfile(candidate.characterId())) {
            return "This character's canonical profile could not be loaded.";
        }
        return null;
    }

    public interface RuntimeAvailability {
        boolean isOnline(int characterId);

        boolean isLeased(int characterId);

        boolean canLoadCanonicalProfile(int characterId);
    }
}
