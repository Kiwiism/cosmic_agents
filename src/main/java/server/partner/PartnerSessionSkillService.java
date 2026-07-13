package server.partner;

import client.Character;
import client.Skill;
import client.SkillFactory;
import constants.game.GameConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.PacketCreator;

import java.util.ArrayList;
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

    public void prepareUnion(long sessionId, Character firstProfile, Character secondProfile) {
        Map<Integer, Map.Entry<Skill, Character.SkillEntry>> firstSkills = skillsById(firstProfile);
        Map<Integer, Map.Entry<Skill, Character.SkillEntry>> secondSkills = skillsById(secondProfile);
        List<PacketCreator.SkillUpdate> firstUpdates = new ArrayList<>();
        List<PacketCreator.SkillUpdate> secondUpdates = new ArrayList<>();
        grantMissingCrossJobSkills(
                sessionId, firstProfile, secondSkills, firstSkills, firstUpdates);
        grantMissingCrossJobSkills(
                sessionId, secondProfile, firstSkills, secondSkills, secondUpdates);
        announceSkillBatch(firstProfile, firstUpdates);
        announceSkillBatch(secondProfile, secondUpdates);
    }

    public void grant(long sessionId, SoloTagBuffSharingService.SkillGrant request) {
        grant(sessionId, request, true);
    }

    private boolean grant(long sessionId,
                          SoloTagBuffSharingService.SkillGrant request,
                          boolean announce) {
        Character recipient = request.recipient();
        Skill skill = request.skill();
        Character.SkillEntry current = recipient.getSkills().entrySet().stream()
                .filter(entry -> entry.getKey().getId() == skill.getId())
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        if (sameState(current, request)) {
            return false;
        }

        AdventurerPartnerRepository.CharacterSkillState original = current == null
                ? null
                : new AdventurerPartnerRepository.CharacterSkillState(
                        current.skillevel, current.masterlevel, current.expiration);
        PartnerSessionSkillGrant recorded = repository.grantTemporarySkill(
                sessionId,
                recipient.getProfileOwnerCharacterId(),
                skill.getId(),
                request.level(),
                request.masterLevel(),
                request.expiration(),
                original);
        if (announce) {
            recipient.applyPartnerSessionSkill(
                    recipient.getProfileOwnerCharacterId(),
                    skill,
                    request.level(),
                    request.masterLevel(),
                    request.expiration());
        } else {
            recipient.applyPartnerSessionSkill(
                    recipient.getProfileOwnerCharacterId(),
                    skill,
                    request.level(),
                    request.masterLevel(),
                    request.expiration(),
                    false);
        }
        recipient.markPartnerSessionSkillBorrowed(
                recipient.getProfileOwnerCharacterId(), skill.getId(), !recorded.hadOriginalSkill());
        log.info("partner_temporary_skill granted session={} character={} skill={} level={}",
                sessionId, recipient.getProfileOwnerCharacterId(), skill.getId(), request.level());
        return true;
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

    public boolean synchronizeUnionSkill(long sessionId,
                                         Character source,
                                         Character recipient,
                                         Skill skill) {
        Character.SkillEntry sourceState = source.getSkills().entrySet().stream()
                .filter(entry -> entry.getKey().getId() == skill.getId())
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        if (sourceState == null) {
            return false;
        }
        boolean borrowed = recipient.isPartnerSessionBorrowedSkill(skill.getId());
        boolean recipientHasSkill = recipient.getSkills().keySet().stream()
                .anyMatch(current -> current.getId() == skill.getId());
        if (!borrowed && (recipientHasSkill
                || skill.isBeginnerSkill()
                || GameConstants.isGMSkills(skill.getId())
                || GameConstants.isInJobTree(skill.getId(), recipient.getJob().getId()))) {
            return false;
        }
        grant(sessionId, new SoloTagBuffSharingService.SkillGrant(
                recipient,
                skill,
                sourceState.skillevel,
                sourceState.masterlevel,
                sourceState.expiration));
        return true;
    }

    private void grantMissingCrossJobSkills(
            long sessionId,
            Character recipient,
            Map<Integer, Map.Entry<Skill, Character.SkillEntry>> sourceSkills,
            Map<Integer, Map.Entry<Skill, Character.SkillEntry>> recipientSkills,
            List<PacketCreator.SkillUpdate> updates) {
        for (Map.Entry<Integer, Map.Entry<Skill, Character.SkillEntry>> candidate
                : sourceSkills.entrySet()) {
            Skill skill = candidate.getValue().getKey();
            if (recipientSkills.containsKey(candidate.getKey())
                    || skill.isBeginnerSkill()
                    || GameConstants.isGMSkills(skill.getId())
                    || GameConstants.isInJobTree(skill.getId(), recipient.getJob().getId())) {
                continue;
            }
            Character.SkillEntry state = candidate.getValue().getValue();
            if (grant(sessionId, new SoloTagBuffSharingService.SkillGrant(
                    recipient, skill, state.skillevel, state.masterlevel, state.expiration), false)) {
                updates.add(new PacketCreator.SkillUpdate(
                        skill.getId(), state.skillevel, state.masterlevel, state.expiration));
            }
        }
    }

    private static void announceSkillBatch(
            Character recipient, List<PacketCreator.SkillUpdate> updates) {
        if (!updates.isEmpty()) {
            recipient.sendPacket(PacketCreator.updateSkills(updates));
        }
    }

    private static Map<Integer, Map.Entry<Skill, Character.SkillEntry>> skillsById(
            Character character) {
        Map<Integer, Map.Entry<Skill, Character.SkillEntry>> result = new LinkedHashMap<>();
        for (Map.Entry<Skill, Character.SkillEntry> entry : character.getSkills().entrySet()) {
            result.put(entry.getKey().getId(), entry);
        }
        return result;
    }

    private static boolean sameState(Character.SkillEntry current,
                                     SoloTagBuffSharingService.SkillGrant request) {
        return current != null
                && current.skillevel == request.level()
                && current.masterlevel == request.masterLevel()
                && current.expiration == request.expiration();
    }
}
