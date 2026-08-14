package server.agents.economy.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import server.Trade;
import server.agents.economy.domain.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Converts exact, attributed Cosmic mutations into balanced analytical events.
 * Missing bindings, lots, or activity facts are hard failures: this boundary never invents evidence.
 */
public final class CosmicOutboxEventTranslator {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ParticipantResolver participants;
    private final LotResolver lots;
    private final ActivityResolver activities;

    public CosmicOutboxEventTranslator(ParticipantResolver participants, LotResolver lots,
                                       ActivityResolver activities) {
        this.participants = Objects.requireNonNull(participants);
        this.lots = Objects.requireNonNull(lots);
        this.activities = Objects.requireNonNull(activities);
    }

    public IngestionPlan translate(CosmicOutboxRecord receipt) {
        requireAttributed(receipt);
        MutationPayload payload = payload(receipt.payloadJson());
        Map<Integer, ParticipantDelta> deltas = new LinkedHashMap<>();
        for (ParticipantDelta delta : payload.participants()) {
            if (deltas.put(delta.characterId(), delta) != null)
                throw new EvidenceMismatchException("duplicate participant delta");
        }
        ParticipantDelta primaryDelta = requireDelta(deltas, receipt.primaryCharacterId());
        Participant primary = participants.resolve(receipt.runId(), receipt.primaryCharacterId(),
                receipt.primaryIsAgent());
        Participant secondary = receipt.secondaryCharacterId() == null ? null
                : participants.resolve(receipt.runId(), receipt.secondaryCharacterId(),
                receipt.secondaryIsAgent());
        ParticipantDelta secondaryDelta = receipt.secondaryCharacterId() == null ? null
                : requireDelta(deltas, receipt.secondaryCharacterId());
        if (deltas.size() != (secondary == null ? 1 : 2))
            throw new EvidenceMismatchException("unexpected mutation participant");

        Builder builder = new Builder(receipt, primary, secondary, payload.operationEvidence());
        switch (receipt.operationKind()) {
            case "SHOP_BUY" -> npcBuy(builder, primary, primaryDelta, "NPC_STOCK");
            case "SHOP_RECHARGE" -> npcBuy(builder, primary, primaryDelta, "NPC_RECHARGE");
            case "SHOP_SELL" -> npcSell(builder, primary, primaryDelta);
            case "PLAYER_SHOP_LIST" -> playerShopList(builder, primary, primaryDelta);
            case "PLAYER_SHOP_SALE" -> playerShopSale(builder, primary, primaryDelta,
                    requireSecondary(secondary), requireSecondary(secondaryDelta));
            case "PLAYER_TRADE" -> directTrade(builder, primary, primaryDelta,
                    requireSecondary(secondary), requireSecondary(secondaryDelta));
            case "OFFSCREEN_FARM_SETTLEMENT" -> farm(builder, primary, primaryDelta);
            case "SCROLL_APPLY" -> scrollApply(builder, primary, primaryDelta);
            default -> throw new EvidenceMismatchException("unsupported operation " + receipt.operationKind());
        }
        return builder.build();
    }

    private void npcBuy(Builder builder, Participant participant, ParticipantDelta delta,
                        String sourcePrefix) {
        int npc = builder.requiredInt("npc");
        require(delta.mesoDelta() <= 0, "NPC purchase cannot increase mesos");
        if (delta.mesoDelta() < 0) builder.transfer(participant.account(), LedgerAccount.sink(
                sourcePrefix + ':' + npc), AssetKey.MESO, -(long) delta.mesoDelta(), "");
        for (ItemDelta item : positive(delta.itemDeltas())) {
            for (CreatedLot lot : builder.createLots(sourcePrefix + ':' + npc, item)) {
                builder.transfer(LedgerAccount.source(sourcePrefix + ':' + npc), participant.account(),
                        AssetKey.item(item.itemId()), lot.quantity(), lot.lotId());
            }
        }
        require(noNegativeItems(delta), "NPC purchase removed an unexplained item");
        builder.kind = "NPC_RECHARGE".equals(sourcePrefix) ? EconomicEventKind.RECHARGE
                : EconomicEventKind.NPC_PURCHASE;
    }

