package server.agents.economy.integration.cosmic;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import server.agents.economy.scenario.EconomyDayCloseReconciler;
import server.agents.economy.scenario.EconomyRunCoordinator;
import server.maps.PlayerShop;
import server.maps.PlayerShopItem;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/** Reconciles live Cosmic holdings and PlayerShop escrow against the analytical ledger. */
public final class JdbcCosmicHoldingsReconciler implements EconomyDayCloseReconciler {
    private final DataSource dataSource;
    private final Function<String, Character> characters;

    public JdbcCosmicHoldingsReconciler(DataSource dataSource, Function<String, Character> characters) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.characters = Objects.requireNonNull(characters);
    }

    @Override
    public Result reconcile(UUID runId, Map<String, EconomyRunCoordinator.AgentView> agents,
                            java.time.Instant logicalAt) {
        LedgerBalances ledger = readLedger(runId);
        List<String> violations = new ArrayList<>();
        for (String agentId : agents.keySet().stream().sorted().toList()) {
            Character character = characters.apply(agentId);
            if (character == null) {
                violations.add("MISSING_LIVE_CHARACTER:" + agentId);
                continue;
            }
            if (character.getTrade() != null) violations.add("OPEN_TRADE_WINDOW:" + agentId);
            long ledgerMeso = ledger.mesos().getOrDefault(agentId, 0L);
            if (ledgerMeso != character.getMeso())
                violations.add("MESO_MISMATCH:" + agentId + ":live=" + character.getMeso()
                        + ":ledger=" + ledgerMeso);
            compareItems("AGENT", agentId, inventory(character), ledger.agentItems(), violations);

            PlayerShop shop = character.getPlayerShop();
            if (shop != null && shop.isOpen()) {
                String escrowId = shop.getEscrowId();
                if (escrowId == null || escrowId.isBlank())
                    violations.add("OPEN_STALL_WITHOUT_ESCROW:" + agentId);
                else compareItems("ESCROW", escrowId, escrow(shop), ledger.escrowItems(), violations);
            }
        }
        return new Result(violations.isEmpty(), violations);
    }

    private LedgerBalances readLedger(UUID runId) {
        String sql = "SELECT account_type,account_owner_id,asset_type,asset_identifier,SUM(quantity) quantity "
                + "FROM ledger_posting p JOIN economic_event e USING(event_id) WHERE e.run_id=? "
                + "AND account_type IN ('AGENT','ESCROW') GROUP BY account_type,account_owner_id,"
                + "asset_type,asset_identifier HAVING SUM(quantity)<>0";
        Map<String, Long> mesos = new HashMap<>();
        Map<String, Map<Integer, Long>> agents = new HashMap<>();
        Map<String, Map<Integer, Long>> escrow = new HashMap<>();
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, runId);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    String type = rows.getString("account_type");
                    String owner = rows.getString("account_owner_id");
                    String asset = rows.getString("asset_type");
                    long quantity = rows.getLong("quantity");
                    if (type.equals("AGENT") && asset.equals("MESO")) mesos.put(owner, quantity);
                    else if (asset.equals("ITEM")) (type.equals("AGENT") ? agents : escrow)
                            .computeIfAbsent(owner, ignored -> new HashMap<>())
                            .put(Integer.parseInt(rows.getString("asset_identifier")), quantity);
                }
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not read economy ledger for day close", failure);
        }
        return new LedgerBalances(mesos, agents, escrow);
    }

    private static Map<Integer, Long> inventory(Character character) {
        Map<Integer, Long> result = new HashMap<>();
        for (InventoryType type : InventoryType.values()) {
            if (type == InventoryType.UNDEFINED || type == InventoryType.CANHOLD
                    || character.getInventory(type) == null) continue;
            for (Item item : character.getInventory(type).list())
                result.merge(item.getItemId(), (long) item.getQuantity(), Math::addExact);
        }
        return result;
    }

    private static Map<Integer, Long> escrow(PlayerShop shop) {
        Map<Integer, Long> result = new HashMap<>();
        for (PlayerShopItem listing : shop.getItems()) {
            long quantity = Math.multiplyExact((long) listing.getItem().getQuantity(), listing.getBundles());
            if (quantity > 0) result.merge(listing.getItem().getItemId(), quantity, Math::addExact);
        }
        return result;
    }

    private static void compareItems(String type, String owner, Map<Integer, Long> live,
                                     Map<String, Map<Integer, Long>> ledger, List<String> violations) {
        Map<Integer, Long> expected = ledger.getOrDefault(owner, Map.of());
        java.util.TreeSet<Integer> itemIds = new java.util.TreeSet<>(live.keySet());
        itemIds.addAll(expected.keySet());
        for (int itemId : itemIds) {
            long actual = live.getOrDefault(itemId, 0L);
            long recorded = expected.getOrDefault(itemId, 0L);
            if (actual != recorded) violations.add(type + "_ITEM_MISMATCH:" + owner + ':' + itemId
                    + ":live=" + actual + ":ledger=" + recorded);
        }
    }

    private record LedgerBalances(Map<String, Long> mesos,
                                  Map<String, Map<Integer, Long>> agentItems,
                                  Map<String, Map<Integer, Long>> escrowItems) { }
}
