package server.partner;

import client.BuffStat;
import client.Character;
import client.Disease;
import client.SkillMacro;
import client.Stat;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.ModifyInventory;
import client.keybind.KeyBinding;
import client.keybind.QuickslotBinding;
import config.YamlConfig;
import net.packet.Packet;
import net.server.PlayerCoolDownValueHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.PacketCreator;
import tools.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** Cosmic packet adapter for deterministic local and public profile refresh. */
public final class CosmicProfilePresentationService implements ProfilePresentationService {
    private static final Logger log = LoggerFactory.getLogger(CosmicProfilePresentationService.class);
    public static final CosmicProfilePresentationService INSTANCE = new CosmicProfilePresentationService();
    private static final List<InventoryType> DISPLAYED_INVENTORIES = List.of(
            InventoryType.EQUIPPED,
            InventoryType.EQUIP,
            InventoryType.USE,
            InventoryType.SETUP,
            InventoryType.ETC,
            InventoryType.CASH);
    private final ConcurrentHashMap<Integer, PreparedStaticPresentation> preparedByProfileOwner =
            new ConcurrentHashMap<>();

    private CosmicProfilePresentationService() {
    }

    @Override
    public void prepare(Character firstProfile, Character secondProfile) {
        prepared(firstProfile);
        prepared(secondProfile);
    }

    @Override
    public void discardPrepared(Character firstProfile, Character secondProfile) {
        discardPrepared(firstProfile);
        discardPrepared(secondProfile);
    }

    private void discardPrepared(Character profile) {
        if (profile != null) {
            preparedByProfileOwner.remove(profile.getProfileOwnerCharacterId());
        }
    }

    @Override
    public RefreshMetrics refresh(Character humanActor,
                                  Character partnerActorOrDormantProfile,
                                  PartnerMode mode,
                                  Character.ProfileExchangeResult exchangeResult) {
        long startedNs = System.nanoTime();
        PacketCounter counter = new PacketCounter(humanActor);
        PreparedStaticPresentation oldPresentation = prepared(partnerActorOrDormantProfile);
        PreparedStaticPresentation newPresentation = prepared(humanActor);

        cancelOldLocalPresentation(counter, partnerActorOrDormantProfile);
        counter.send("stats", PacketCreator.updatePlayerStats(fullStats(humanActor), false, humanActor));
        refreshSkills(counter, oldPresentation, newPresentation);
        counter.send("bindings", PacketCreator.getKeymap(newPresentation.keymap()));
        counter.send("bindings", PacketCreator.QuickslotMappedInit(newPresentation.quickslots()));
        counter.send("bindings", PacketCreator.getMacros(newPresentation.macros()));
        refreshInventory(counter, oldPresentation, newPresentation);
        refreshLocalPets(counter, partnerActorOrDormantProfile, humanActor);
        refreshLocalBuffs(counter, humanActor);
        refreshLocalDiseases(counter, humanActor);
        refreshLocalCooldowns(counter, humanActor);

        if (YamlConfig.config.adventurerPartner.PUBLIC_PRESENTATION) {
            refreshPublicActor(counter, humanActor, partnerActorOrDormantProfile);
            if (mode == PartnerMode.DOUBLE_PARTNER) {
                refreshPublicActor(counter, partnerActorOrDormantProfile, humanActor);
            }
        }
        refreshSwitchEffects(counter, humanActor, partnerActorOrDormantProfile, mode);
        counter.send("actions", PacketCreator.enableActions());
        long duration = System.nanoTime() - startedNs;
        log.info("partner_presentation packets={} bytes={} durationNs={} categories={}",
                counter.packetCount, counter.packetBytes, duration, counter.categoryTotals);
        return new RefreshMetrics(counter.packetCount, counter.packetBytes, duration);
    }

    private static void cancelOldLocalPresentation(PacketCounter counter, Character oldProfileHolder) {
        List<BuffStat> oldBuffs = oldProfileHolder.getActiveBuffStatsSnapshot();
        if (!oldBuffs.isEmpty()) {
            counter.send("cancel", PacketCreator.cancelBuff(oldBuffs));
        }
        for (PlayerCoolDownValueHolder cooldown : oldProfileHolder.getAllCooldowns()) {
            counter.send("cancel", PacketCreator.skillCooldown(cooldown.skillId, 0));
        }
        for (Character.DiseasePresentationSnapshot disease
                : oldProfileHolder.getDiseasePresentationSnapshots()) {
            counter.send("cancel", PacketCreator.cancelDebuff(disease.disease().getValue()));
        }
        for (byte slot = 0; slot < 3; slot++) {
            if (oldProfileHolder.getPet(slot) != null) {
                counter.send("cancel", PacketCreator.showPetAtIndex(
                        counter.character, slot, oldProfileHolder.getPet(slot), true, false));
            }
        }
    }