    private void npcSell(Builder builder, Participant participant, ParticipantDelta delta) {
        int npc = builder.requiredInt("npc");
        require(delta.mesoDelta() >= 0, "NPC sale cannot remove mesos");
        for (ItemDelta item : negative(delta.itemDeltas())) {
            builder.withdrawAndTransfer(participant.account(), LedgerAccount.sink("NPC_BUYBACK:" + npc),
                    item, -(long) item.quantityDelta());
        }
        require(noPositiveItems(delta), "NPC sale introduced an unexplained item");
        if (delta.mesoDelta() > 0) builder.transfer(LedgerAccount.source("NPC_BUYBACK:" + npc),
                participant.account(), AssetKey.MESO, delta.mesoDelta(), "");
        builder.kind = EconomicEventKind.NPC_SALE;
    }

    private void playerShopList(Builder builder, Participant seller, ParticipantDelta delta) {
        String escrowId = builder.required("escrow");
        LedgerAccount escrow = LedgerAccount.escrow(escrowId);
        boolean returnToOwner = builder.receipt.summary().startsWith("close ")
                || builder.receipt.summary().startsWith("recover ");
        require(delta.mesoDelta() == 0, "stall escrow movement changed mesos");
        if (returnToOwner) {
            for (ItemDelta item : positive(delta.itemDeltas())) {
                builder.withdrawAndTransfer(escrow, seller.account(), item, item.quantityDelta());
            }
            require(noNegativeItems(delta), "stall recovery removed owner inventory");
        } else {
            for (ItemDelta item : negative(delta.itemDeltas())) {
                builder.withdrawAndTransfer(seller.account(), escrow, item, -(long) item.quantityDelta());
            }
            require(noPositiveItems(delta), "stall listing introduced owner inventory");
        }
        builder.kind = EconomicEventKind.STALL_LISTED;
        builder.evidence.put("escrowDirection", returnToOwner ? "RETURN_TO_OWNER" : "OWNER_TO_ESCROW");
    }

    private void playerShopSale(Builder builder, Participant buyer, ParticipantDelta buyerDelta,
                                Participant seller, ParticipantDelta sellerDelta) {
        String escrowId = builder.required("escrow");
        int gross = builder.requiredInt("gross");
        int buyerTax = builder.intValue("buyerTax", 0);
        int sellerTax = builder.intValue("sellerTax", builder.intValue("fee", 0));
        require(buyerDelta.mesoDelta() == -Math.addExact(gross, buyerTax),
                "buyer debit does not match stall summary");
        require(sellerDelta.mesoDelta() == gross - sellerTax,
                "seller proceeds do not match stall summary");
        require(noNegativeItems(buyerDelta) && sellerDelta.itemDeltas().isEmpty(),
                "stall sale item evidence is inconsistent with escrow");
        for (ItemDelta item : positive(buyerDelta.itemDeltas())) {
            builder.withdrawAndTransfer(LedgerAccount.escrow(escrowId), buyer.account(), item,
                    item.quantityDelta());
        }
        long proceeds = gross - (long) sellerTax;
        if (proceeds > 0) builder.transfer(buyer.account(), seller.account(), AssetKey.MESO, proceeds, "");
        long taxes = Math.addExact((long) buyerTax, sellerTax);
        if (taxes > 0) builder.transfer(buyer.account(), LedgerAccount.sink("MARKET_TAX"),
                AssetKey.MESO, taxes, "");
        builder.kind = EconomicEventKind.STALL_SALE;
    }

