package server.agents.economy.domain;

import server.agents.economy.activity.FarmSessionOutcome;
import server.agents.economy.market.EconomicReason;

import java.time.Instant;
import java.util.*;

/** Creates balanced provenance events for every supported economic mutation. */
public final class EconomicEventFactory {
    private final UUID runId;
    private final String configHash;
    private final String catalogVersion;

    public EconomicEventFactory(UUID runId, String configHash, String catalogVersion) {
        this.runId = Objects.requireNonNull(runId);
        if (configHash == null || configHash.isBlank() || catalogVersion == null || catalogVersion.isBlank())
            throw new IllegalArgumentException("config and catalog versions are required");
        this.configHash = configHash;
        this.catalogVersion = catalogVersion;
    }

    public EconomicEvent initialEndowment(String key, Instant time, String agentId, long mesos,
                                           Map<Integer, Integer> items) {
        List<LedgerPosting> postings = new ArrayList<>();
        if (mesos > 0) transfer(postings, LedgerAccount.source("INITIAL_ENDOWMENT"),
                LedgerAccount.agent(agentId), AssetKey.MESO, mesos, "");
        items.forEach((itemId, quantity) -> transfer(postings,
                LedgerAccount.source("INITIAL_ENDOWMENT"), LedgerAccount.agent(agentId),
                AssetKey.item(itemId), quantity, key + ":" + itemId));
        return event(key, time, EconomicEventKind.INITIAL_ENDOWMENT, List.of(agentId),
                Map.of("reason", "configured initial holdings"), postings);
    }

    public EconomicEvent farm(FarmSessionOutcome outcome) {
        List<LedgerPosting> postings = new ArrayList<>();
        LedgerAccount agent = LedgerAccount.agent(outcome.agentId());
        if (outcome.mesos() > 0) transfer(postings, LedgerAccount.source("MOB_MESO_DROP"), agent,
                AssetKey.MESO, outcome.mesos(), "");
        if (outcome.experience() > 0) transfer(postings, LedgerAccount.source("MOB_EXPERIENCE"), agent,
                new AssetKey(AssetType.EXPERIENCE, "EXP"), outcome.experience(), "");
        for (FarmSessionOutcome.ItemDrop drop : outcome.itemDrops()) {
            transfer(postings, LedgerAccount.source("MOB:" + drop.monsterId()), agent,
                    AssetKey.item(drop.itemId()), drop.quantity(), drop.lotId());
        }
        outcome.consumedItems().forEach(consumed -> transfer(postings, agent,
                LedgerAccount.sink("FARM_CONSUMPTION"), AssetKey.item(consumed.itemId()),
                consumed.quantity(), consumed.lotId()));
        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("mapId", Integer.toString(outcome.mapId()));
        evidence.put("calibrationId", outcome.calibrationId());
        evidence.put("kills", outcome.killCounts().toString());
        evidence.put("dropCount", Integer.toString(outcome.itemDrops().size()));
        evidence.put("reason", "rule-exact calibrated farm result");
        return event("farm:" + outcome.sessionId(), outcome.completedAt(), EconomicEventKind.FARM_RESULT,
                List.of(outcome.agentId()), evidence, postings);
    }

    public EconomicEvent npcPurchase(String key, Instant time, String agentId, int npcId, int sourceMapId,
                                     int itemId, int quantity, long totalMesos, EconomicReason reason) {
        List<LedgerPosting> postings = new ArrayList<>();
        transfer(postings, LedgerAccount.agent(agentId), LedgerAccount.sink("NPC_PURCHASE:" + npcId),
                AssetKey.MESO, totalMesos, "");
        transfer(postings, LedgerAccount.source("NPC_STOCK:" + npcId), LedgerAccount.agent(agentId),
                AssetKey.item(itemId), quantity, key + ":item");
        return event(key, time, EconomicEventKind.NPC_PURCHASE, List.of(agentId),
                reasonEvidence(reason, npcId, sourceMapId, itemId, quantity, totalMesos), postings);
    }

    public EconomicEvent npcSale(String key, Instant time, String agentId, int npcId, int sourceMapId,
                                 int itemId, int quantity, long totalMesos, String lotId,
                                 EconomicReason reason) {
        List<LedgerPosting> postings = new ArrayList<>();
        transfer(postings, LedgerAccount.agent(agentId), LedgerAccount.sink("NPC_BUYBACK:" + npcId),
                AssetKey.item(itemId), quantity, lotId);
        if (totalMesos > 0) transfer(postings, LedgerAccount.source("NPC_BUYBACK:" + npcId),
                LedgerAccount.agent(agentId), AssetKey.MESO, totalMesos, "");
        return event(key, time, EconomicEventKind.NPC_SALE, List.of(agentId),
                reasonEvidence(reason, npcId, sourceMapId, itemId, quantity, totalMesos), postings);
    }

    public EconomicEvent stallSale(String key, Instant time, String buyerId, String sellerId,
                                   int roomMapId, String listingId, int itemId, int quantity,
                                   long grossMesos, long taxMesos, String lotId, EconomicReason reason) {
        if (buyerId.equals(sellerId) || taxMesos < 0 || taxMesos > grossMesos)
            throw new IllegalArgumentException("invalid stall transaction");
        List<LedgerPosting> postings = new ArrayList<>();
        transfer(postings, LedgerAccount.agent(sellerId), LedgerAccount.agent(buyerId),
                AssetKey.item(itemId), quantity, lotId);
        transfer(postings, LedgerAccount.agent(buyerId), LedgerAccount.agent(sellerId),
                AssetKey.MESO, grossMesos - taxMesos, "");
        if (taxMesos > 0) transfer(postings, LedgerAccount.agent(buyerId),
                LedgerAccount.sink("MARKET_TAX"), AssetKey.MESO, taxMesos, "");
        return event(key, time, EconomicEventKind.STALL_SALE, List.of(buyerId, sellerId),
                Map.of("reason", reason.name(), "roomMapId", Integer.toString(roomMapId),
                        "listingId", listingId, "itemId", Integer.toString(itemId),
                        "quantity", Integer.toString(quantity), "grossMesos", Long.toString(grossMesos),
                        "taxMesos", Long.toString(taxMesos)), postings);
    }

    private EconomicEvent event(String key, Instant time, EconomicEventKind kind, List<String> actors,
                                Map<String, String> evidence, List<LedgerPosting> postings) {
        UUID eventId = UUID.nameUUIDFromBytes((runId + ":" + key).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new EconomicEvent(eventId, runId, time, kind, key, "", key, configHash,
                catalogVersion, actors, evidence, postings);
    }

    private static Map<String, String> reasonEvidence(EconomicReason reason, int npcId, int sourceMapId,
                                                       int itemId, int quantity, long mesos) {
        return Map.of("reason", reason.name(), "npcId", Integer.toString(npcId),
                "sourceMapId", Integer.toString(sourceMapId), "itemId", Integer.toString(itemId),
                "quantity", Integer.toString(quantity), "mesos", Long.toString(mesos),
                "remoteAccess", "true");
    }

    private static void transfer(List<LedgerPosting> postings, LedgerAccount from, LedgerAccount to,
                                 AssetKey asset, long quantity, String lotId) {
        if (quantity <= 0) throw new IllegalArgumentException("transfer quantity must be positive");
        postings.add(new LedgerPosting(from, asset, -quantity, lotId));
        postings.add(new LedgerPosting(to, asset, quantity, lotId));
    }
}