    private static List<Pair<Stat, Integer>> fullStats(Character character) {
        List<Pair<Stat, Integer>> stats = new ArrayList<>(20);
        stats.add(new Pair<>(Stat.SKIN, character.getSkinColor().getId()));
        stats.add(new Pair<>(Stat.FACE, character.getFace()));
        stats.add(new Pair<>(Stat.HAIR, character.getHair()));
        stats.add(new Pair<>(Stat.LEVEL, character.getLevel()));
        stats.add(new Pair<>(Stat.JOB, character.getJob().getId()));
        stats.add(new Pair<>(Stat.STR, character.getStr()));
        stats.add(new Pair<>(Stat.DEX, character.getDex()));
        stats.add(new Pair<>(Stat.INT, character.getInt()));
        stats.add(new Pair<>(Stat.LUK, character.getLuk()));
        stats.add(new Pair<>(Stat.HP, character.getHp()));
        stats.add(new Pair<>(Stat.MAXHP, character.getClientMaxHp()));
        stats.add(new Pair<>(Stat.MP, character.getMp()));
        stats.add(new Pair<>(Stat.MAXMP, character.getClientMaxMp()));
        stats.add(new Pair<>(Stat.AVAILABLEAP, character.getRemainingAp()));
        stats.add(new Pair<>(Stat.AVAILABLESP, character.getRemainingSp()));
        stats.add(new Pair<>(Stat.EXP, character.getExp()));
        stats.add(new Pair<>(Stat.FAME, character.getFame()));
        stats.add(new Pair<>(Stat.MESO, character.getMeso()));
        stats.add(new Pair<>(Stat.GACHAEXP, character.getGachaExp()));
        return stats;
    }

    private void refreshSkills(PacketCounter counter,
                               PreparedStaticPresentation oldProfile,
                               PreparedStaticPresentation newProfile) {
        Map<Integer, SkillSnapshot> oldSkillsById = skillsById(oldProfile.skills());
        Map<Integer, SkillSnapshot> newSkillsById = skillsById(newProfile.skills());

        for (SkillSnapshot oldSkill : oldProfile.skills()) {
            if (!newSkillsById.containsKey(oldSkill.skillId())) {
                counter.send("skills", PacketCreator.updateSkill(
                        oldSkill.skillId(), -1, 0, -1));
            }
        }
        for (SkillSnapshot newSkill : newProfile.skills()) {
            if (!newSkill.equals(oldSkillsById.get(newSkill.skillId()))) {
                counter.send("skills", PacketCreator.updateSkill(
                        newSkill.skillId(), newSkill.level(),
                        newSkill.masterLevel(), newSkill.expiration()));
            }
        }
    }

    private static Map<Integer, SkillSnapshot> skillsById(List<SkillSnapshot> skills) {
        Map<Integer, SkillSnapshot> result = new LinkedHashMap<>();
        for (SkillSnapshot skill : skills) {
            result.put(skill.skillId(), skill);
        }
        return result;
    }

    private static void refreshInventory(PacketCounter counter,
                                         PreparedStaticPresentation oldProfile,
                                         PreparedStaticPresentation newProfile) {
        for (List<ModifyInventory> chunk : oldProfile.inventoryRemovals()) {
            counter.send("inventory", PacketCreator.modifyInventory(false, chunk));
        }

        for (InventoryType type : List.of(
                InventoryType.EQUIP, InventoryType.USE, InventoryType.SETUP, InventoryType.ETC)) {
            counter.send("inventory", PacketCreator.updateInventorySlotLimit(
                    type.getType(), newProfile.slotLimits().get(type)));
        }

        for (List<ModifyInventory> chunk : newProfile.inventoryAdditions()) {
            counter.send("inventory", PacketCreator.modifyInventory(false, chunk));
        }
    }

    private PreparedStaticPresentation prepared(Character profile) {
        int ownerId = profile.getProfileOwnerCharacterId();
        long version = profile.getProfileVersion();
        return preparedByProfileOwner.compute(ownerId, (ignored, existing) ->
                existing != null && existing.profileVersion() == version
                        ? existing
                        : PreparedStaticPresentation.capture(profile, version));
    }