    private void directTrade(Builder builder, Participant first, ParticipantDelta firstDelta,
                             Participant second, ParticipantDelta secondDelta) {
        require(noNegativeItems(firstDelta) && noNegativeItems(secondDelta),
                "trade settlement snapshots may only contain incoming trade items");
        for (ItemDelta received : positive(firstDelta.itemDeltas()))
            builder.withdrawAndTransfer(second.account(), first.account(), received, received.quantityDelta());
        for (ItemDelta received : positive(secondDelta.itemDeltas()))
            builder.withdrawAndTransfer(first.account(), second.account(), received, received.quantityDelta());
        int firstReceivesGross = builder.requiredInt("firstMesos");
        int secondReceivesGross = builder.requiredInt("secondMesos");
        int firstNetReceipt = firstReceivesGross - Trade.getFee(firstReceivesGross);
        int secondNetReceipt = secondReceivesGross - Trade.getFee(secondReceivesGross);
        require(firstDelta.mesoDelta() == firstNetReceipt && secondDelta.mesoDelta() == secondNetReceipt,
                "direct-trade meso deltas do not match offered mesos and native fees");
        if (firstReceivesGross > 0) {
            int fee = Trade.getFee(firstReceivesGross);
            if (firstReceivesGross - fee > 0) builder.transfer(second.account(), first.account(),
                    AssetKey.MESO, firstReceivesGross - (long) fee, "");
            if (fee > 0) builder.transfer(second.account(), LedgerAccount.sink("DIRECT_TRADE_FEE"),
                    AssetKey.MESO, fee, "");
        }
        if (secondReceivesGross > 0) {
            int fee = Trade.getFee(secondReceivesGross);
            if (secondReceivesGross - fee > 0) builder.transfer(first.account(), second.account(),
                    AssetKey.MESO, secondReceivesGross - (long) fee, "");
            if (fee > 0) builder.transfer(first.account(), LedgerAccount.sink("DIRECT_TRADE_FEE"),
                    AssetKey.MESO, fee, "");
        }
        builder.kind = EconomicEventKind.DIRECT_TRADE;
    }

    private void farm(Builder builder, Participant agent, ParticipantDelta delta) {
        if (builder.receipt.activityId() == null)
            throw new EvidenceMismatchException("farm receipt has no activity id");
        FarmEvidence farm = activities.resolve(builder.receipt.runId(), builder.receipt.activityId());
        require(delta.mesoDelta() == farm.mesos(), "farm meso outcome differs from Cosmic mutation");
        // Current-level EXP wraps during real level-ups, so gross EXP comes from the durable
        // activity outcome while the before/after progression remains supporting evidence.
        builder.evidence.put("levelBefore", Integer.toString(delta.levelBefore()));
        builder.evidence.put("levelAfter", Integer.toString(delta.levelAfter()));
        builder.evidence.put("experienceBefore", Integer.toString(delta.experienceBefore()));
        builder.evidence.put("experienceAfter", Integer.toString(delta.experienceAfter()));
        Map<Integer, Long> expectedNet = new HashMap<>();
        for (FarmDrop drop : farm.drops()) expectedNet.merge(drop.itemId(), (long) drop.quantity(), Long::sum);
        for (FarmConsumption use : farm.consumed()) expectedNet.merge(use.itemId(), -(long) use.quantity(), Long::sum);
        Map<Integer, Long> actualNet = new HashMap<>();
        for (ItemDelta item : delta.itemDeltas()) actualNet.merge(item.itemId(), (long) item.quantityDelta(), Long::sum);
        expectedNet.entrySet().removeIf(e -> e.getValue() == 0); actualNet.entrySet().removeIf(e -> e.getValue() == 0);
        require(expectedNet.equals(actualNet), "farm item outcome differs from Cosmic mutation");

        if (farm.mesos() > 0) builder.transfer(LedgerAccount.source("MOB_MESO_DROP"), agent.account(),
                AssetKey.MESO, farm.mesos(), "");
        if (farm.experience() > 0) builder.transfer(LedgerAccount.source("MOB_EXPERIENCE"), agent.account(),
                new AssetKey(AssetType.EXPERIENCE, "EXP"), farm.experience(), "");
        Map<Integer, Deque<ItemDelta>> gainedFacts = new HashMap<>();
        for (ItemDelta item : positive(delta.itemDeltas()))
            gainedFacts.computeIfAbsent(item.itemId(), ignored -> new ArrayDeque<>()).add(item);
        for (FarmDrop drop : farm.drops()) {
            Deque<ItemDelta> candidates = gainedFacts.getOrDefault(drop.itemId(), new ArrayDeque<>());
            ItemDelta exact = candidates.peekFirst();
            if (exact != null && "EQUIP".equals(exact.inventoryType())) candidates.removeFirst();
            String fingerprint = exact == null ? "" : exact.fingerprint();
            Map<String, Object> attributes = new LinkedHashMap<>(drop.attributes());
            if (!fingerprint.isBlank()) attributes.put("fingerprint", fingerprint);
            List<CreatedInstance> instances = exact != null && "EQUIP".equals(exact.inventoryType())
                    ? List.of(new CreatedInstance(drop.lotId() + ":instance", drop.lotId(), drop.itemId(),
                    exact.attributes(), agent.id(), "INVENTORY")) : List.of();
            CreatedLot lot = new CreatedLot(drop.lotId(), drop.itemId(), drop.quantity(),
                    "MOB_DROP", Integer.toString(drop.monsterId()), attributes, instances);
            builder.createdLots.add(lot);
            builder.transfer(LedgerAccount.source("MOB:" + drop.monsterId()), agent.account(),
                    AssetKey.item(drop.itemId()), drop.quantity(), drop.lotId());
        }
        for (FarmConsumption use : farm.consumed()) {
            ItemDelta fact = delta.itemDeltas().stream().filter(i -> i.itemId() == use.itemId()).findFirst()
                    .orElse(new ItemDelta(use.itemId(), "USE", "", 0, 0, -use.quantity(), Map.of()));
            builder.withdrawAndTransfer(agent.account(), LedgerAccount.sink("FARM_CONSUMPTION"),
                    fact, use.quantity());
        }
        builder.kind = EconomicEventKind.FARM_RESULT;
    }

