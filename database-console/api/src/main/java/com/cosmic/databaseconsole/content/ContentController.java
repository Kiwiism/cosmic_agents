package com.cosmic.databaseconsole.content;

import com.cosmic.databaseconsole.audit.AuditService;
import com.cosmic.databaseconsole.bridge.BridgeClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ContentController {
    private final NamedParameterJdbcTemplate game;
    private final AuditService audit;
    private final BridgeClient bridge;

    public ContentController(@Qualifier("gameJdbc") NamedParameterJdbcTemplate game, AuditService audit,
                             BridgeClient bridge) {
        this.game = game;
        this.audit = audit;
        this.bridge = bridge;
    }

    @GetMapping("/drops")
    List<Map<String, Object>> drops(@RequestParam int mobId) {
        return game.queryForList("""
                SELECT d.id, d.dropperid, d.itemid, d.minimum_quantity, d.maximum_quantity,
                       d.questid, d.chance, c.name AS item_name, c.description, c.properties_json
                FROM drop_data d
                LEFT JOIN cosmic_database_console.catalog_entities c
                  ON c.entity_type = 'ITEM' AND c.entity_id = d.itemid
                WHERE d.dropperid = :mobId ORDER BY d.chance DESC, d.itemid
                """, Map.of("mobId", mobId));
    }

    @GetMapping("/global-drops")
    List<Map<String, Object>> globalDrops() {
        return game.queryForList("""
                SELECT d.*, c.name AS item_name, c.description, c.properties_json
                FROM drop_data_global d
                LEFT JOIN cosmic_database_console.catalog_entities c
                  ON c.entity_type='ITEM' AND c.entity_id=d.itemid
                ORDER BY d.continent, d.chance DESC, d.itemid
                """, Map.of());
    }

    @PostMapping("/global-drops")
    @Transactional("gameTransactionManager")
    Map<String, Object> addGlobalDrop(@Valid @RequestBody GlobalDropRequest body, Principal principal,
                                      HttpServletRequest request) {
        validateQuantities(body.minimumQuantity(), body.maximumQuantity());
        game.update("""
                INSERT INTO drop_data_global(continent, itemid, minimum_quantity, maximum_quantity,
                    questid, chance, comments)
                VALUES (:continent, :itemId, :minimum, :maximum, :questId, :chance, :comments)
                """, new MapSqlParameterSource().addValue("continent", body.continent())
                .addValue("itemId", body.itemId()).addValue("minimum", body.minimumQuantity())
                .addValue("maximum", body.maximumQuantity()).addValue("questId", body.questId())
                .addValue("chance", body.chance()).addValue("comments", body.comments()));
        boolean active = bridge.reloadDrops();
        audit.record(principal, "GLOBAL_DROP_CREATE", "GLOBAL_DROP", body.itemId(), body.reason(),
                null, body, active ? "ACTIVE" : "SAVED_RELOAD_PENDING", request);
        return Map.of("saved", true, "active", active);
    }

    @PutMapping("/global-drops/{id}")
    @Transactional("gameTransactionManager")
    Map<String, Object> updateGlobalDrop(@PathVariable long id, @Valid @RequestBody GlobalDropRequest body,
                                         Principal principal, HttpServletRequest request) {
        validateQuantities(body.minimumQuantity(), body.maximumQuantity());
        Map<String, Object> before = requiredRow("SELECT * FROM drop_data_global WHERE id=:id",
                Map.of("id", id), "Global drop not found");
        game.update("""
                UPDATE drop_data_global SET continent=:continent, itemid=:itemId,
                    minimum_quantity=:minimum, maximum_quantity=:maximum, questid=:questId,
                    chance=:chance, comments=:comments WHERE id=:id
                """, new MapSqlParameterSource().addValue("id", id).addValue("continent", body.continent())
                .addValue("itemId", body.itemId()).addValue("minimum", body.minimumQuantity())
                .addValue("maximum", body.maximumQuantity()).addValue("questId", body.questId())
                .addValue("chance", body.chance()).addValue("comments", body.comments()));
        Map<String, Object> after = requiredRow("SELECT * FROM drop_data_global WHERE id=:id",
                Map.of("id", id), "Global drop not found");
        boolean active = bridge.reloadDrops();
        audit.record(principal, "GLOBAL_DROP_UPDATE", "GLOBAL_DROP", id, body.reason(), before, after,
                active ? "ACTIVE" : "SAVED_RELOAD_PENDING", request);
        return Map.of("saved", true, "active", active, "drop", after);
    }

    @DeleteMapping("/global-drops/{id}")
    @Transactional("gameTransactionManager")
    Map<String, Object> deleteGlobalDrop(@PathVariable long id, @RequestParam @NotBlank String reason,
                                         Principal principal, HttpServletRequest request) {
        Map<String, Object> before = requiredRow("SELECT * FROM drop_data_global WHERE id=:id",
                Map.of("id", id), "Global drop not found");
        game.update("DELETE FROM drop_data_global WHERE id=:id", Map.of("id", id));
        boolean active = bridge.reloadDrops();
        audit.record(principal, "GLOBAL_DROP_DELETE", "GLOBAL_DROP", id, reason, before, null,
                active ? "ACTIVE" : "SAVED_RELOAD_PENDING", request);
        return Map.of("deleted", true, "active", active);
    }

    @PostMapping("/drops")
    @Transactional("gameTransactionManager")
    Map<String, Object> addDrop(@Valid @RequestBody DropRequest body, Principal principal,
                                HttpServletRequest request) {
        if (body.maximumQuantity() < body.minimumQuantity()) {
            throw new IllegalArgumentException("Maximum quantity must be at least the minimum quantity");
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("mobId", body.mobId()).addValue("itemId", body.itemId())
                .addValue("minimum", body.minimumQuantity()).addValue("maximum", body.maximumQuantity())
                .addValue("questId", body.questId()).addValue("chance", body.chance());
        game.update("""
                INSERT INTO drop_data(dropperid, itemid, minimum_quantity, maximum_quantity, questid, chance)
                VALUES (:mobId, :itemId, :minimum, :maximum, :questId, :chance)
                """, params);
        boolean active = bridge.reloadDrops();
        Map<String, Object> after = Map.of("mobId", body.mobId(), "itemId", body.itemId(),
                "minimumQuantity", body.minimumQuantity(), "maximumQuantity", body.maximumQuantity(),
                "questId", body.questId(), "chance", body.chance());
        audit.record(principal, "DROP_CREATE", "DROP", body.mobId() + ":" + body.itemId(), body.reason(),
                null, after, active ? "ACTIVE" : "SAVED_RELOAD_PENDING", request);
        return Map.of("saved", true, "active", active);
    }

    @DeleteMapping("/drops/{id}")
    @Transactional("gameTransactionManager")
    Map<String, Object> deleteDrop(@PathVariable long id, @RequestParam @NotBlank String reason,
                                   Principal principal, HttpServletRequest request) {
        List<Map<String, Object>> rows = game.queryForList("SELECT * FROM drop_data WHERE id = :id", Map.of("id", id));
        if (rows.isEmpty()) {
            return Map.of("deleted", false);
        }
        game.update("DELETE FROM drop_data WHERE id = :id", Map.of("id", id));
        boolean active = bridge.reloadDrops();
        audit.record(principal, "DROP_DELETE", "DROP", id, reason, rows.getFirst(), null,
                active ? "ACTIVE" : "SAVED_RELOAD_PENDING", request);
        return Map.of("deleted", true, "active", active);
    }

    @PutMapping("/drops/{id}")
    @Transactional("gameTransactionManager")
    Map<String, Object> updateDrop(@PathVariable long id, @Valid @RequestBody DropRequest body,
                                   Principal principal, HttpServletRequest request) {
        Map<String, Object> before = requiredRow("SELECT * FROM drop_data WHERE id = :id", Map.of("id", id),
                "Drop entry not found");
        if (body.maximumQuantity() < body.minimumQuantity()) {
            throw new IllegalArgumentException("Maximum quantity must be at least the minimum quantity");
        }
        game.update("""
                UPDATE drop_data SET dropperid = :mobId, itemid = :itemId,
                    minimum_quantity = :minimum, maximum_quantity = :maximum,
                    questid = :questId, chance = :chance
                WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id).addValue("mobId", body.mobId()).addValue("itemId", body.itemId())
                .addValue("minimum", body.minimumQuantity()).addValue("maximum", body.maximumQuantity())
                .addValue("questId", body.questId()).addValue("chance", body.chance()));
        Map<String, Object> after = requiredRow("SELECT * FROM drop_data WHERE id = :id", Map.of("id", id),
                "Drop entry not found after update");
        boolean active = bridge.reloadDrops();
        audit.record(principal, "DROP_UPDATE", "DROP", id, body.reason(), before, after,
                active ? "ACTIVE" : "SAVED_RELOAD_PENDING", request);
        return Map.of("saved", true, "active", active, "drop", after);
    }

    @GetMapping("/shops")
    List<Map<String, Object>> shops(@RequestParam(defaultValue = "") String query) {
        return game.queryForList("""
                SELECT s.shopid, s.npcid, COUNT(DISTINCT si.shopitemid) AS item_count,
                       n.name AS npc_name, GROUP_CONCAT(DISTINCT CONCAT(ml.map_id, ':', COALESCE(m.name, ml.map_id))
                         ORDER BY m.name SEPARATOR ' | ') locations,
                       MAX(ml.region_name) region_name,
                       (SELECT pm.name FROM cosmic_database_console.catalog_map_life pml
                        JOIN cosmic_database_console.catalog_entities pm ON pm.entity_type='MAP' AND pm.entity_id=pml.map_id
                        WHERE pml.life_type='n' AND pml.entity_id=s.npcid
                        ORDER BY pml.spawn_count DESC,pm.name LIMIT 1) primary_map_name
                FROM shops s
                LEFT JOIN shopitems si ON si.shopid = s.shopid
                LEFT JOIN cosmic_database_console.catalog_entities n
                  ON n.entity_type = 'NPC' AND n.entity_id = s.npcid
                LEFT JOIN cosmic_database_console.catalog_map_life ml ON ml.life_type='n' AND ml.entity_id=s.npcid
                LEFT JOIN cosmic_database_console.catalog_entities m ON m.entity_type='MAP' AND m.entity_id=ml.map_id
                WHERE :query = '' OR CAST(s.shopid AS CHAR) LIKE :likeQuery
                   OR CAST(s.npcid AS CHAR) LIKE :likeQuery OR LOWER(n.name) LIKE :likeQuery
                GROUP BY s.shopid, s.npcid, n.name
                ORDER BY n.name, s.shopid LIMIT 200
                """, Map.of("query", query, "likeQuery", "%" + query.toLowerCase() + "%"));
    }

    @PostMapping("/shops")
    @Transactional("gameTransactionManager")
    Map<String, Object> createShop(@Valid @RequestBody ShopCreateRequest body, Principal principal,
                                   HttpServletRequest request) {
        game.update("INSERT INTO shops(npcid) VALUES (:npcId)", Map.of("npcId", body.npcId()));
        Long shopId = game.queryForObject("SELECT LAST_INSERT_ID()", Map.of(), Long.class);
        audit.record(principal, "SHOP_CREATE", "SHOP", shopId, body.reason(), null,
                Map.of("shopId", shopId, "npcId", body.npcId()), "SAVED", request);
        return Map.of("shopId", shopId, "npcId", body.npcId());
    }

    @GetMapping("/shops/{shopId}/items")
    List<Map<String, Object>> shopItems(@PathVariable int shopId) {
        return game.queryForList("""
                SELECT si.shopitemid, si.itemid, si.price, si.pitch, si.position, c.name AS item_name,
                       c.description, c.properties_json
                FROM shopitems si
                LEFT JOIN cosmic_database_console.catalog_entities c
                  ON c.entity_type = 'ITEM' AND c.entity_id = si.itemid
                WHERE si.shopid = :shopId ORDER BY si.position
                """, Map.of("shopId", shopId));
    }

    @PostMapping("/shops/{shopId}/items")
    @Transactional("gameTransactionManager")
    Map<String, Object> addShopItem(@PathVariable int shopId, @Valid @RequestBody ShopItemRequest body,
                                    Principal principal, HttpServletRequest request) {
        game.update("""
                INSERT INTO shopitems(shopid, itemid, price, pitch, position)
                VALUES (:shopId, :itemId, :price, :pitch, :position)
                """, new MapSqlParameterSource()
                .addValue("shopId", shopId).addValue("itemId", body.itemId())
                .addValue("price", body.price()).addValue("pitch", body.pitch())
                .addValue("position", body.position()));
        boolean active = bridge.reloadShops();
        audit.record(principal, "SHOP_ITEM_CREATE", "SHOP", shopId, body.reason(), null, body,
                active ? "ACTIVE" : "SAVED_RELOAD_PENDING", request);
        return Map.of("saved", true, "active", active);
    }

    @PutMapping("/shops/{shopId}/items/{shopItemId}")
    @Transactional("gameTransactionManager")
    Map<String, Object> updateShopItem(@PathVariable int shopId, @PathVariable long shopItemId,
                                       @Valid @RequestBody ShopItemRequest body, Principal principal,
                                       HttpServletRequest request) {
        Map<String, Object> before = requiredRow("""
                SELECT * FROM shopitems WHERE shopid = :shopId AND shopitemid = :shopItemId
                """, Map.of("shopId", shopId, "shopItemId", shopItemId), "Shop item not found");
        game.update("""
                UPDATE shopitems SET itemid = :itemId, price = :price, pitch = :pitch, position = :position
                WHERE shopid = :shopId AND shopitemid = :shopItemId
                """, new MapSqlParameterSource()
                .addValue("shopId", shopId).addValue("shopItemId", shopItemId)
                .addValue("itemId", body.itemId()).addValue("price", body.price())
                .addValue("pitch", body.pitch()).addValue("position", body.position()));
        Map<String, Object> after = requiredRow("""
                SELECT * FROM shopitems WHERE shopid = :shopId AND shopitemid = :shopItemId
                """, Map.of("shopId", shopId, "shopItemId", shopItemId), "Shop item not found after update");
        boolean active = bridge.reloadShops();
        audit.record(principal, "SHOP_ITEM_UPDATE", "SHOP_ITEM", shopItemId, body.reason(), before, after,
                active ? "ACTIVE" : "SAVED_RELOAD_PENDING", request);
        return Map.of("saved", true, "active", active, "item", after);
    }

    @PostMapping("/shops/{shopId}/items/swap")
    @Transactional("gameTransactionManager")
    Map<String, Object> swapShopItems(@PathVariable int shopId, @Valid @RequestBody ShopSwapRequest body,
                                      Principal principal, HttpServletRequest request) {
        Map<String, Object> first = requiredRow("""
                SELECT * FROM shopitems WHERE shopid=:shopId AND shopitemid=:itemId
                """, Map.of("shopId", shopId, "itemId", body.firstItemId()), "First shop item not found");
        Map<String, Object> second = requiredRow("""
                SELECT * FROM shopitems WHERE shopid=:shopId AND shopitemid=:itemId
                """, Map.of("shopId", shopId, "itemId", body.secondItemId()), "Second shop item not found");
        int firstPosition = ((Number) first.get("position")).intValue();
        int secondPosition = ((Number) second.get("position")).intValue();
        game.update("""
                UPDATE shopitems SET position=CASE shopitemid
                    WHEN :firstId THEN :secondPosition WHEN :secondId THEN :firstPosition END
                WHERE shopid=:shopId AND shopitemid IN (:firstId,:secondId)
                """, new MapSqlParameterSource().addValue("shopId", shopId)
                .addValue("firstId", body.firstItemId()).addValue("secondId", body.secondItemId())
                .addValue("firstPosition", firstPosition).addValue("secondPosition", secondPosition));
        boolean active = bridge.reloadShops();
        Map<String, Object> after = Map.of(
                "first", requiredRow("SELECT * FROM shopitems WHERE shopitemid=:id",
                        Map.of("id", body.firstItemId()), "First shop item missing after swap"),
                "second", requiredRow("SELECT * FROM shopitems WHERE shopitemid=:id",
                        Map.of("id", body.secondItemId()), "Second shop item missing after swap"));
        audit.record(principal, "SHOP_ITEMS_SWAP", "SHOP", shopId, body.reason(),
                Map.of("first", first, "second", second), after,
                active ? "ACTIVE" : "SAVED_RELOAD_PENDING", request);
        return Map.of("saved", true, "active", active, "items", after);
    }

    @DeleteMapping("/shops/{shopId}/items/{shopItemId}")
    @Transactional("gameTransactionManager")
    Map<String, Object> deleteShopItem(@PathVariable int shopId, @PathVariable long shopItemId,
                                       @RequestParam @NotBlank String reason, Principal principal,
                                       HttpServletRequest request) {
        Map<String, Object> before = requiredRow("""
                SELECT * FROM shopitems WHERE shopid = :shopId AND shopitemid = :shopItemId
                """, Map.of("shopId", shopId, "shopItemId", shopItemId), "Shop item not found");
        game.update("""
                DELETE FROM shopitems WHERE shopid = :shopId AND shopitemid = :shopItemId
                """, Map.of("shopId", shopId, "shopItemId", shopItemId));
        boolean active = bridge.reloadShops();
        audit.record(principal, "SHOP_ITEM_DELETE", "SHOP_ITEM", shopItemId, reason, before, null,
                active ? "ACTIVE" : "SAVED_RELOAD_PENDING", request);
        return Map.of("deleted", true, "active", active);
    }

    private Map<String, Object> requiredRow(String sql, Map<String, ?> parameters, String message) {
        List<Map<String, Object>> rows = game.queryForList(sql, parameters);
        if (rows.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, message);
        }
        return rows.getFirst();
    }

    private void validateQuantities(int minimum, int maximum) {
        if (maximum < minimum) {
            throw new IllegalArgumentException("Maximum quantity must be at least the minimum quantity");
        }
    }

    public record DropRequest(@Min(1) int mobId, @Min(0) int itemId, @Min(0) int minimumQuantity,
                              @Min(0) int maximumQuantity, @Min(0) int questId, @Min(0) int chance,
                              @NotBlank String reason) {}

    public record ShopItemRequest(@Min(1) int itemId, @Min(0) int price, int pitch, int position,
                                  @NotBlank String reason) {}
    public record ShopSwapRequest(@Min(1) long firstItemId, @Min(1) long secondItemId,
                                  @NotBlank String reason) {}

    public record GlobalDropRequest(int continent, @Min(1) int itemId, @Min(0) int minimumQuantity,
                                    @Min(0) int maximumQuantity, @Min(0) int questId,
                                    @Min(0) int chance, String comments, @NotBlank String reason) {}

    public record ShopCreateRequest(@Min(1) int npcId, @NotBlank String reason) {}
}
