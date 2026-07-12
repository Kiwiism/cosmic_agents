package server.partner;

import client.Character;
import client.inventory.manipulator.InventoryManipulator;
import config.AdventurerPartnerConfig;
import config.YamlConfig;
import constants.inventory.ItemConstants;
import net.server.PlayerBuffValueHolder;
import net.server.Server;
import server.StatEffect;
import tools.Pair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Item-gated, profile-directed skill-buff sharing for Solo Tag transitions. */
public final class SoloTagBuffSharingService {
    public static final SoloTagBuffSharingService INSTANCE =
            new SoloTagBuffSharingService(YamlConfig.config.adventurerPartner);

    private final AdventurerPartnerConfig config;
    private final ItemGrant itemGrant;

    SoloTagBuffSharingService(AdventurerPartnerConfig config) {
        this(config, (buyer, itemId) ->
                InventoryManipulator.addById(buyer.getClient(), itemId, (short) 1));
    }

    SoloTagBuffSharingService(AdventurerPartnerConfig config, ItemGrant itemGrant) {
        this.config = config;
        this.itemGrant = itemGrant;
    }

    public SharingPlan capture(PartnerMode mode, Character humanActor,
                               Character partnerActorOrDormantProfile) {
        if (!config.SOLO_TAG_BUFF_SHARING_ENABLED || mode != PartnerMode.SOLO_TAG) {
            return SharingPlan.none();
        }
        return new SharingPlan(
                transferableBuffs(humanActor),
                transferableBuffs(partnerActorOrDormantProfile),
                eligible(humanActor),
                eligible(partnerActorOrDormantProfile));
    }

    /** Applies the pre-exchange source buffs to the post-exchange receiving profiles. */
    public void applyAfterExchange(SharingPlan plan, Character humanActor,
                                   Character partnerActorOrDormantProfile) {
        if (!plan.enabled()) {
            return;
        }
        apply(humanActor, plan.humanProfileBuffs(), plan.partnerProfileEligible());
        apply(partnerActorOrDormantProfile, plan.partnerProfileBuffs(),
                plan.humanProfileEligible());
    }

    public boolean enabled() {
        return config.SOLO_TAG_BUFF_SHARING_ENABLED;
    }

    public int itemId() {
        return config.SOLO_TAG_BUFF_SHARING_ITEM_ID;
    }

    public int priceMesos() {
        return config.SOLO_TAG_BUFF_SHARING_PRICE_MESOS;
    }

    public boolean eligible(Character character) {
        int itemId = itemId();
        return ItemConstants.isEquipment(itemId)
                ? character.haveItemEquipped(itemId)
                : character.haveItemWithId(itemId, false);
    }

    public boolean ownsItem(Character character) {
        return character.haveItemWithId(itemId(), true);
    }

    public String entitlementStatus(Character character) {
        if (!enabled()) {
            return "Disabled";
        }
        if (eligible(character)) {
            return "#gActive#k";
        }
        if (ownsItem(character) && ItemConstants.isEquipment(itemId())) {
            return "#rInactive - equip #t" + itemId() + "##k";
        }
        return "#dNot owned#k";
    }

    public String purchase(Character buyer) {
        if (!enabled()) {
            throw new IllegalStateException("Solo Tag buff sharing is disabled.");
        }
        if (ownsItem(buyer)) {
            throw new IllegalStateException("You already own #t" + itemId() + "#.");
        }
        if (buyer.getMeso() < priceMesos()) {
            throw new IllegalStateException("You need " + formatMesos(priceMesos())
                    + " mesos to buy #t" + itemId() + "#.");
        }
        if (!buyer.canHold(itemId())) {
            throw new IllegalStateException("Make room in the correct inventory tab first.");
        }
        if (!itemGrant.grant(buyer, itemId())) {
            throw new IllegalStateException("The item could not be added to your inventory.");
        }
        buyer.gainMeso(-priceMesos(), true, false, true);
        String equipInstruction = ItemConstants.isEquipment(itemId())
                ? " Equip it before switching if you want this character to receive the other profile's buffs."
                : " Keep it in this character's inventory to receive the other profile's buffs.";
        return "You purchased #t" + itemId() + "# for " + formatMesos(priceMesos())
                + " mesos." + equipInstruction;
    }

    public String purchaseConfirmation() {
        return "Purchase #t" + itemId() + "# for " + formatMesos(priceMesos())
                + " mesos? " + (ItemConstants.isEquipment(itemId())
                ? "This character must equip it to receive Solo Tag buffs."
                : "This character must carry it to receive Solo Tag buffs.");
    }