    @SuppressWarnings("unchecked")
    private void scrollApply(Builder builder, Participant agent, ParticipantDelta delta) {
        require(delta.mesoDelta() == 0, "scroll application changed mesos");
        Object raw = builder.evidence.get("scrollApplication");
        require(raw instanceof Map<?, ?>, "scroll application evidence is missing");
        Map<String, Object> application = (Map<String, Object>) raw;
        int scrollId = number(application, "scrollItemId");
        int equipmentId = number(application, "equipmentItemId");
        String outcome = Objects.toString(application.get("outcome"), "");
        require(Set.of("SUCCESS", "FAIL", "CURSE").contains(outcome), "invalid scroll outcome");
        long consumedScrolls = 0;
        for (ItemDelta item : negative(delta.itemDeltas())) {
            long quantity = -(long) item.quantityDelta();
            if (item.itemId() == scrollId) {
                consumedScrolls += quantity;
                builder.withdrawAndTransfer(agent.account(), LedgerAccount.sink("SCROLL_CONSUMPTION"),
                        item, quantity);
            } else {
                require(item.itemId() == equipmentId, "scroll removed an unrelated item");
                builder.withdrawAndTransfer(agent.account(), LedgerAccount.sink("SCROLL_INPUT"),
                        item, quantity);
            }
        }
        require(consumedScrolls == 1, "scroll application must consume exactly one project scroll");
        for (ItemDelta item : positive(delta.itemDeltas())) {
            require(item.itemId() == equipmentId && "EQUIP".equals(item.inventoryType()),
                    "scroll introduced an unrelated item");
            for (CreatedLot lot : builder.createLots("TRANSFORMATION",
                    "SCROLL:" + scrollId + ':' + outcome, item))
                builder.transfer(LedgerAccount.source("SCROLL_TRANSFORMATION:" + scrollId),
                        agent.account(), AssetKey.item(equipmentId), lot.quantity(), lot.lotId());
        }
        builder.kind = EconomicEventKind.SCROLL_APPLIED;
    }

