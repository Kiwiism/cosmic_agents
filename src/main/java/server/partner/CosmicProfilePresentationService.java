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
import config.AdventurerPartnerConfig;
import config.YamlConfig;
import net.packet.Packet;
import net.server.PlayerCoolDownValueHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.PacketCreator;
import tools.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
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
        AdventurerPartnerConfig config = YamlConfig.config.adventurerPartner;

        cancelOldLocalPresentation(counter, partnerActorOrDormantProfile);
        counter.send("stats", PacketCreator.updatePlayerStats(
                fullStats(humanActor), false, humanActor));
        refreshSkills(counter, oldPresentation, newPresentation);
        refreshBindings(counter, oldPresentation, newPresentation);
        refreshInventory(counter, oldPresentation, newPresentation);
        refreshLocalPets(counter, partnerActorOrDormantProfile, humanActor);
        refreshLocalBuffs(counter, humanActor);
        refreshLocalDiseases(counter, humanActor);
        refreshLocalCooldowns(counter, humanActor);

        refreshPublicActor(counter, humanActor, partnerActorOrDormantProfile);
        if (mode == PartnerMode.DOUBLE_PARTNER) {
            refreshPublicActor(counter, partnerActorOrDormantProfile, humanActor);
        }
        refreshSwitchEffects(counter, humanActor, partnerActorOrDormantProfile, mode, config);
        counter.send("actions", PacketCreator.enableActions());
        counter.flush();
        long duration = System.nanoTime() - startedNs;
        log.info("partner_presentation packets={} bytes={} durationNs={} categories={}",
                counter.packetCount, counter.packetBytes, duration, counter.categoryTotals);
        return new RefreshMetrics(counter.packetCount, counter.packetBytes, duration);
    }

    private static void cancelOldLocalPresentation(PacketCounter counter,
                                                   Character oldProfileHolder) {
        List<BuffStat> oldBuffs = oldProfileHolder.getActiveBuffStatsSnapshot();
        if (!oldBuffs.isEmpty()) {
            counter.send("cancel-buffs", PacketCreator.cancelBuff(oldBuffs));
        }
        for (PlayerCoolDownValueHolder cooldown : oldProfileHolder.getAllCooldowns()) {
            counter.send("cancel-cooldowns", PacketCreator.skillCooldown(cooldown.skillId, 0));
        }
        for (Character.DiseasePresentationSnapshot disease
                : oldProfileHolder.getDiseasePresentationSnapshots()) {
            counter.send("cancel-diseases", PacketCreator.cancelDebuff(disease.disease().getValue()));
        }
        for (byte slot = 0; slot < 3; slot++) {
            if (oldProfileHolder.getPet(slot) != null) {
                counter.send("cancel-pets", PacketCreator.showPetAtIndex(
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
        List<PacketCreator.SkillUpdate> updates = new ArrayList<>();

        for (SkillSnapshot oldSkill : oldProfile.skills()) {
            if (!newSkillsById.containsKey(oldSkill.skillId())) {
                updates.add(new PacketCreator.SkillUpdate(oldSkill.skillId(), -1, 0, -1));
            }
        }
        for (SkillSnapshot newSkill : newProfile.skills()) {
            if (!newSkill.equals(oldSkillsById.get(newSkill.skillId()))) {
                updates.add(new PacketCreator.SkillUpdate(
                        newSkill.skillId(), newSkill.level(),
                        newSkill.masterLevel(), newSkill.expiration()));
            }
        }
        if (!updates.isEmpty()) {
            counter.send("skills", PacketCreator.updateSkills(updates));
        }
    }

    private static Map<Integer, SkillSnapshot> skillsById(List<SkillSnapshot> skills) {
        Map<Integer, SkillSnapshot> result = new LinkedHashMap<>();
        for (SkillSnapshot skill : skills) {
            result.put(skill.skillId(), skill);
        }
        return result;
    }

    private static void refreshBindings(PacketCounter counter,
                                        PreparedStaticPresentation oldProfile,
                                        PreparedStaticPresentation newProfile) {
        if (!sameKeymap(oldProfile.keymap(), newProfile.keymap())) {
            counter.send("bindings", PacketCreator.getKeymap(newProfile.keymap()));
        }
        if (!Arrays.equals(
                oldProfile.quickslots().GetKeybindings(),
                newProfile.quickslots().GetKeybindings())) {
            counter.send("bindings", PacketCreator.QuickslotMappedInit(newProfile.quickslots()));
        }
        if (!sameMacros(oldProfile.macros(), newProfile.macros())) {
            counter.send("bindings", PacketCreator.getMacros(newProfile.macros()));
        }
    }

    private static boolean sameKeymap(Map<Integer, KeyBinding> first,
                                      Map<Integer, KeyBinding> second) {
        if (!first.keySet().equals(second.keySet())) {
            return false;
        }
        for (Map.Entry<Integer, KeyBinding> entry : first.entrySet()) {
            KeyBinding other = second.get(entry.getKey());
            if (other == null
                    || entry.getValue().getType() != other.getType()
                    || entry.getValue().getAction() != other.getAction()) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameMacros(SkillMacro[] first, SkillMacro[] second) {
        if (first.length != second.length) {
            return false;
        }
        for (int index = 0; index < first.length; index++) {
            SkillMacro left = first[index];
            SkillMacro right = second[index];
            if (left == right) {
                continue;
            }
            if (left == null || right == null
                    || left.getSkill1() != right.getSkill1()
                    || left.getSkill2() != right.getSkill2()
                    || left.getSkill3() != right.getSkill3()
                    || !Objects.equals(left.getName(), right.getName())
                    || left.getShout() != right.getShout()
                    || left.getPosition() != right.getPosition()) {
                return false;
            }
        }
        return true;
    }

    private static void refreshInventory(PacketCounter counter,
                                         PreparedStaticPresentation oldProfile,
                                         PreparedStaticPresentation newProfile) {
        sendInventoryItems(counter, oldProfile.inventoryItems(), 3);

        for (InventoryType type : List.of(
                InventoryType.EQUIP, InventoryType.USE, InventoryType.SETUP, InventoryType.ETC)) {
            if (!oldProfile.slotLimits().get(type).equals(newProfile.slotLimits().get(type))) {
                counter.send("inventory", PacketCreator.updateInventorySlotLimit(
                        type.getType(), newProfile.slotLimits().get(type)));
            }
        }

        sendInventoryItems(counter, newProfile.inventoryItems(), 0);
    }

    private static void sendInventoryItems(PacketCounter counter,
                                           List<Item> items,
                                           int mode) {
        for (List<ModifyInventory> chunk
                : InventoryPacketChunker.chunk(inventoryOperations(items, mode))) {
            counter.send("inventory", PacketCreator.modifyInventory(false, chunk));
        }
    }

    static List<ModifyInventory> inventoryOperations(List<Item> items,
                                                     int mode) {
        return items.stream()
                .map(item -> new ModifyInventory(mode, item))
                .toList();
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
            counter.record("public-buffs", packet);
            actor.getMap().broadcastMessage(actor, packet, false);
        }
        for (Character.BuffPresentationSnapshot buff : actor.getBuffPresentationSnapshots()) {
            Packet packet = PacketCreator.giveForeignBuff(actor.getId(), buff.statups());
            counter.record("public-buffs", packet);
            actor.getMap().broadcastMessage(actor, packet, false);
        }
        for (Character.DiseasePresentationSnapshot disease
                : oldProfileHolder.getDiseasePresentationSnapshots()) {
            Packet packet = disease.disease() == Disease.SLOW
                    ? PacketCreator.cancelForeignSlowDebuff(actor.getId())
                    : PacketCreator.cancelForeignDebuff(actor.getId(), disease.disease().getValue());
            counter.record("public-diseases", packet);
            actor.getMap().broadcastMessage(actor, packet, false);
        }
        for (Character.DiseasePresentationSnapshot disease : actor.getDiseasePresentationSnapshots()) {
            List<Pair<Disease, Integer>> statups = List.of(
                    new Pair<>(disease.disease(), disease.skill().getX()));
            Packet packet = disease.disease() == Disease.SLOW
                    ? PacketCreator.giveForeignSlowDebuff(actor.getId(), statups, disease.skill())
                    : PacketCreator.giveForeignDebuff(actor.getId(), statups, disease.skill());
            counter.record("public-diseases", packet);
            actor.getMap().broadcastMessage(actor, packet, false);
        }
        for (byte slot = 0; slot < 3; slot++) {
            if (oldProfileHolder.getPet(slot) != null) {
                Packet packet = PacketCreator.showPetAtIndex(
                        actor, slot, oldProfileHolder.getPet(slot), true, false);
                counter.record("public-pets", packet);
                actor.getMap().broadcastMessage(actor, packet, false);
            }
            if (actor.getPet(slot) != null) {
                Packet packet = PacketCreator.showPetAtIndex(
                        actor, slot, actor.getPet(slot), false, false);
                counter.record("public-pets", packet);
                actor.getMap().broadcastMessage(actor, packet, false);
            }
        }
    }

    private static void refreshSwitchEffects(PacketCounter counter,
                                             Character humanActor,
                                             Character partnerActorOrDormantProfile,
                                             PartnerMode mode,
                                             AdventurerPartnerConfig config) {
        int effectId = config.SWITCH_EFFECT_ID;
        if (effectId < 0) {
            return;
        }
        counter.send("switch-effect", PacketCreator.showSpecialEffect(effectId));
        if (config.SWITCH_EFFECT_BROADCAST) {
            broadcastSwitchEffect(counter, humanActor, effectId);
            if (mode == PartnerMode.DOUBLE_PARTNER) {
                broadcastSwitchEffect(counter, partnerActorOrDormantProfile, effectId);
            }
        }
    }

    private static void broadcastSwitchEffect(PacketCounter counter,
                                              Character actor,
                                              int effectId) {
        if (actor == null || actor.getMap() == null) {
            return;
        }
        Packet packet = PacketCreator.showForeignEffect(actor.getId(), effectId);
        counter.record("switch-effect", packet);
        actor.getMap().broadcastMessage(actor, packet, false);
    }

    private static final class PacketCounter {
        private final Character character;
        private int packetCount;
        private long packetBytes;
        private final Map<String, CategoryTotal> categoryTotals = new LinkedHashMap<>();
        private final List<Packet> localPackets = new ArrayList<>();

        private PacketCounter(Character character) {
            this.character = character;
        }

        private void send(String category, Packet packet) {
            record(category, packet);
            localPackets.add(packet);
        }

        private void flush() {
            character.sendPackets(localPackets);
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
            List<Item> inventoryItems) {
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
            List<Item> inventoryItems = new ArrayList<>();
            for (InventoryType type : DISPLAYED_INVENTORIES) {
                slotLimits.put(type, profile.getInventory(type).getSlotLimit());
                for (Item item : profile.getInventory(type).list()) {
                    inventoryItems.add(item.copy());
                }
            }
            return new PreparedStaticPresentation(
                    profileVersion,
                    skills,
                    keymap,
                    quickslots,
                    macros,
                    Map.copyOf(slotLimits),
                    List.copyOf(inventoryItems));
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