    /*
     * Stock v83 exposes quests and Monster Book cards only through gameplay
     * delta packets. Replaying those packets announces every quest/card and
     * opens the Quest Helper. The authoritative profile state still exchanges
     * in memory and persists canonically; deliberately omit the noisy client
     * deltas until a silent client protocol is available.
     */

    private static void refreshLocalPets(PacketCounter counter, Character oldProfile, Character newProfile) {
        for (byte slot = 0; slot < 3; slot++) {
            if (newProfile.getPet(slot) != null) {
                counter.send("pets", PacketCreator.showPetAtIndex(
                        counter.character, slot, newProfile.getPet(slot), false, false));
            }
        }
        counter.send("pets", PacketCreator.petStatUpdate(newProfile));
    }

    private static void refreshLocalBuffs(PacketCounter counter, Character newProfile) {
        for (Character.BuffPresentationSnapshot buff : newProfile.getBuffPresentationSnapshots()) {
            counter.send("buffs", PacketCreator.giveBuff(
                    buff.sourceId(), buff.remainingDurationMs(), buff.statups()));
        }
    }

    private static void refreshLocalCooldowns(PacketCounter counter, Character newProfile) {
        long now = System.currentTimeMillis();
        for (PlayerCoolDownValueHolder cooldown : newProfile.getAllCooldowns()) {
            int remainingSeconds = ProfileEffectTiming.remainingDurationSecondsCeiling(
                    cooldown.startTime, cooldown.length, now);
            counter.send("cooldowns", PacketCreator.skillCooldown(cooldown.skillId, remainingSeconds));
        }
    }

    private static void refreshLocalDiseases(PacketCounter counter, Character newProfile) {
        for (Character.DiseasePresentationSnapshot disease
                : newProfile.getDiseasePresentationSnapshots()) {
            List<Pair<Disease, Integer>> statups = List.of(
                    new Pair<>(disease.disease(), disease.skill().getX()));
            counter.send("diseases", PacketCreator.giveDebuff(
                    statups, disease.skill(), disease.remainingDurationMs()));
        }
    }

    private static void refreshPublicActor(PacketCounter counter,
                                           Character actor,
                                           Character oldProfileHolder) {
        if (actor.getMap() == null) {
            return;
        }
        var lookMetrics = actor.getMap().broadcastUpdateCharLookMessage(actor, actor);
        counter.record("public-look", lookMetrics.packetCount(), lookMetrics.packetBytes());
        List<BuffStat> oldBuffs = oldProfileHolder.getActiveBuffStatsSnapshot();
        if (!oldBuffs.isEmpty()) {
            Packet packet = PacketCreator.cancelForeignBuff(actor.getId(), oldBuffs);
            counter.record("public", packet);
            actor.getMap().broadcastMessage(actor, packet, false);
        }
        for (Character.BuffPresentationSnapshot buff : actor.getBuffPresentationSnapshots()) {
            Packet packet = PacketCreator.giveForeignBuff(actor.getId(), buff.statups());
            counter.record("public", packet);
            actor.getMap().broadcastMessage(actor, packet, false);
        }
        for (Character.DiseasePresentationSnapshot disease
                : oldProfileHolder.getDiseasePresentationSnapshots()) {
            Packet packet = disease.disease() == Disease.SLOW
                    ? PacketCreator.cancelForeignSlowDebuff(actor.getId())
                    : PacketCreator.cancelForeignDebuff(actor.getId(), disease.disease().getValue());
            counter.record("public", packet);
            actor.getMap().broadcastMessage(actor, packet, false);
        }
        for (Character.DiseasePresentationSnapshot disease : actor.getDiseasePresentationSnapshots()) {
            List<Pair<Disease, Integer>> statups = List.of(
                    new Pair<>(disease.disease(), disease.skill().getX()));
            Packet packet = disease.disease() == Disease.SLOW
                    ? PacketCreator.giveForeignSlowDebuff(actor.getId(), statups, disease.skill())
                    : PacketCreator.giveForeignDebuff(actor.getId(), statups, disease.skill());
            counter.record("public", packet);
            actor.getMap().broadcastMessage(actor, packet, false);
        }
        for (byte slot = 0; slot < 3; slot++) {
            if (oldProfileHolder.getPet(slot) != null) {
                Packet packet = PacketCreator.showPetAtIndex(
                        actor, slot, oldProfileHolder.getPet(slot), true, false);
                counter.record("public", packet);
                actor.getMap().broadcastMessage(actor, packet, false);
            }
            if (actor.getPet(slot) != null) {
                Packet packet = PacketCreator.showPetAtIndex(
                        actor, slot, actor.getPet(slot), false, false);
                counter.record("public", packet);
                actor.getMap().broadcastMessage(actor, packet, false);
            }
        }
    }