    private static int number(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Number number))
            throw new EvidenceMismatchException("scroll evidence missing numeric " + key);
        return number.intValue();
    }

    private static List<ItemDelta> positive(List<ItemDelta> values) {
        return values.stream().filter(v -> v.quantityDelta() > 0).toList();
    }
    private static List<ItemDelta> negative(List<ItemDelta> values) {
        return values.stream().filter(v -> v.quantityDelta() < 0).toList();
    }
    private static boolean noPositiveItems(ParticipantDelta d) { return positive(d.itemDeltas()).isEmpty(); }
    private static boolean noNegativeItems(ParticipantDelta d) { return negative(d.itemDeltas()).isEmpty(); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new EvidenceMismatchException(message);
    }
    private static <T> T requireSecondary(T value) {
        if (value == null) throw new EvidenceMismatchException("operation requires a secondary participant");
        return value;
    }
    private static ParticipantDelta requireDelta(Map<Integer, ParticipantDelta> values, int id) {
        ParticipantDelta value = values.get(id);
        if (value == null) throw new EvidenceMismatchException("missing participant delta for " + id);
        return value;
    }
    private static void requireAttributed(CosmicOutboxRecord receipt) {
        if (receipt.runId() == null || receipt.logicalAt() == null
                || receipt.configRevision() == null || receipt.configRevision().isBlank()
                || receipt.catalogRevision() == null || receipt.catalogRevision().isBlank())
            throw new EvidenceMismatchException("only fully attributed receipts can enter a simulation ledger");
    }
    private static MutationPayload payload(String json) {
        try { return JSON.readValue(json, MutationPayload.class); }
        catch (JsonProcessingException failure) { throw new EvidenceMismatchException("invalid mutation payload", failure); }
    }

    private final class Builder {
        private final CosmicOutboxRecord receipt;
        private final List<Participant> actors;
        private final Map<String, String> summary;
        private final List<LedgerPosting> postings = new ArrayList<>();
        private final List<CreatedLot> createdLots = new ArrayList<>();
        private final Map<String, Object> evidence = new LinkedHashMap<>();
        private EconomicEventKind kind;

        private Builder(CosmicOutboxRecord receipt, Participant primary, Participant secondary,
                        Map<String, Object> operationEvidence) {
            this.receipt = receipt;
            this.actors = secondary == null ? List.of(primary) : List.of(primary, secondary);
            this.summary = parseSummary(receipt.summary());
            evidence.put("outboxId", receipt.outboxId().toString());
            evidence.put("operationKind", receipt.operationKind()); evidence.put("summary", receipt.summary());
            if (receipt.reasonCode() != null) evidence.put("reason", receipt.reasonCode());
            if (receipt.decisionId() != null) evidence.put("decisionId", receipt.decisionId());
            if (receipt.activityId() != null) evidence.put("activityId", receipt.activityId());
            evidence.putAll(summary);
            if (operationEvidence != null) evidence.putAll(operationEvidence);
        }

        private String required(String key) {
            String value = summary.get(key);
            if (value == null || value.isBlank()) throw new EvidenceMismatchException("summary missing " + key);
            return value;
        }
        private int requiredInt(String key) { return intValue(key, Integer.MIN_VALUE); }
        private int intValue(String key, int fallback) {
            String value = summary.get(key);
            if (value == null) {
                if (fallback == Integer.MIN_VALUE) throw new EvidenceMismatchException("summary missing " + key);
                return fallback;
            }
            try { return Integer.parseInt(value); }
            catch (NumberFormatException failure) { throw new EvidenceMismatchException("invalid summary " + key, failure); }
        }
        private List<CreatedLot> createLots(String source, ItemDelta item) {
            return createLots("NPC", source, item);
        }
        private List<CreatedLot> createLots(String sourceKind, String source, ItemDelta item) {
            List<CreatedLot> result = new ArrayList<>();
            if ("EQUIP".equals(item.inventoryType())) {
                for (int i = 0; i < item.quantityDelta(); i++) {
                    String id = receipt.outboxId() + ":" + item.itemId() + ":" + item.fingerprint() + ":" + i;
                    CreatedInstance instance = new CreatedInstance(id + ":instance", id, item.itemId(),
                            item.attributes(), actors.getFirst().id(), "INVENTORY");
                    Map<String, Object> attributes = new LinkedHashMap<>(item.attributes());
                    attributes.put("fingerprint", item.fingerprint());
                    result.add(new CreatedLot(id, item.itemId(), 1, sourceKind, source,
                            attributes, List.of(instance)));
                }
            } else {
                String id = receipt.outboxId() + ":" + item.itemId() + ":" + item.fingerprint();
                Map<String, Object> attributes = new LinkedHashMap<>(item.attributes());
                attributes.put("fingerprint", item.fingerprint());
                result.add(new CreatedLot(id, item.itemId(), item.quantityDelta(), sourceKind, source,
                        attributes, List.of()));
            }
            createdLots.addAll(result);
            return result;
        }
        private void withdrawAndTransfer(LedgerAccount from, LedgerAccount to, ItemDelta item, long quantity) {
            long remaining = quantity;
            for (LotSlice slice : lots.withdraw(receipt.runId(), from, item.itemId(), item.fingerprint(), quantity)) {
                require(slice.quantity() > 0 && slice.quantity() <= remaining, "invalid FIFO lot allocation");
                transfer(from, to, AssetKey.item(item.itemId()), slice.quantity(), slice.lotId());
                remaining -= slice.quantity();
            }
            require(remaining == 0, "insufficient lot provenance for item " + item.itemId());
        }
        private void transfer(LedgerAccount from, LedgerAccount to, AssetKey asset, long quantity, String lot) {
            require(quantity > 0, "transfer quantity must be positive");
            postings.add(new LedgerPosting(from, asset, -quantity, lot));
            postings.add(new LedgerPosting(to, asset, quantity, lot));
        }
        private IngestionPlan build() {
            require(kind != null, "event kind was not assigned");
            UUID eventId = UUID.nameUUIDFromBytes((receipt.runId() + ":cosmic:" + receipt.outboxId())
                    .getBytes(StandardCharsets.UTF_8));
            EconomicEvent event = new EconomicEvent(eventId, receipt.runId(), receipt.logicalAt(), kind,
                    "cosmic:" + receipt.outboxId(), receipt.decisionId(), receipt.activityId(),
                    receipt.configRevision(), receipt.catalogRevision(), actors.stream().map(Participant::id).toList(),
                    evidence, postings);
            return new IngestionPlan(event, createdLots);
        }
    }

    private static Map<String, String> parseSummary(String text) {
        Map<String, String> result = new LinkedHashMap<>();
        if (text == null) return result;
        for (String token : text.split("\\s+")) {
            int equals = token.indexOf('=');
            if (equals > 0 && equals < token.length() - 1)
                result.put(token.substring(0, equals), token.substring(equals + 1));
        }
        return result;
    }

    public interface ParticipantResolver {
        Participant resolve(UUID runId, int characterId, boolean markedAgent);
    }
    public interface LotResolver {
        List<LotSlice> withdraw(UUID runId, LedgerAccount account, int itemId,
                               String fingerprint, long quantity);
    }
    public interface ActivityResolver {
        FarmEvidence resolve(UUID runId, String activityId);
    }
    public record Participant(String id, LedgerAccount account) {
        public Participant { Objects.requireNonNull(id); Objects.requireNonNull(account); }
    }
    public record LotSlice(String lotId, long quantity) { }
    public record IngestionPlan(EconomicEvent event, List<CreatedLot> createdLots) {
        public IngestionPlan { createdLots = List.copyOf(createdLots); }
    }
    public record CreatedLot(String lotId, int itemId, long quantity, String sourceKind,
                             String sourceIdentifier, Map<String, Object> attributes,
                             List<CreatedInstance> instances) {
        public CreatedLot { attributes = Map.copyOf(attributes); instances = List.copyOf(instances); }
    }
    public record CreatedInstance(String instanceId, String lotId, int itemId,
                                  Map<String, Object> stats, String ownerId, String location) {
        public CreatedInstance { stats = Map.copyOf(stats); }
    }
    public record FarmEvidence(long experience, long mesos, List<FarmDrop> drops,
                               List<FarmConsumption> consumed) {
        public FarmEvidence { drops = List.copyOf(drops); consumed = List.copyOf(consumed); }
    }
    public record FarmDrop(String lotId, int monsterId, int itemId, int quantity,
                           Map<String, Object> attributes) {
        public FarmDrop { attributes = Map.copyOf(attributes); }
    }
    public record FarmConsumption(int itemId, int quantity) { }
    public record MutationPayload(List<ParticipantDelta> participants,
                                  Map<String, Object> operationEvidence) {
        public MutationPayload {
            participants = List.copyOf(participants);
            operationEvidence = operationEvidence == null ? Map.of() : Map.copyOf(operationEvidence);
        }
        public MutationPayload(List<ParticipantDelta> participants) { this(participants, Map.of()); }
    }
    public record ParticipantDelta(int characterId, int mesoBefore, int mesoAfter, int mesoDelta,
                                   int levelBefore, int levelAfter, int experienceBefore,
                                   int experienceAfter, List<ItemDelta> itemDeltas) {
        public ParticipantDelta { itemDeltas = List.copyOf(itemDeltas); }
    }
    public record ItemDelta(int itemId, String inventoryType, String fingerprint, int quantityBefore,
                            int quantityAfter, int quantityDelta, Map<String, Object> attributes) {
        public ItemDelta { attributes = Map.copyOf(attributes); fingerprint = fingerprint == null ? "" : fingerprint; }
    }
    public static final class EvidenceMismatchException extends RuntimeException {
        public EvidenceMismatchException(String message) { super(message); }
        public EvidenceMismatchException(String message, Throwable cause) { super(message, cause); }
    }
}
