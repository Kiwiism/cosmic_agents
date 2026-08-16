package server.agents.economy.integration.cosmic;

import client.Character;
import client.Job;
import client.inventory.Equip;
import client.inventory.InventoryType;
import client.inventory.Item;
import constants.id.ItemId;
import constants.inventory.ItemConstants;
import server.ItemInformationProvider;
import server.agents.economy.decision.AgentNeed;
import server.agents.economy.market.EconomicReason;
import server.agents.economy.market.MarketObservation;
import server.agents.economy.session.CommerceParticipant;
import server.agents.economy.scenario.EconomyEngineConfig;

import java.time.Instant;
import java.util.*;

/**
 * Adds needs only for exact items an agent physically observed. Values are derived from live holdings,
 * WZ rules, and configured utility limits; the seller's ask never defines willingness to pay.
 */
public final class CosmicObservedOfferNeedAugmenter
        implements AutonomousFreeMarketBehavior.ObservedNeedAugmenter {
    private final EconomyEngineConfig.Demand demand;
    private final EconomyEngineConfig.Scrolling scrolling;
    private final EconomyEngineConfig.Chairs chairs;
    private final Catalog items;

    public CosmicObservedOfferNeedAugmenter(EconomyEngineConfig.Demand demand,
                                            EconomyEngineConfig.Scrolling scrolling,
                                            EconomyEngineConfig.Chairs chairs) {
        this(demand, scrolling, chairs, new CosmicCatalog(ItemInformationProvider.getInstance()));
    }

    CosmicObservedOfferNeedAugmenter(EconomyEngineConfig.Demand demand,
                                     EconomyEngineConfig.Scrolling scrolling,
                                     EconomyEngineConfig.Chairs chairs,
                                     Catalog items) {
        this.demand = Objects.requireNonNull(demand);
        this.scrolling = Objects.requireNonNull(scrolling);
        this.chairs = Objects.requireNonNull(chairs);
        this.items = Objects.requireNonNull(items);
    }

    @Override
    public List<AgentNeed> augment(Character agent, CommerceParticipant profile,
                                   List<MarketObservation> observations, List<AgentNeed> base,
                                   Instant logicalAt) {
        Map<Integer, AgentNeed> result = new LinkedHashMap<>();
        base.forEach(need -> result.put(need.itemId(), need));
        observations.stream()
                .filter(o -> o.state() == MarketObservation.State.LISTED)
                .sorted(Comparator.comparing(MarketObservation::observationId))
                .forEach(observation -> candidate(agent, profile, observation, logicalAt)
                        .ifPresent(need -> result.merge(need.itemId(), need,
                                (existing, added) -> existing.maximumWillingnessToPay()
                                        >= added.maximumWillingnessToPay() ? existing : added)));
        return List.copyOf(result.values());
    }

    private Optional<AgentNeed> candidate(Character agent, CommerceParticipant profile,
                                          MarketObservation observation, Instant at) {
        int itemId = observation.itemId();
        if (isEquipment(itemId)) return equipmentNeed(agent, observation, at);
        if (scrolling.enabled && isScroll(itemId)) return scrollNeed(agent, observation, at);
        if (chairs.enabled && chairs.collectionPreferenceEnabled && ItemId.isChair(itemId))
            return chairNeed(agent, profile, observation, at);
        return Optional.empty();
    }

    private Optional<AgentNeed> equipmentNeed(Character agent, MarketObservation observation, Instant at) {
        int itemId = observation.itemId();
        if (!items.meetsEquipRequirements(agent, itemId)) return Optional.empty();
        String slot = items.getEquipmentSlot(itemId);
        if (slot == null || slot.isBlank()) return Optional.empty();
        double candidateUtility = equipmentUtility(agent.getJob(), observation.attributes());
        double currentUtility = agent.getInventory(InventoryType.EQUIPPED).list().stream()
                .filter(Equip.class::isInstance).map(Equip.class::cast)
                .filter(equip -> slot.equals(items.getEquipmentSlot(equip.getItemId())))
                .mapToDouble(equip -> equipmentUtility(agent.getJob(), equip)).max().orElse(0);
        double marginal = candidateUtility - currentUtility;
        if (marginal < demand.minimumMarginalUtility) return Optional.empty();
        long wtp = willingnessToPay(agent, marginal, demand.equipmentMaximumWalletFraction);
        if (wtp <= 0) return Optional.empty();
        return Optional.of(new AgentNeed(itemId, count(agent, itemId), count(agent, itemId) + 1,
                urgency(marginal), EconomicReason.EQUIPMENT_UPGRADE, at, wtp, Set.of(), Set.of(),
                "observedListing=" + observation.listingId() + " slot=" + slot
                        + " marginalUtility=" + marginal + " fingerprint=" + observation.fingerprint()));
    }

    private Optional<AgentNeed> scrollNeed(Character agent, MarketObservation observation, Instant at) {
        Map<String, Integer> scrollStats = items.getEquipStats(observation.itemId());
        if (scrollStats == null) return Optional.empty();
        Optional<Equip> target = agent.getInventory(InventoryType.EQUIP).list().stream()
                .filter(Equip.class::isInstance).map(Equip.class::cast)
                .filter(equip -> (!scrolling.requireRemainingSlots || equip.getUpgradeSlots() > 0)
                        && items.canApplyScroll(observation.itemId(), equip.getItemId()))
                .max(Comparator.comparingDouble(equip -> scrollExpectedUtility(agent.getJob(), scrollStats, equip)));
        if (target.isEmpty()) {
            target = agent.getInventory(InventoryType.EQUIPPED).list().stream()
                    .filter(Equip.class::isInstance).map(Equip.class::cast)
                    .filter(equip -> (!scrolling.requireRemainingSlots || equip.getUpgradeSlots() > 0)
                            && items.canApplyScroll(observation.itemId(), equip.getItemId()))
                    .max(Comparator.comparingDouble(equip -> scrollExpectedUtility(agent.getJob(), scrollStats, equip)));
        }
        if (scrolling.requireOwnedEquipment && target.isEmpty()) return Optional.empty();
        double expected = target.map(equip -> scrollExpectedUtility(agent.getJob(), scrollStats, equip)).orElse(0d);
        if (expected < demand.minimumMarginalUtility) return Optional.empty();
        long wtp = willingnessToPay(agent, expected, demand.scrollMaximumWalletFraction);
        if (wtp <= 0) return Optional.empty();
        int owned = count(agent, observation.itemId());
        Set<Integer> complements = target.map(equip -> Set.of(equip.getItemId())).orElse(Set.of());
        return Optional.of(new AgentNeed(observation.itemId(), owned, 1, urgency(expected),
                EconomicReason.SCROLL_UPGRADE, at, wtp, Set.of(), complements,
                "observedListing=" + observation.listingId() + " targetEquipment="
                        + target.map(Equip::getItemId).orElse(0) + " expectedUtility=" + expected));
    }

    private Optional<AgentNeed> chairNeed(Character agent, CommerceParticipant profile,
                                          MarketObservation observation, Instant at) {
        if (count(agent, observation.itemId()) > 0 || profile.chairInterest() <= 0) return Optional.empty();
        double utility = profile.chairInterest();
        long wtp = willingnessToPay(agent, utility, demand.chairMaximumWalletFraction);
        if (wtp <= 0) return Optional.empty();
        return Optional.of(new AgentNeed(observation.itemId(), 0, 1, Math.min(1, utility),
                EconomicReason.COLLECTIBLE_OR_CHAIR, at, wtp, Set.of(), Set.of(),
                "observedListing=" + observation.listingId() + " chairPreference=" + utility));
    }

    private long willingnessToPay(Character agent, double utility, double walletFraction) {
        long utilityLimit = Math.max(0, Math.round(utility * demand.utilityMesoScale));
        long walletLimit = Math.max(0, (long) Math.floor(agent.getMeso() * walletFraction));
        return Math.min(utilityLimit, walletLimit);
    }

    private static double urgency(double utility) { return Math.min(1, utility / 10d); }
    private static boolean isEquipment(int itemId) { return itemId >= 1_000_000 && itemId < 2_000_000; }
    private static boolean isScroll(int itemId) { return itemId >= 2_040_000 && itemId < 2_050_000; }
    private static int count(Character agent, int itemId) {
        return agent.getInventory(ItemConstants.getInventoryType(itemId)).countById(itemId);
    }

    private static double equipmentUtility(Job job, Equip equip) {
        return weighted(job, equip.getStr(), equip.getDex(), equip.getInt(), equip.getLuk(),
                equip.getWatk(), equip.getMatk(), equip.getAcc(), equip.getAvoid(), equip.getHp(),
                equip.getMp(), equip.getSpeed(), equip.getJump(), equip.getWdef(), equip.getMdef());
    }

    private static double equipmentUtility(Job job, Map<String, Object> attributes) {
        return weighted(job, value(attributes, "str"), value(attributes, "dex"), value(attributes, "int"),
                value(attributes, "luk"), value(attributes, "watk"), value(attributes, "matk"),
                value(attributes, "acc"), value(attributes, "avoid"), value(attributes, "hp"),
                value(attributes, "mp"), value(attributes, "speed"), value(attributes, "jump"),
                value(attributes, "wdef"), value(attributes, "mdef"));
    }

    private static double scrollExpectedUtility(Job job, Map<String, Integer> stats, Equip target) {
        double success = stats.getOrDefault("success", 0) / 100d;
        double destruction = stats.getOrDefault("cursed", 0) / 100d;
        double gain = weighted(job, stat(stats, "STR"), stat(stats, "DEX"), stat(stats, "INT"),
                stat(stats, "LUK"), stat(stats, "PAD"), stat(stats, "MAD"), stat(stats, "ACC"),
                stat(stats, "EVA"), stat(stats, "MHP"), stat(stats, "MMP"), stat(stats, "Speed"),
                stat(stats, "Jump"), stat(stats, "PDD"), stat(stats, "MDD"));
        return success * gain - destruction * equipmentUtility(job, target);
    }

    private static double weighted(Job job, double str, double dex, double int_, double luk,
                                   double watk, double matk, double acc, double avoid, double hp,
                                   double mp, double speed, double jump, double wdef, double mdef) {
        int niche = job == null ? 0 : job.getJobNiche();
        double strW = niche == 1 || niche == 5 ? 1.0 : 0.15;
        double dexW = switch (niche) { case 1 -> 0.75; case 3, 4, 5 -> 1.0; default -> 0.15; };
        double intW = niche == 2 ? 1.0 : 0.05;
        double lukW = niche == 2 ? 0.35 : niche == 4 ? 1.0 : 0.10;
        double watkW = niche == 2 ? 0.20 : 4.0;
        double matkW = niche == 2 ? 4.0 : 0.05;
        return str * strW + dex * dexW + int_ * intW + luk * lukW + watk * watkW
                + matk * matkW + acc * (niche == 1 ? 0.8 : 0.25) + avoid * 0.15
                + hp * 0.01 + mp * (niche == 2 ? 0.02 : 0.005) + speed * 0.2 + jump * 0.1
                + wdef * 0.02 + mdef * 0.02;
    }

    private static int value(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }
    private static int stat(Map<String, Integer> values, String key) { return values.getOrDefault(key, 0); }

    interface Catalog {
        boolean meetsEquipRequirements(Character agent, int itemId);
        String getEquipmentSlot(int itemId);
        Map<String, Integer> getEquipStats(int itemId);
        boolean canApplyScroll(int scrollId, int equipmentItemId);
    }

    private record CosmicCatalog(ItemInformationProvider delegate) implements Catalog {
        @Override public boolean meetsEquipRequirements(Character agent, int itemId) {
            return delegate.meetsEquipRequirements(agent, itemId);
        }
        @Override public String getEquipmentSlot(int itemId) { return delegate.getEquipmentSlot(itemId); }
        @Override public Map<String, Integer> getEquipStats(int itemId) { return delegate.getEquipStats(itemId); }
        @Override public boolean canApplyScroll(int scrollId, int equipmentItemId) {
            return delegate.canApplyScroll(scrollId, equipmentItemId);
        }
    }
}
