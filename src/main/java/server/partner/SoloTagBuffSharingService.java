package server.partner;

import client.Character;
import client.Skill;
import client.SkillFactory;
import client.BuffStat;
import config.AdventurerPartnerConfig;
import config.YamlConfig;
import net.server.PlayerBuffValueHolder;
import net.server.Server;
import server.StatEffect;
import tools.Pair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Consumer;

/** Item-gated, profile-directed skill-buff sharing for Solo Tag transitions. */
public final class SoloTagBuffSharingService {
    public static final SoloTagBuffSharingService INSTANCE =
            new SoloTagBuffSharingService(YamlConfig.config.adventurerPartner);

    private final PartnerMedalEffectService medalEffects;

    SoloTagBuffSharingService(AdventurerPartnerConfig config) {
        this(config, new PartnerMedalEffectService(config, PartnerRuntimeRegistry.global()));
    }

    SoloTagBuffSharingService(AdventurerPartnerConfig config,
                              PartnerMedalEffectService medalEffects) {
        this.medalEffects = medalEffects;
    }

    public SharingPlan capture(PartnerMode mode, Character humanActor,
                               Character partnerActorOrDormantProfile) {
        if (mode != PartnerMode.SOLO_TAG || !medalEffects.selfBuffSharingEnabled(mode)) {
            return SharingPlan.none();
        }
        return new SharingPlan(
                transferableBuffs(humanActor),
                transferableBuffs(partnerActorOrDormantProfile),
                medalEffects.selfBuffSkillCap(
                        humanActor, partnerActorOrDormantProfile, mode),
                medalEffects.selfBuffSkillCap(
                        partnerActorOrDormantProfile, humanActor, mode));
    }

    /**
     * Pre-registers source skills for buffs that can be shared during this Solo session.
     * Bond-item eligibility remains a switch-time buff decision, not a skill-safety gate.
     */
    public void prepareSessionSkills(PartnerMode mode,
                                     Character humanProfile,
                                     Character partnerProfile,
                                     Consumer<SkillGrant> skillGrant) {
        if (!medalEffects.selfBuffSharingEnabled(mode)) {
            return;
        }
        grantLearnedSelfBuffSkills(
                mode, humanProfile, partnerProfile, skillGrant);
        grantLearnedSelfBuffSkills(
                mode, partnerProfile, humanProfile, skillGrant);
        grantBuffSourceSkills(humanProfile, transferableBuffs(partnerProfile), skillGrant);
        grantBuffSourceSkills(partnerProfile, transferableBuffs(humanProfile), skillGrant);
    }

    public boolean isLearnedSelfBuffSkill(Skill skill, int level) {
        if (skill == null || level <= 0 || skill.getMaxLevel() <= 0) {
            return false;
        }
        StatEffect effect = skill.getEffect(Math.min(level, skill.getMaxLevel()));
        if (effect == null || !effect.isSkill() || !effect.isOverTime()
                || effect.getDuration() <= 0 || effect.getStatups().isEmpty()
                || effect.isPartyBuff() || (!skill.getAction() && skill.getSkillType() != 2)) {
            return false;
        }
        return effect.getStatups().stream().noneMatch(statup ->
                statup.getLeft() == BuffStat.SUMMON || statup.getLeft() == BuffStat.PUPPET);
    }

    /** Applies the pre-exchange source buffs to the post-exchange receiving profiles. */
    public void applyAfterExchange(SharingPlan plan, Character humanActor,
                                   Character partnerActorOrDormantProfile) {
        applyAfterExchange(plan, humanActor, partnerActorOrDormantProfile, ignored -> { });
    }

    public void applyAfterExchange(SharingPlan plan,
                                   Character humanActor,
                                   Character partnerActorOrDormantProfile,
                                   Consumer<SkillGrant> skillGrant) {
        if (!plan.enabled()) {
            return;
        }
        apply(humanActor, plan.humanProfileBuffs(), plan.partnerProfileSelfBuffCap(), skillGrant);
        apply(partnerActorOrDormantProfile, plan.partnerProfileBuffs(),
                plan.humanProfileSelfBuffCap(), skillGrant);
    }

    public boolean enabled() {
        return medalEffects.selfBuffSharingEnabled(PartnerMode.SOLO_TAG)
                || medalEffects.selfBuffSharingEnabled(PartnerMode.DOUBLE_PARTNER);
    }

    public int itemId() {
        return medalEffects.selfBuffBondItemId();
    }

    public boolean eligible(Character character) {
        return medalEffects.hasActiveSelfBuffBond(character);
    }

    public boolean ownsItem(Character character) {
        return medalEffects.ownsSelfBuffBond(character);
    }

    public String entitlementStatus(Character character) {
        return medalEffects.selfBuffBondStatus(character);
    }

