package server.partner;

import client.Character;
import client.Skill;
import client.SkillFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Installs and restores real, database-backed skills needed by borrowed Solo Tag buffs. */
public final class PartnerSessionSkillService {
    private static final Logger log = LoggerFactory.getLogger(PartnerSessionSkillService.class);

    private final AdventurerPartnerRepository repository;

    PartnerSessionSkillService(AdventurerPartnerRepository repository) {
        this.repository = repository;
    }

    public void grant(long sessionId, SoloTagBuffSharingService.SkillGrant request) {
        Character recipient = request.recipient();
        Skill skill = request.skill();
        Character.SkillEntry current = recipient.getSkills().entrySet().stream()
                .filter(entry -> entry.getKey().getId() == skill.getId())
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        if (sameState(current, request)) {
            return;
        }

        AdventurerPartnerRepository.CharacterSkillState original = current == null
                ? null
                : new AdventurerPartnerRepository.CharacterSkillState(
                        current.skillevel, current.masterlevel, current.expiration);
        repository.grantTemporarySkill(
                sessionId,
                recipient.getProfileOwnerCharacterId(),
                skill.getId(),
                request.level(),
                request.masterLevel(),
                request.expiration(),
                original);
        recipient.applyPartnerSessionSkill(
                recipient.getProfileOwnerCharacterId(),
                skill,
                request.level(),
                request.masterLevel(),
                request.expiration());
        log.info("partner_temporary_skill granted session={} character={} skill={} level={}",
                sessionId, recipient.getProfileOwnerCharacterId(), skill.getId(), request.level());
    }

    public void restore(long sessionId, Character firstProfile, Character secondProfile) {
        List<PartnerSessionSkillGrant> grants = repository.findTemporarySkills(sessionId);
        if (grants == null || grants.isEmpty()) {
            return;
        }
        Map<Integer, Character> holders = new LinkedHashMap<>();
        holders.put(firstProfile.getProfileOwnerCharacterId(), firstProfile);
        holders.put(secondProfile.getProfileOwnerCharacterId(), secondProfile);
        for (PartnerSessionSkillGrant grant : grants) {
            if (!holders.containsKey(grant.characterId())) {
                throw new IllegalStateException(
                        "Temporary Partner skill profile is not canonically attached for release");
            }
        }

        repository.restoreTemporarySkills(sessionId);
        for (PartnerSessionSkillGrant grant : grants) {
            Character holder = holders.get(grant.characterId());
            Skill skill = holder.getSkills().keySet().stream()
                    .filter(current -> current.getId() == grant.skillId())
                    .findFirst()
                    .orElseGet(() -> {
                        Skill canonical = SkillFactory.getSkill(grant.skillId());
                        return canonical == null ? new Skill(grant.skillId()) : canonical;
                    });
            holder.cancelPartnerBuffFromSource(grant.skillId());
            Character.SkillEntry original = grant.hadOriginalSkill()
                    ? new Character.SkillEntry(
                            grant.originalSkillLevel().byteValue(),
                            grant.originalMasterLevel(),
                            grant.originalExpiration())
                    : null;
            holder.restorePartnerSessionSkill(grant.characterId(), skill, original);
            log.info("partner_temporary_skill restored session={} character={} skill={} hadOriginal={}",
                    sessionId, grant.characterId(), grant.skillId(), grant.hadOriginalSkill());
        }
    }

    private static boolean sameState(Character.SkillEntry current,
                                     SoloTagBuffSharingService.SkillGrant request) {
        return current != null
                && current.skillevel == request.level()
                && current.masterlevel == request.masterLevel()
                && current.expiration == request.expiration();
    }
}