    private static void refreshSwitchEffects(PacketCounter counter,
                                             Character humanActor,
                                             Character partnerActorOrDormantProfile,
                                             PartnerMode mode) {
        counter.send("switch-effect", PacketCreator.showSpecialEffect(8));
        broadcastSwitchEffect(counter, humanActor);
        if (mode == PartnerMode.DOUBLE_PARTNER) {
            broadcastSwitchEffect(counter, partnerActorOrDormantProfile);
        }
    }

    private static void broadcastSwitchEffect(PacketCounter counter, Character actor) {
        if (actor == null || actor.getMap() == null) {
            return;
        }
        Packet packet = PacketCreator.showForeignEffect(actor.getId(), 8);
        counter.record("switch-effect", packet);
        actor.getMap().broadcastMessage(actor, packet, false);
    }

    private static final class PacketCounter {
        private final Character character;
        private int packetCount;
        private long packetBytes;
        private final Map<String, CategoryTotal> categoryTotals = new LinkedHashMap<>();

        private PacketCounter(Character character) {
            this.character = character;
        }

        private void send(String category, Packet packet) {
            record(category, packet);
            character.sendPacket(packet);
        }

        private void record(String category, Packet packet) {
            record(category, 1, packet.getBytes().length);
        }

        private void record(String category, int packets, long bytes) {
            packetCount += packets;
            packetBytes += bytes;
            categoryTotals.computeIfAbsent(category, ignored -> new CategoryTotal())
                    .add(packets, bytes);
        }
    }

    private record SkillSnapshot(int skillId, byte level, int masterLevel, long expiration) {
    }

    private record PreparedStaticPresentation(
            long profileVersion,
            List<SkillSnapshot> skills,
            Map<Integer, KeyBinding> keymap,
            QuickslotBinding quickslots,
            SkillMacro[] macros,
            Map<InventoryType, Byte> slotLimits,
            List<List<ModifyInventory>> inventoryRemovals,
            List<List<ModifyInventory>> inventoryAdditions) {
        private static PreparedStaticPresentation capture(Character profile, long profileVersion) {
            List<SkillSnapshot> skills = profile.getSkills().entrySet().stream()
                    .map(entry -> new SkillSnapshot(
                            entry.getKey().getId(), entry.getValue().skillevel,
                            entry.getValue().masterlevel, entry.getValue().expiration))
                    .toList();
            Map<Integer, KeyBinding> keymap = Map.copyOf(new LinkedHashMap<>(profile.getKeymap()));
            QuickslotBinding quickslots = new QuickslotBinding(
                    profile.getQuickslotBindingForPresentation().GetKeybindings());
            SkillMacro[] macros = new SkillMacro[profile.getMacros().length];
            SkillMacro[] sourceMacros = profile.getMacros();
            for (int index = 0; index < sourceMacros.length; index++) {
                SkillMacro macro = sourceMacros[index];
                if (macro != null) {
                    macros[index] = new SkillMacro(
                            macro.getSkill1(), macro.getSkill2(), macro.getSkill3(),
                            macro.getName(), macro.getShout(), macro.getPosition());
                }
            }

            Map<InventoryType, Byte> slotLimits = new LinkedHashMap<>();
            List<ModifyInventory> removals = new ArrayList<>();
            List<ModifyInventory> additions = new ArrayList<>();
            for (InventoryType type : DISPLAYED_INVENTORIES) {
                slotLimits.put(type, profile.getInventory(type).getSlotLimit());
                for (Item item : profile.getInventory(type).list()) {
                    Item snapshot = item.copy();
                    removals.add(new ModifyInventory(3, snapshot));
                    additions.add(new ModifyInventory(0, snapshot));
                }
            }
            return new PreparedStaticPresentation(
                    profileVersion,
                    skills,
                    keymap,
                    quickslots,
                    macros,
                    Map.copyOf(slotLimits),
                    InventoryPacketChunker.chunk(removals),
                    InventoryPacketChunker.chunk(additions));
        }
    }

    private static final class CategoryTotal {
        private int packets;
        private long bytes;

        private void add(int packetCount, long packetBytes) {
            packets += packetCount;
            bytes += packetBytes;
        }

        @Override
        public String toString() {
            return packets + "/" + bytes;
        }
    }
}