    private static BuffBundle transferableBuffs(Character source) {
        List<BuffTransfer> partyBuffs = new ArrayList<>();
        List<BuffTransfer> selfBuffs = new ArrayList<>();
        Map<Skill, Character.SkillEntry> skills = source.getSkills();
        for (PlayerBuffValueHolder holder : source.getAllBuffs()) {
            StatEffect effect = holder.effect;
            if (effect != null && effect.isSkill() && !effect.getStatups().isEmpty()) {
                Map.Entry<Skill, Character.SkillEntry> sourceSkill = skills.entrySet().stream()
                        .filter(entry -> entry.getKey().getId() == effect.getBuffSourceId())
                        .findFirst()
                        .orElse(null);
                BuffTransfer transfer = new BuffTransfer(
                        new PlayerBuffValueHolder(holder.usedTime, effect),
                        sourceSkill == null ? null : sourceSkill.getKey(),
                        sourceSkill == null ? null : sourceSkill.getValue().persistenceCopy());
                List<BuffTransfer> destination = effect.isPartyBuff()
                        ? partyBuffs : selfBuffs;
                destination.add(transfer);
            }
        }
        partyBuffs.sort(buffPriority());
        selfBuffs.sort(buffPriority());
        return new BuffBundle(List.copyOf(partyBuffs), List.copyOf(selfBuffs));
    }

    private static Comparator<BuffTransfer> buffPriority() {
        return Comparator.comparingInt(
                        (BuffTransfer transfer) -> benefitScore(transfer.holder()))
                .thenComparingInt(transfer -> transfer.holder().effect.getBuffSourceId());
    }

    private static void grantBuffSourceSkills(Character recipient,
                                              BuffBundle sourceBuffs,
                                              Consumer<SkillGrant> skillGrant) {
        // Self-buff skills are preloaded through grantLearnedSelfBuffSkills at the
        // configured tier cap. Do not overwrite that cap with the donor's full level.
        for (BuffTransfer transfer : sourceBuffs.partyBuffs()) {
            if (transfer.skill() != null && transfer.skillState() != null) {
                skillGrant.accept(new SkillGrant(
                        recipient,
                        transfer.skill(),
                        transfer.skillState().skillevel,
                        transfer.skillState().masterlevel,
                        transfer.skillState().expiration));
            }
        }
    }

    private void grantLearnedSelfBuffSkills(PartnerMode mode,
                                            Character recipient,
                                            Character source,
                                            Consumer<SkillGrant> skillGrant) {
        int cap = medalEffects.configuredSelfBuffSkillCap(recipient, source, mode);
        if (cap <= 0) {
            return;
        }
        for (Map.Entry<Skill, Character.SkillEntry> entry : source.getSkills().entrySet()) {
            Character.SkillEntry state = entry.getValue();
            if (isLearnedSelfBuffSkill(entry.getKey(), state.skillevel)) {
                byte grantedLevel = (byte) Math.min(Byte.toUnsignedInt(state.skillevel), cap);
                skillGrant.accept(new SkillGrant(
                        recipient,
                        entry.getKey(),
                        grantedLevel,
                        state.masterlevel,
                        state.expiration));
            }
        }
    }

    private static int benefitScore(PlayerBuffValueHolder holder) {
        return holder.effect.getStatups().stream()
                .mapToInt(statup -> Math.abs(statup.getRight()))
                .sum();
    }

    private static void apply(Character recipient,
                              BuffBundle sourceBuffs,
                              int selfBuffCap,
                              Consumer<SkillGrant> skillGrant) {
        List<BuffTransfer> buffs = new ArrayList<>(sourceBuffs.partyBuffs());
        if (selfBuffCap > 0) {
            sourceBuffs.selfBuffs().stream()
                    .map(transfer -> capped(transfer, selfBuffCap))
                    .filter(java.util.Objects::nonNull)
                    .forEach(buffs::add);
        }
        buffs.sort(buffPriority());
        buffs = removeWeakerDuplicateSources(recipient, buffs);
        if (buffs.isEmpty()) {
            return;
        }
        long now = Server.getInstance().getCurrentTime();
        List<Pair<Long, PlayerBuffValueHolder>> timedBuffs = new ArrayList<>(buffs.size());
        for (BuffTransfer transfer : buffs) {
            if (transfer.skill() != null && transfer.skillState() != null) {
                skillGrant.accept(new SkillGrant(
                        recipient,
                        transfer.skill(),
                        transfer.skillState().skillevel,
                        transfer.skillState().masterlevel,
                        transfer.skillState().expiration));
            }
            int expectedSkillLevel = transfer.skillState() == null
                    ? 0 : Byte.toUnsignedInt(transfer.skillState().skillevel);
            if (hasStat(transfer.holder(), BuffStat.SHADOWPARTNER)
                    && (expectedSkillLevel <= 0
                    || PartnerSessionSkillService.clientVisibleSkillLevel(
                    recipient, transfer.holder().effect.getBuffSourceId())
                    != expectedSkillLevel)) {
                // Stock v83 resolves Shadow Partner duplicate-hit data from the
                // recipient's skill table. A missing or different level can make
                // the client resolve the wrong (or absent level/0) WZ node.
                continue;
            }
            timedBuffs.add(new Pair<>(now - transfer.holder().usedTime, transfer.holder()));
        }
        if (!timedBuffs.isEmpty()) {
            recipient.silentGiveBuffs(timedBuffs);
        }
    }