    private static BuffBundle transferableBuffs(Character source) {
        List<PlayerBuffValueHolder> partyBuffs = new ArrayList<>();
        List<PlayerBuffValueHolder> selfBuffs = new ArrayList<>();
        for (PlayerBuffValueHolder holder : source.getAllBuffs()) {
            StatEffect effect = holder.effect;
            if (effect != null && effect.isSkill() && !effect.getStatups().isEmpty()) {
                List<PlayerBuffValueHolder> destination = effect.isPartyBuff()
                        ? partyBuffs : selfBuffs;
                destination.add(new PlayerBuffValueHolder(holder.usedTime, effect));
            }
        }
        partyBuffs.sort(buffPriority());
        selfBuffs.sort(buffPriority());
        return new BuffBundle(List.copyOf(partyBuffs), List.copyOf(selfBuffs));
    }

    private static Comparator<PlayerBuffValueHolder> buffPriority() {
        return Comparator.comparingInt(SoloTagBuffSharingService::benefitScore)
                .thenComparingInt(holder -> holder.effect.getBuffSourceId());
    }

    private static int benefitScore(PlayerBuffValueHolder holder) {
        return holder.effect.getStatups().stream()
                .mapToInt(statup -> Math.abs(statup.getRight()))
                .sum();
    }

    private static void apply(Character recipient, BuffBundle sourceBuffs,
                              boolean receivesSelfBuffs) {
        List<PlayerBuffValueHolder> buffs = new ArrayList<>(sourceBuffs.partyBuffs());
        if (receivesSelfBuffs) {
            buffs.addAll(sourceBuffs.selfBuffs());
        }
        buffs.sort(buffPriority());
        buffs = removeWeakerDuplicateSources(recipient, buffs);
        if (buffs.isEmpty()) {
            return;
        }
        long now = Server.getInstance().getCurrentTime();
        List<Pair<Long, PlayerBuffValueHolder>> timedBuffs = new ArrayList<>(buffs.size());
        for (PlayerBuffValueHolder holder : buffs) {
            timedBuffs.add(new Pair<>(now - holder.usedTime, holder));
        }
        recipient.silentGiveBuffs(timedBuffs);
    }

    private static List<PlayerBuffValueHolder> removeWeakerDuplicateSources(
            Character recipient, List<PlayerBuffValueHolder> incoming) {
        List<PlayerBuffValueHolder> result = new ArrayList<>(incoming.size());
        List<PlayerBuffValueHolder> existing = recipient.getAllBuffs();
        for (PlayerBuffValueHolder candidate : incoming) {
            PlayerBuffValueHolder sameSource = existing.stream()
                    .filter(holder -> holder.effect != null
                            && holder.effect.getBuffSourceId()
                            == candidate.effect.getBuffSourceId())
                    .findFirst()
                    .orElse(null);
            if (sameSource == null || strongerOrLonger(candidate, sameSource)) {
                result.add(candidate);
            }
        }
        return result;
    }

    private static boolean strongerOrLonger(PlayerBuffValueHolder candidate,
                                             PlayerBuffValueHolder existing) {
        int candidateScore = benefitScore(candidate);
        int existingScore = benefitScore(existing);
        return candidateScore > existingScore
                || (candidateScore == existingScore && candidate.usedTime < existing.usedTime);
    }

    private static String formatMesos(int mesos) {
        return String.format("%,d", mesos);
    }

    private record BuffBundle(List<PlayerBuffValueHolder> partyBuffs,
                              List<PlayerBuffValueHolder> selfBuffs) {
        private static BuffBundle none() {
            return new BuffBundle(List.of(), List.of());
        }

        private boolean isEmpty() {
            return partyBuffs.isEmpty() && selfBuffs.isEmpty();
        }
    }

    record SharingPlan(BuffBundle humanProfileBuffs,
                       BuffBundle partnerProfileBuffs,
                       boolean humanProfileEligible,
                       boolean partnerProfileEligible) {
        static SharingPlan none() {
            return new SharingPlan(BuffBundle.none(), BuffBundle.none(), false, false);
        }

        private boolean enabled() {
            return humanProfileEligible || partnerProfileEligible
                    || !humanProfileBuffs.isEmpty() || !partnerProfileBuffs.isEmpty();
        }
    }

    @FunctionalInterface
    interface ItemGrant {
        boolean grant(Character buyer, int itemId);
    }
}