    private static List<BuffTransfer> removeWeakerDuplicateSources(
            Character recipient, List<BuffTransfer> incoming) {
        List<BuffTransfer> result = new ArrayList<>(incoming.size());
        List<PlayerBuffValueHolder> existing = recipient.getAllBuffs();
        boolean existingBooster = existing.stream()
                .anyMatch(holder -> hasStat(holder, BuffStat.BOOSTER));
        Set<Integer> incomingBoosterSources = incoming.stream()
                .filter(candidate -> hasStat(candidate.holder(), BuffStat.BOOSTER))
                .map(candidate -> candidate.holder().effect.getBuffSourceId())
                .collect(Collectors.toSet());
        boolean incomingBoosterConflict = incomingBoosterSources.size() > 1;
        for (BuffTransfer candidate : incoming) {
            if (hasStat(candidate.holder(), BuffStat.BOOSTER)
                    && (existingBooster || incomingBoosterConflict)) {
                continue;
            }
            PlayerBuffValueHolder sameSource = existing.stream()
                    .filter(holder -> holder.effect != null
                            && holder.effect.getBuffSourceId()
                            == candidate.holder().effect.getBuffSourceId())
                    .findFirst()
                    .orElse(null);
            if (sameSource == null || strongerOrLonger(candidate.holder(), sameSource)) {
                result.add(candidate);
            }
        }
        return result;
    }

    private static boolean hasStat(PlayerBuffValueHolder holder, BuffStat stat) {
        return holder != null && holder.effect != null
                && holder.effect.getStatups().stream()
                .anyMatch(statup -> statup.getLeft() == stat);
    }

    private static boolean strongerOrLonger(PlayerBuffValueHolder candidate,
                                             PlayerBuffValueHolder existing) {
        int candidateScore = benefitScore(candidate);
        int existingScore = benefitScore(existing);
        return candidateScore > existingScore
                || (candidateScore == existingScore && candidate.usedTime < existing.usedTime);
    }

    private static BuffTransfer capped(BuffTransfer transfer, int cap) {
        int sourceLevel = transfer.skillState() == null
                ? cap : Byte.toUnsignedInt(transfer.skillState().skillevel);
        Skill skill = transfer.skill() != null
                ? transfer.skill() : SkillFactory.getSkill(transfer.holder().effect.getBuffSourceId());
        if (skill == null) {
            return transfer;
        }
        int skillMaxLevel = skill.getMaxLevel();
        int level = Math.min(sourceLevel, cap);
        if (skillMaxLevel > 0) {
            level = Math.min(level, skillMaxLevel);
        }
        if (level <= 0) {
            return null;
        }
        Character.SkillEntry sourceState = transfer.skillState();
        Character.SkillEntry cappedState = sourceState == null
                ? null
                : new Character.SkillEntry(
                (byte) level, sourceState.masterlevel, sourceState.expiration);
        StatEffect cappedEffect = skillMaxLevel > 0
                ? skill.getEffect(level) : transfer.holder().effect;
        return new BuffTransfer(
                new PlayerBuffValueHolder(transfer.holder().usedTime, cappedEffect),
                skill,
                cappedState);
    }

    private record BuffTransfer(PlayerBuffValueHolder holder,
                                Skill skill,
                                Character.SkillEntry skillState) {
    }

    private record BuffBundle(List<BuffTransfer> partyBuffs,
                              List<BuffTransfer> selfBuffs) {
        private static BuffBundle none() {
            return new BuffBundle(List.of(), List.of());
        }

        private boolean isEmpty() {
            return partyBuffs.isEmpty() && selfBuffs.isEmpty();
        }
    }

    record SharingPlan(BuffBundle humanProfileBuffs,
                       BuffBundle partnerProfileBuffs,
                       int humanProfileSelfBuffCap,
                       int partnerProfileSelfBuffCap) {
        static SharingPlan none() {
            return new SharingPlan(BuffBundle.none(), BuffBundle.none(), 0, 0);
        }

        private boolean enabled() {
            return humanProfileSelfBuffCap > 0 || partnerProfileSelfBuffCap > 0
                    || !humanProfileBuffs.isEmpty() || !partnerProfileBuffs.isEmpty();
        }
    }

    record SkillGrant(Character recipient,
                      Skill skill,
                      byte level,
                      int masterLevel,
                      long expiration) {
    }
}
