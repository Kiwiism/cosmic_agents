package com.cosmic.databaseconsole.players;

import com.cosmic.databaseconsole.audit.AuditService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api")
public class PlayerController {
    private static final Pattern CHARACTER_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9]{3,12}$");
    private static final int[] DEFAULT_KEYS = {18, 65, 2, 23, 3, 4, 5, 6, 16, 17, 19, 25, 26, 27, 31, 34, 35, 37, 38, 40, 43, 44, 45, 46, 50, 56, 59, 60, 61, 62, 63, 64, 57, 48, 29, 7, 24, 33, 41, 39};
    private static final int[] DEFAULT_KEY_TYPES = {4, 6, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 4, 4, 5, 6, 6, 6, 6, 6, 6, 5, 4, 5, 4, 4, 4, 4, 4};
    private static final int[] DEFAULT_KEY_ACTIONS = {0, 106, 10, 1, 12, 13, 18, 24, 8, 5, 4, 19, 14, 15, 2, 17, 11, 3, 20, 16, 9, 50, 51, 6, 7, 53, 100, 101, 102, 103, 104, 105, 54, 22, 52, 21, 25, 26, 23, 27};
    private final NamedParameterJdbcTemplate game;
    private final AuditService audit;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper json;

    public PlayerController(@Qualifier("gameJdbc") NamedParameterJdbcTemplate game, AuditService audit,
                            PasswordEncoder passwordEncoder, ObjectMapper json) {
        this.game = game;
        this.audit = audit;
        this.passwordEncoder = passwordEncoder;
        this.json = json;
    }

    @GetMapping("/accounts")
    Map<String, Object> accounts(@RequestParam(defaultValue = "") String query,
                                 @RequestParam(defaultValue = "lastlogin") String sort,
                                 @RequestParam(defaultValue = "desc") String direction,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "50") int size) {
        String order = switch (sort) {
            case "id" -> "a.id";
            case "name" -> "a.name";
            case "created" -> "a.createdat";
            case "characters" -> "character_count";
            default -> "a.lastlogin";
        };
        String dir = "asc".equalsIgnoreCase(direction) ? "ASC" : "DESC";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("query", query).addValue("likeQuery", "%" + query.toLowerCase() + "%")
                .addValue("limit", Math.clamp(size, 10, 200)).addValue("offset", Math.max(page, 0) * size);
        List<Map<String, Object>> rows = game.queryForList("""
                SELECT a.id, a.name, a.email, a.banned, a.mute, a.loggedin, a.lastlogin, a.createdat,
                       a.nxCredit, a.maplePoint, a.nxPrepaid, a.characterslots,
                       a.banreason, COUNT(c.id) AS character_count,
                       COALESCE(MAX(c.gm), 0) AS max_gm,
                       GROUP_CONCAT(DISTINCT c.world ORDER BY c.world) worlds,
                       GROUP_CONCAT(c.name ORDER BY c.name) character_names
                FROM accounts a LEFT JOIN characters c ON c.accountid = a.id
                WHERE :query = '' OR LOWER(a.name) LIKE :likeQuery
                   OR LOWER(COALESCE(a.email, '')) LIKE :likeQuery OR CAST(a.id AS CHAR) LIKE :likeQuery
                   OR LOWER(c.name) LIKE :likeQuery
                GROUP BY a.id ORDER BY
                """ + order + " " + dir + " LIMIT :limit OFFSET :offset", params);
        Long total = game.queryForObject("""
                SELECT COUNT(DISTINCT a.id) FROM accounts a LEFT JOIN characters c ON c.accountid=a.id
                WHERE :query='' OR LOWER(a.name) LIKE :likeQuery OR LOWER(COALESCE(a.email,'')) LIKE :likeQuery
                   OR CAST(a.id AS CHAR) LIKE :likeQuery OR LOWER(c.name) LIKE :likeQuery
                """, params, Long.class);
        return Map.of("items", rows, "page", page, "size", size, "total", total == null ? 0 : total,
                "pages", total == null ? 0 : (total + size - 1) / size);
    }

    @PostMapping("/accounts")
    @Transactional("gameTransactionManager")
    Map<String, Object> createAccount(@Valid @RequestBody AccountCreate body, Principal principal,
                                      HttpServletRequest request) {
        if (body.name().length() > 13 || body.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account name must be 1 to 13 characters");
        }
        Integer existing = game.queryForObject("SELECT COUNT(*) FROM accounts WHERE LOWER(name)=LOWER(:name)",
                Map.of("name", body.name()), Integer.class);
        if (existing != null && existing > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account name is already used");
        }
        KeyHolder key = new GeneratedKeyHolder();
        game.update("""
                INSERT INTO accounts(name, password, email, birthday, tempban, characterslots, gender,
                    nxCredit, maplePoint, nxPrepaid)
                VALUES (:name, :password, :email, '2005-05-11', '2005-05-11 00:00:00', :slots, :gender, 0, 0, 0)
                """, new MapSqlParameterSource()
                .addValue("name", body.name()).addValue("password", passwordEncoder.encode(body.password()))
                .addValue("email", body.email()).addValue("slots", Math.clamp(body.characterSlots(), 1, 15))
                .addValue("gender", body.gender()), key, new String[]{"id"});
        int accountId = key.getKey().intValue();
        Map<String, Object> after = account(accountId);
        audit.record(principal, "ACCOUNT_CREATE", "ACCOUNT", accountId, body.reason(), null, after,
                "SAVED", request);
        return after;
    }

    @GetMapping("/characters/name-check")
    Map<String, Object> checkCharacterName(@RequestParam(defaultValue = "") String name) {
        String trimmed = name.trim();
        String message = characterNameProblem(trimmed);
        if (message != null) {
            return Map.of("name", trimmed, "valid", false, "available", false, "message", message);
        }
        boolean available = characterNameAvailable(trimmed);
        return Map.of("name", trimmed, "valid", true, "available", available,
                "message", available ? "IGN is available" : "IGN is already taken");
    }

    @PostMapping("/accounts/{accountId}/characters")
    @Transactional("gameTransactionManager")
    Map<String, Object> createCharacter(@PathVariable int accountId, @Valid @RequestBody CharacterCreate body,
                                        Principal principal, HttpServletRequest request) {
        lockAccount(accountId);
        requireOffline(accountId);
        String name = body.name().trim();
        String nameProblem = characterNameProblem(name);
        if (nameProblem != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, nameProblem);
        }
        if (!characterNameAvailable(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "IGN is already taken");
        }
        Map<String, Object> owner = account(accountId);
        Long characterCount = game.queryForObject("SELECT COUNT(*) FROM characters WHERE accountid=:accountId",
                Map.of("accountId", accountId), Long.class);
        int slots = ((Number) owner.get("characterslots")).intValue();
        if (characterCount != null && characterCount >= slots) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account has no empty character slots");
        }

        KeyHolder key = new GeneratedKeyHolder();
        game.update("""
                INSERT INTO characters(str, dex, luk, `int`, skincolor, gender, job, gm, hair, face, map,
                    meso, spawnpoint, accountid, name, world, hp, mp, maxhp, maxmp, level, ap, sp,
                    equipslots, useslots, setupslots, etcslots)
                VALUES (4, 4, 4, 4, :skincolor, :gender, 0, :gm, :hair, :face, 10000,
                    0, 0, :accountId, :name, :world, 50, 5, 50, 5, 1, 0,
                    '0,0,0,0,0,0,0,0,0,0', 24, 24, 24, 24)
                """, new MapSqlParameterSource()
                .addValue("skincolor", body.skincolor()).addValue("gender", body.gender())
                .addValue("gm", body.gm()).addValue("hair", body.hair()).addValue("face", body.face())
                .addValue("accountId", accountId).addValue("name", name).addValue("world", body.world()),
                key, new String[]{"id"});
        int characterId = key.getKey().intValue();
        insertStarterEquipment(characterId, body);
        insertDefaultKeyMap(characterId);
        ensureStorage(accountId, body.world());
        Map<String, Object> after = character(characterId);
        audit.record(principal, "CHARACTER_CREATE", "CHARACTER", characterId, body.reason(), null, after,
                "SAVED_OFFLINE", request);
        return after;
    }

    @GetMapping("/characters/search")
    List<Map<String, Object>> searchCharacters(@RequestParam(defaultValue = "") String query,
                                                @RequestParam(required = false) Integer itemId) {
        return game.queryForList("""
                SELECT DISTINCT c.id, c.name, c.accountid, a.name account_name, c.world, c.level, c.job,
                       j.job_name, c.meso, c.map, m.name map_name, c.createdate, c.lastLogoutTime, a.loggedin,
                       c.equipslots, c.useslots, c.setupslots, c.etcslots
                FROM characters c JOIN accounts a ON a.id=c.accountid
                LEFT JOIN inventoryitems i ON i.characterid=c.id
                LEFT JOIN cosmic_database_console.catalog_jobs j ON j.job_id=c.job
                LEFT JOIN cosmic_database_console.catalog_entities m ON m.entity_type='MAP' AND m.entity_id=c.map
                WHERE (:query='' OR LOWER(c.name) LIKE :likeQuery OR CAST(c.id AS CHAR) LIKE :likeQuery
                       OR LOWER(a.name) LIKE :likeQuery)
                  AND (:itemId IS NULL OR i.itemid=:itemId)
                ORDER BY c.name LIMIT 100
                """, new MapSqlParameterSource().addValue("query", query)
                .addValue("likeQuery", "%" + query.toLowerCase() + "%").addValue("itemId", itemId));
    }

    @GetMapping("/accounts/{accountId}/characters")
    List<Map<String, Object>> characters(@PathVariable int accountId) {
        return game.queryForList("""
                SELECT c.id, c.name, c.world, c.level, c.exp, c.job, j.job_name, c.gm, c.str, c.dex,
                       c.`int`, c.luk, c.hp, c.mp, c.maxhp, c.maxmp, c.ap, c.sp, c.meso, c.fame,
                       c.map, m.name map_name, c.guildid, c.lastLogoutTime, c.equipslots, c.useslots,
                       c.setupslots, c.etcslots
                FROM characters c
                LEFT JOIN cosmic_database_console.catalog_jobs j ON j.job_id=c.job
                LEFT JOIN cosmic_database_console.catalog_entities m ON m.entity_type='MAP' AND m.entity_id=c.map
                WHERE c.accountid = :accountId ORDER BY c.world, c.name
                """, Map.of("accountId", accountId));
    }

    @GetMapping("/characters/{characterId}")
    Map<String, Object> characterDetails(@PathVariable int characterId) {
        return character(characterId);
    }

    @PatchMapping("/accounts/{accountId}")
    @Transactional("gameTransactionManager")
    Map<String, Object> updateAccount(@PathVariable int accountId, @Valid @RequestBody AccountPatch body,
                                      Principal principal, HttpServletRequest request) {
        Map<String, Object> before = account(accountId);
        game.update("""
                UPDATE accounts SET banned = :banned, banreason = :banReason, mute = :mute,
                    nxCredit = :nxCredit, maplePoint = :maplePoint, nxPrepaid = :nxPrepaid,
                    characterslots = :characterSlots
                WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", accountId).addValue("banned", body.banned())
                .addValue("banReason", body.banReason()).addValue("mute", body.mute())
                .addValue("nxCredit", body.nxCredit()).addValue("maplePoint", body.maplePoint())
                .addValue("nxPrepaid", body.nxPrepaid()).addValue("characterSlots", body.characterSlots()));
        Map<String, Object> after = account(accountId);
        audit.record(principal, "ACCOUNT_UPDATE", "ACCOUNT", accountId, body.reason(), before, after,
                "SAVED", request);
        return after;
    }

    @PatchMapping("/characters/{characterId}")
    @Transactional("gameTransactionManager")
    Map<String, Object> updateCharacter(@PathVariable int characterId, @Valid @RequestBody CharacterPatch body,
                                        Principal principal, HttpServletRequest request) {
        lockCharacter(characterId);
        Map<String, Object> before = character(characterId);
        requireOffline(((Number) before.get("accountid")).intValue());
        game.update("""
                UPDATE characters SET level = :level, job = :job, gm = :gm, str = :str, dex = :dex, `int` = :int,
                    luk = :luk, hp = LEAST(:hp, :maxhp), mp = LEAST(:mp, :maxmp),
                    maxhp = :maxhp, maxmp = :maxmp, ap = :ap, sp = :sp, meso = :meso, fame = :fame,
                    map = :map WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", characterId).addValue("level", body.level()).addValue("job", body.job())
                .addValue("gm", body.gm())
                .addValue("str", body.str()).addValue("dex", body.dex()).addValue("int", body.intStat())
                .addValue("luk", body.luk()).addValue("hp", body.hp()).addValue("mp", body.mp())
                .addValue("maxhp", body.maxHp()).addValue("maxmp", body.maxMp()).addValue("ap", body.ap())
                .addValue("sp", body.sp()).addValue("meso", body.meso()).addValue("fame", body.fame())
                .addValue("map", body.map()));
        Map<String, Object> after = character(characterId);
        audit.record(principal, "CHARACTER_UPDATE", "CHARACTER", characterId, body.reason(), before, after,
                "SAVED_OFFLINE", request);
        return after;
    }

    @PatchMapping("/characters/{characterId}/appearance")
    @Transactional("gameTransactionManager")
    Map<String, Object> updateAppearance(@PathVariable int characterId, @Valid @RequestBody AppearancePatch body,
                                         Principal principal, HttpServletRequest request) {
        lockCharacter(characterId);
        Map<String, Object> before = character(characterId);
        requireOffline(((Number) before.get("accountid")).intValue());
        game.update("""
                UPDATE characters SET hair = :hair, face = :face, skincolor = :skincolor,
                    gender = :gender WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", characterId).addValue("hair", body.hair()).addValue("face", body.face())
                .addValue("skincolor", body.skincolor()).addValue("gender", body.gender()));
        Map<String, Object> after = character(characterId);
        audit.record(principal, "CHARACTER_APPEARANCE_UPDATE", "CHARACTER", characterId, body.reason(), before, after,
                "SAVED_OFFLINE", request);
        return after;
    }

    @GetMapping("/characters/{characterId}/skills")
    List<Map<String, Object>> characterSkills(@PathVariable int characterId) {
        Map<String, Object> owner = character(characterId);
        List<Integer> jobIds = jobTree(((Number) owner.get("job")).intValue());
        return game.queryForList("""
                SELECT c.entity_id skillid, c.name skill_name, COALESCE(c.job_id, FLOOR(c.entity_id / 10000)) job_id,
                       j.job_name, COALESCE(NULLIF(c.level_value, 0), 30) max_level, c.properties_json,
                       COALESCE(s.skilllevel, 0) skilllevel, COALESCE(s.masterlevel, 0) masterlevel,
                       COALESCE(s.expiration, -1) expiration
                FROM cosmic_database_console.catalog_entities c
                LEFT JOIN cosmic_database_console.catalog_jobs j ON j.job_id = COALESCE(c.job_id, FLOOR(c.entity_id / 10000))
                LEFT JOIN skills s ON s.characterid = :characterId AND s.skillid = c.entity_id
                WHERE c.entity_type = 'SKILL'
                  AND (c.job_id IN (:jobIds) OR FLOOR(c.entity_id / 10000) IN (:jobIds)
                       OR s.characterid IS NOT NULL)
                UNION ALL
                SELECT s.skillid, CONCAT('Skill ', s.skillid) skill_name, FLOOR(s.skillid / 10000) job_id,
                       j.job_name, 30 max_level, NULL properties_json, s.skilllevel, s.masterlevel, s.expiration
                FROM skills s
                LEFT JOIN cosmic_database_console.catalog_entities c ON c.entity_type = 'SKILL' AND c.entity_id = s.skillid
                LEFT JOIN cosmic_database_console.catalog_jobs j ON j.job_id = FLOOR(s.skillid / 10000)
                WHERE s.characterid = :characterId AND c.entity_id IS NULL
                ORDER BY job_id, skillid
                """, new MapSqlParameterSource()
                .addValue("characterId", characterId).addValue("jobIds", jobIds));
    }

    @PatchMapping("/characters/{characterId}/skills/{skillId}")
    @Transactional("gameTransactionManager")
    Map<String, Object> updateCharacterSkill(@PathVariable int characterId, @PathVariable int skillId,
                                             @Valid @RequestBody SkillPatch body, Principal principal,
                                             HttpServletRequest request) {
        lockCharacter(characterId);
        Map<String, Object> ownerCharacter = character(characterId);
        requireOffline(((Number) ownerCharacter.get("accountid")).intValue());
        List<Map<String, Object>> beforeRows = game.queryForList(
                "SELECT * FROM skills WHERE characterid=:characterId AND skillid=:skillId",
                Map.of("characterId", characterId, "skillId", skillId));
        Map<String, Object> before = beforeRows.isEmpty() ? null : beforeRows.getFirst();
        if (body.skillLevel() == 0 && body.masterLevel() == 0) {
            game.update("DELETE FROM skills WHERE characterid=:characterId AND skillid=:skillId",
                    Map.of("characterId", characterId, "skillId", skillId));
        } else {
            game.update("""
                    INSERT INTO skills(characterid, skillid, skilllevel, masterlevel, expiration)
                    VALUES (:characterId, :skillId, :skillLevel, :masterLevel, :expiration)
                    ON DUPLICATE KEY UPDATE skilllevel=:skillLevel, masterlevel=:masterLevel,
                        expiration=:expiration
                    """, new MapSqlParameterSource()
                    .addValue("characterId", characterId).addValue("skillId", skillId)
                    .addValue("skillLevel", body.skillLevel()).addValue("masterLevel", body.masterLevel())
                    .addValue("expiration", body.expiration()));
        }
        Map<String, Object> after = game.queryForList(
                "SELECT * FROM skills WHERE characterid=:characterId AND skillid=:skillId",
                Map.of("characterId", characterId, "skillId", skillId)).stream().findFirst().orElse(Map.of());
        audit.record(principal, "CHARACTER_SKILL_UPDATE", "CHARACTER_SKILL", skillId, body.reason(),
                before, after, "SAVED_OFFLINE", request);
        return after;
    }

    @GetMapping("/characters/{characterId}/inventory")
    List<Map<String, Object>> inventory(@PathVariable int characterId) {
        return game.queryForList("""
                SELECT i.inventoryitemid, i.itemid, i.inventorytype, i.position, i.quantity, i.owner,
                       i.petid, i.flag, i.expiration, i.giftFrom, c.name AS item_name, c.description,
                       c.properties_json,
                       e.upgradeslots, e.level AS upgrades, e.str, e.dex, e.`int`, e.luk, e.hp, e.mp,
                       e.watk, e.matk, e.wdef, e.mdef, e.acc, e.avoid, e.hands, e.speed, e.jump,
                       e.locked, e.vicious, e.itemlevel, e.itemexp, e.ringid
                FROM inventoryitems i
                LEFT JOIN inventoryequipment e ON e.inventoryitemid = i.inventoryitemid
                LEFT JOIN cosmic_database_console.catalog_entities c
                  ON c.entity_type = 'ITEM' AND c.entity_id = i.itemid
                WHERE i.characterid = :characterId
                ORDER BY i.inventorytype, i.position
                """, Map.of("characterId", characterId));
    }

    @PatchMapping("/characters/{characterId}/inventory/{inventoryItemId}")
    @Transactional("gameTransactionManager")
    Map<String, Object> updateInventoryItem(@PathVariable int characterId, @PathVariable long inventoryItemId,
                                            @Valid @RequestBody InventoryItemPatch body, Principal principal,
                                            HttpServletRequest request) {
        lockCharacter(characterId);
        Map<String, Object> ownerCharacter = character(characterId);
        requireOffline(((Number) ownerCharacter.get("accountid")).intValue());
        Map<String, Object> before = inventoryItem(inventoryItemId, characterId);
        int storedInventoryType = ((Number) before.get("inventorytype")).intValue();
        int itemInventoryType = ((Number) before.get("itemid")).intValue() / 1_000_000;
        if (body.itemId() / 1_000_000 != itemInventoryType) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Replacement item must belong to the same inventory category");
        }
        if (body.position() == 0 || itemInventoryType != 1 && body.position() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    itemInventoryType == 1 ? "Equipment position cannot be zero" : "Inventory position must be positive");
        }
        Integer conflict = game.queryForObject("""
                SELECT COUNT(*) FROM inventoryitems WHERE characterid=:characterId
                  AND inventorytype=:inventoryType AND position=:position AND inventoryitemid<>:itemId
                """, new MapSqlParameterSource().addValue("characterId", characterId)
                .addValue("inventoryType", storedInventoryType).addValue("position", body.position())
                .addValue("itemId", inventoryItemId), Integer.class);
        if (conflict != null && conflict > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Target inventory slot is occupied");
        }
        game.update("""
                UPDATE inventoryitems SET itemid=:replacementItemId, position=:position, quantity=:quantity, owner=:owner,
                    flag=:flag, expiration=:expiration, giftFrom=:giftFrom
                WHERE inventoryitemid=:itemId AND characterid=:characterId
                """, new MapSqlParameterSource().addValue("replacementItemId", body.itemId())
                .addValue("position", body.position())
                .addValue("quantity", itemInventoryType == 1 ? 1 : body.quantity())
                .addValue("owner", body.owner()).addValue("flag", body.flag())
                .addValue("expiration", body.expiration()).addValue("giftFrom", body.giftFrom())
                .addValue("itemId", inventoryItemId).addValue("characterId", characterId));
        if (itemInventoryType == 1 && body.equipment() != null) {
            game.update("""
                    UPDATE inventoryequipment SET upgradeslots=:upgradeSlots, level=:level,
                        str=:str, dex=:dex, `int`=:int, luk=:luk, hp=:hp, mp=:mp,
                        watk=:watk, matk=:matk, wdef=:wdef, mdef=:mdef, acc=:acc,
                        avoid=:avoid, hands=:hands, speed=:speed, jump=:jump,
                        locked=:locked, vicious=:vicious, itemlevel=:itemLevel, itemexp=:itemExp
                    WHERE inventoryitemid=:inventoryItemId
                    """, equipmentParameters(inventoryItemId, body.equipment()));
        }
        Map<String, Object> after = inventoryItem(inventoryItemId, characterId);
        audit.record(principal, "INVENTORY_ITEM_UPDATE", "INVENTORY_ITEM", inventoryItemId, body.reason(),
                before, after, "SAVED_OFFLINE", request);
        return after;
    }

    @PostMapping("/characters/{characterId}/inventory/{inventoryItemId}/duplicate")
    @Transactional("gameTransactionManager")
    Map<String, Object> duplicateInventoryItem(@PathVariable int characterId, @PathVariable long inventoryItemId,
                                               @RequestBody ReasonRequest body, Principal principal,
                                               HttpServletRequest request) {
        lockCharacter(characterId);
        Map<String, Object> ownerCharacter = character(characterId);
        requireOffline(((Number) ownerCharacter.get("accountid")).intValue());
        Map<String, Object> before = inventoryItem(inventoryItemId, characterId);
        int inventoryType = ((Number) before.get("inventorytype")).intValue();
        Integer nextSlot = game.queryForObject("""
                SELECT candidate FROM (
                  SELECT ones.n + tens.n*10 + 1 candidate FROM
                  (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION
                   SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) ones
                  CROSS JOIN
                  (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) tens
                ) slots WHERE candidate NOT IN (
                  SELECT position FROM inventoryitems WHERE characterid=:characterId AND inventorytype=:inventoryType
                ) ORDER BY candidate LIMIT 1
                """, Map.of("characterId", characterId, "inventoryType", inventoryType), Integer.class);
        if (nextSlot == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "No empty inventory slot");
        KeyHolder key = new GeneratedKeyHolder();
        game.update("""
                INSERT INTO inventoryitems(type, characterid, accountid, itemid, inventorytype, position,
                    quantity, owner, petid, flag, expiration, giftFrom)
                SELECT type, characterid, accountid, itemid, inventorytype, :position,
                    quantity, owner, -1, flag, expiration, giftFrom
                FROM inventoryitems WHERE inventoryitemid=:itemId AND characterid=:characterId
                """, new MapSqlParameterSource().addValue("position", nextSlot)
                .addValue("itemId", inventoryItemId).addValue("characterId", characterId),
                key, new String[]{"inventoryitemid"});
        long newId = key.getKey().longValue();
        game.update("""
                INSERT INTO inventoryequipment(inventoryitemid, upgradeslots, level, str, dex, `int`, luk,
                    hp, mp, watk, matk, wdef, mdef, acc, avoid, hands, speed, jump, locked, vicious,
                    itemlevel, itemexp, ringid)
                SELECT :newId, upgradeslots, level, str, dex, `int`, luk, hp, mp, watk, matk,
                    wdef, mdef, acc, avoid, hands, speed, jump, locked, vicious, itemlevel, itemexp, -1
                FROM inventoryequipment WHERE inventoryitemid=:itemId
                """, Map.of("newId", newId, "itemId", inventoryItemId));
        Map<String, Object> after = inventoryItem(newId, characterId);
        audit.record(principal, "INVENTORY_ITEM_DUPLICATE", "INVENTORY_ITEM", newId, body.reason(),
                before, after, "SAVED_OFFLINE", request);
        return after;
    }

    @PostMapping("/characters/{characterId}/inventory/swap")
    @Transactional("gameTransactionManager")
    Map<String, Object> swapInventoryItems(@PathVariable int characterId,
                                            @Valid @RequestBody ItemSwap body,
                                            Principal principal, HttpServletRequest request) {
        lockCharacter(characterId);
        Map<String, Object> ownerCharacter = character(characterId);
        requireOffline(((Number) ownerCharacter.get("accountid")).intValue());
        Map<String, Object> first = inventoryItem(body.firstItemId(), characterId);
        Map<String, Object> second = inventoryItem(body.secondItemId(), characterId);
        int firstType = ((Number) first.get("inventorytype")).intValue();
        int secondType = ((Number) second.get("inventorytype")).intValue();
        if (firstType != secondType) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Items can only be swapped inside the same inventory category");
        }
        int firstPosition = ((Number) first.get("position")).intValue();
        int secondPosition = ((Number) second.get("position")).intValue();
        game.update("""
                UPDATE inventoryitems SET position = CASE inventoryitemid
                    WHEN :firstId THEN :secondPosition
                    WHEN :secondId THEN :firstPosition END
                WHERE characterid=:characterId AND inventoryitemid IN (:firstId, :secondId)
                """, new MapSqlParameterSource().addValue("firstId", body.firstItemId())
                .addValue("secondId", body.secondItemId()).addValue("firstPosition", firstPosition)
                .addValue("secondPosition", secondPosition).addValue("characterId", characterId));
        Map<String, Object> after = Map.of(
                "first", inventoryItem(body.firstItemId(), characterId),
                "second", inventoryItem(body.secondItemId(), characterId));
        audit.record(principal, "INVENTORY_ITEMS_SWAP", "CHARACTER", characterId, body.reason(),
                Map.of("first", first, "second", second), after, "SAVED_OFFLINE", request);
        return after;
    }

    @GetMapping("/accounts/{accountId}/storage")
    List<Map<String, Object>> storage(@PathVariable int accountId,
                                      @RequestParam(defaultValue = "0") int world) {
        return game.queryForList("""
                SELECT s.storageid, s.slots, s.meso, s.world, i.inventoryitemid, i.itemid,
                       i.inventorytype, i.position, i.quantity, i.owner, i.flag, i.expiration,
                       c.name item_name, c.description, c.properties_json,
                       e.upgradeslots, e.level upgrades, e.str, e.dex, e.`int`, e.luk, e.hp, e.mp,
                       e.watk, e.matk, e.wdef, e.mdef, e.acc, e.avoid, e.hands, e.speed, e.jump,
                       e.locked, e.vicious, e.itemlevel, e.itemexp, e.ringid
                FROM storages s LEFT JOIN inventoryitems i ON i.type=2 AND i.accountid=s.storageid
                LEFT JOIN inventoryequipment e ON e.inventoryitemid=i.inventoryitemid
                LEFT JOIN cosmic_database_console.catalog_entities c ON c.entity_type='ITEM' AND c.entity_id=i.itemid
                WHERE s.accountid=:accountId AND s.world=:world ORDER BY i.position
                """, Map.of("accountId", accountId, "world", world));
    }

    @PatchMapping("/accounts/{accountId}/storage")
    @Transactional("gameTransactionManager")
    Map<String, Object> updateStorage(@PathVariable int accountId, @Valid @RequestBody StoragePatch body,
                                      Principal principal, HttpServletRequest request) {
        lockAccount(accountId);
        requireOffline(accountId);
        List<Map<String, Object>> rows = game.queryForList("""
                SELECT storageid, accountid, world, slots, meso FROM storages
                WHERE accountid=:accountId AND world=:world FOR UPDATE
                """, Map.of("accountId", accountId, "world", body.world()));
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Storage not found");
        }
        Map<String, Object> before = rows.getFirst();
        game.update("UPDATE storages SET slots=:slots, meso=:meso WHERE storageid=:storageId",
                Map.of("slots", body.slots(), "meso", body.meso(), "storageId", before.get("storageid")));
        Map<String, Object> after = game.queryForMap("""
                SELECT storageid, accountid, world, slots, meso FROM storages WHERE storageid=:storageId
                """, Map.of("storageId", before.get("storageid")));
        audit.record(principal, "STORAGE_UPDATE", "STORAGE", before.get("storageid"), body.reason(),
                before, after, "SAVED_OFFLINE", request);
        return after;
    }

    @PostMapping("/accounts/{accountId}/storage")
    @Transactional("gameTransactionManager")
    Map<String, Object> addStorageItem(@PathVariable int accountId, @Valid @RequestBody StorageItemCreate body,
                                       Principal principal, HttpServletRequest request) {
        lockAccount(accountId);
        requireOffline(accountId);
        List<Map<String, Object>> storages = game.queryForList("""
                SELECT * FROM storages WHERE accountid=:accountId AND world=:world FOR UPDATE
                """, Map.of("accountId", accountId, "world", body.world()));
        if (storages.isEmpty()) {
            game.update("INSERT INTO storages(accountid, world, slots, meso) VALUES (:accountId, :world, 48, 0)",
                    Map.of("accountId", accountId, "world", body.world()));
            storages = game.queryForList("""
                    SELECT * FROM storages WHERE accountid=:accountId AND world=:world FOR UPDATE
                    """, Map.of("accountId", accountId, "world", body.world()));
        }
        Map<String, Object> storage = storages.getFirst();
        int storageId = ((Number) storage.get("storageid")).intValue();
        int slots = ((Number) storage.get("slots")).intValue();
        Integer occupied = game.queryForObject("""
                SELECT COUNT(*) FROM inventoryitems WHERE type=2 AND accountid=:storageId AND position=:position
                """, Map.of("storageId", storageId, "position", body.position()), Integer.class);
        if (body.position() < 0 || body.position() >= slots || occupied != null && occupied > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Storage slot is invalid or occupied");
        }
        int inventoryType = body.itemId() / 1_000_000;
        KeyHolder key = new GeneratedKeyHolder();
        game.update("""
                INSERT INTO inventoryitems(type, characterid, accountid, itemid, inventorytype, position,
                    quantity, owner, petid, flag, expiration, giftFrom)
                VALUES (2, NULL, :storageId, :itemId, :inventoryType, :position,
                    :quantity, '', -1, 0, -1, '')
                """, new MapSqlParameterSource().addValue("storageId", storageId)
                .addValue("itemId", body.itemId()).addValue("inventoryType", inventoryType)
                .addValue("position", body.position()).addValue("quantity", inventoryType == 1 ? 1 : body.quantity()),
                key, new String[]{"inventoryitemid"});
        long itemId = key.getKey().longValue();
        if (inventoryType == 1) insertEquipment(itemId,
                body.equipment() == null ? EquipmentStats.empty() : body.equipment());
        Map<String, Object> after = storageItem(itemId, storageId);
        audit.record(principal, "STORAGE_ITEM_CREATE", "STORAGE_ITEM", itemId, body.reason(), null, after,
                "SAVED_OFFLINE", request);
        return after;
    }

    @PatchMapping("/accounts/{accountId}/storage/{inventoryItemId}")
    @Transactional("gameTransactionManager")
    Map<String, Object> updateStorageItem(@PathVariable int accountId, @PathVariable long inventoryItemId,
                                          @Valid @RequestBody StorageItemPatch body, Principal principal,
                                          HttpServletRequest request) {
        lockAccount(accountId);
        requireOffline(accountId);
        Integer storageId = game.queryForObject("""
                SELECT storageid FROM storages WHERE accountid=:accountId AND world=:world
                """, Map.of("accountId", accountId, "world", body.world()), Integer.class);
        if (storageId == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Storage not found");
        Map<String, Object> before = storageItem(inventoryItemId, storageId);
        Integer conflict = game.queryForObject("""
                SELECT COUNT(*) FROM inventoryitems WHERE type=2 AND accountid=:storageId
                  AND position=:position AND inventoryitemid<>:itemId
                """, Map.of("storageId", storageId, "position", body.position(), "itemId", inventoryItemId),
                Integer.class);
        if (conflict != null && conflict > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Target storage slot is occupied");
        }
        game.update("""
                UPDATE inventoryitems SET position=:position, quantity=:quantity
                WHERE inventoryitemid=:itemId AND type=2 AND accountid=:storageId
                """, Map.of("position", body.position(), "quantity", body.quantity(),
                "itemId", inventoryItemId, "storageId", storageId));
        if (body.equipment() != null) {
            game.update("""
                    UPDATE inventoryequipment SET upgradeslots=:upgradeSlots, level=:level,
                        str=:str, dex=:dex, `int`=:int, luk=:luk, hp=:hp, mp=:mp,
                        watk=:watk, matk=:matk, wdef=:wdef, mdef=:mdef, acc=:acc,
                        avoid=:avoid, hands=:hands, speed=:speed, jump=:jump,
                        locked=:locked, vicious=:vicious, itemlevel=:itemLevel, itemexp=:itemExp
                    WHERE inventoryitemid=:inventoryItemId
                    """, equipmentParameters(inventoryItemId, body.equipment()));
        }
        Map<String, Object> after = storageItem(inventoryItemId, storageId);
        audit.record(principal, "STORAGE_ITEM_UPDATE", "STORAGE_ITEM", inventoryItemId, body.reason(),
                before, after, "SAVED_OFFLINE", request);
        return after;
    }

    @DeleteMapping("/accounts/{accountId}/storage/{inventoryItemId}")
    @Transactional("gameTransactionManager")
    Map<String, Object> deleteStorageItem(@PathVariable int accountId, @PathVariable long inventoryItemId,
                                          @RequestParam int world, @RequestParam @NotBlank String reason,
                                          Principal principal, HttpServletRequest request) {
        lockAccount(accountId);
        requireOffline(accountId);
        Integer storageId = game.queryForObject("""
                SELECT storageid FROM storages WHERE accountid=:accountId AND world=:world
                """, Map.of("accountId", accountId, "world", world), Integer.class);
        Map<String, Object> before = storageItem(inventoryItemId, storageId);
        game.update("DELETE FROM inventoryequipment WHERE inventoryitemid=:id", Map.of("id", inventoryItemId));
        game.update("DELETE FROM inventoryitems WHERE inventoryitemid=:id AND type=2 AND accountid=:storageId",
                Map.of("id", inventoryItemId, "storageId", storageId));
        audit.record(principal, "STORAGE_ITEM_DELETE", "STORAGE_ITEM", inventoryItemId, reason, before, null,
                "SAVED_OFFLINE", request);
        return Map.of("deleted", true);
    }

    @PostMapping("/accounts/{accountId}/storage/swap")
    @Transactional("gameTransactionManager")
    Map<String, Object> swapStorageItems(@PathVariable int accountId,
                                         @Valid @RequestBody StorageItemSwap body,
                                         Principal principal, HttpServletRequest request) {
        lockAccount(accountId);
        requireOffline(accountId);
        Integer storageId = game.queryForObject("""
                SELECT storageid FROM storages WHERE accountid=:accountId AND world=:world
                """, Map.of("accountId", accountId, "world", body.world()), Integer.class);
        if (storageId == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Storage not found");
        Map<String, Object> first = storageItem(body.firstItemId(), storageId);
        Map<String, Object> second = storageItem(body.secondItemId(), storageId);
        int firstPosition = ((Number) first.get("position")).intValue();
        int secondPosition = ((Number) second.get("position")).intValue();
        game.update("""
                UPDATE inventoryitems SET position = CASE inventoryitemid
                    WHEN :firstId THEN :secondPosition
                    WHEN :secondId THEN :firstPosition END
                WHERE type=2 AND accountid=:storageId AND inventoryitemid IN (:firstId, :secondId)
                """, new MapSqlParameterSource().addValue("firstId", body.firstItemId())
                .addValue("secondId", body.secondItemId()).addValue("firstPosition", firstPosition)
                .addValue("secondPosition", secondPosition).addValue("storageId", storageId));
        Map<String, Object> after = Map.of(
                "first", storageItem(body.firstItemId(), storageId),
                "second", storageItem(body.secondItemId(), storageId));
        audit.record(principal, "STORAGE_ITEMS_SWAP", "ACCOUNT", accountId, body.reason(),
                Map.of("first", first, "second", second), after, "SAVED_OFFLINE", request);
        return after;
    }

    @DeleteMapping("/characters/{characterId}/inventory/{inventoryItemId}")
    @Transactional("gameTransactionManager")
    Map<String, Object> deleteInventoryItem(@PathVariable int characterId, @PathVariable long inventoryItemId,
                                            @RequestParam @NotBlank String reason, Principal principal,
                                            HttpServletRequest request) {
        lockCharacter(characterId);
        Map<String, Object> character = character(characterId);
        requireOffline(((Number) character.get("accountid")).intValue());
        List<Map<String, Object>> rows = game.queryForList("""
                SELECT * FROM inventoryitems i LEFT JOIN inventoryequipment e
                    ON e.inventoryitemid = i.inventoryitemid
                WHERE i.inventoryitemid = :itemId AND i.characterid = :characterId
                """, Map.of("itemId", inventoryItemId, "characterId", characterId));
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found");
        }
        game.update("DELETE FROM inventoryequipment WHERE inventoryitemid = :itemId",
                Map.of("itemId", inventoryItemId));
        game.update("DELETE FROM inventoryitems WHERE inventoryitemid = :itemId AND characterid = :characterId",
                Map.of("itemId", inventoryItemId, "characterId", characterId));
        audit.record(principal, "INVENTORY_ITEM_DELETE", "INVENTORY_ITEM", inventoryItemId, reason,
                rows.getFirst(), null, "SAVED_OFFLINE", request);
        return Map.of("deleted", true);
    }

    @PostMapping("/characters/{characterId}/inventory")
    @Transactional("gameTransactionManager")
    Map<String, Object> addInventoryItem(@PathVariable int characterId, @Valid @RequestBody InventoryItemCreate body,
                                         Principal principal, HttpServletRequest request) {
        lockCharacter(characterId);
        Map<String, Object> ownerCharacter = character(characterId);
        requireOffline(((Number) ownerCharacter.get("accountid")).intValue());
        int inventoryType = body.itemId() / 1_000_000;
        if (inventoryType < 1 || inventoryType > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item ID does not map to a valid inventory");
        }
        if (body.position() == 0 || inventoryType != 1 && body.position() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    inventoryType == 1 ? "Equipment position cannot be zero" : "Inventory position must be positive");
        }
        Integer occupied = game.queryForObject("""
                SELECT COUNT(*) FROM inventoryitems
                WHERE characterid = :characterId AND inventorytype = :inventoryType AND position = :position
                """, Map.of("characterId", characterId, "inventoryType", inventoryType,
                "position", body.position()), Integer.class);
        if (occupied != null && occupied > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That inventory slot is already occupied");
        }
        Integer catalogMatches = game.queryForObject("""
                SELECT COUNT(*) FROM cosmic_database_console.catalog_entities
                WHERE entity_type = 'ITEM' AND entity_id = :itemId
                """, Map.of("itemId", body.itemId()), Integer.class);
        if (catalogMatches == null || catalogMatches == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown item ID. Import the WZ catalog before granting items");
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        game.update("""
                INSERT INTO inventoryitems(type, characterid, accountid, itemid, inventorytype, position,
                    quantity, owner, petid, flag, expiration, giftFrom)
                VALUES (1, :characterId, NULL, :itemId, :inventoryType, :position,
                    :quantity, :owner, -1, :flag, :expiration, :giftFrom)
                """, new MapSqlParameterSource()
                .addValue("characterId", characterId).addValue("itemId", body.itemId())
                .addValue("inventoryType", inventoryType).addValue("position", body.position())
                .addValue("quantity", inventoryType == 1 ? 1 : body.quantity())
                .addValue("owner", body.owner()).addValue("flag", body.flag())
                .addValue("expiration", body.expiration()).addValue("giftFrom", body.giftFrom()),
                keyHolder, new String[]{"inventoryitemid"});
        Number generated = keyHolder.getKey();
        if (generated == null) {
            throw new IllegalStateException("MySQL did not return the new inventory item ID");
        }
        long inventoryItemId = generated.longValue();
        if (inventoryType == 1) {
            EquipmentStats stats = body.equipment() == null ? EquipmentStats.empty() : body.equipment();
            insertEquipment(inventoryItemId, stats);
        }
        Map<String, Object> after = inventoryItem(inventoryItemId, characterId);
        audit.record(principal, "INVENTORY_ITEM_CREATE", "INVENTORY_ITEM", inventoryItemId, body.reason(),
                null, after, "SAVED_OFFLINE", request);
        return after;
    }

    @PatchMapping("/characters/{characterId}/inventory/{inventoryItemId}/equipment")
    @Transactional("gameTransactionManager")
    Map<String, Object> updateEquipment(@PathVariable int characterId, @PathVariable long inventoryItemId,
                                        @Valid @RequestBody EquipmentPatch body, Principal principal,
                                        HttpServletRequest request) {
        lockCharacter(characterId);
        Map<String, Object> ownerCharacter = character(characterId);
        requireOffline(((Number) ownerCharacter.get("accountid")).intValue());
        Map<String, Object> before = inventoryItem(inventoryItemId, characterId);
        int updated = game.update("""
                UPDATE inventoryequipment SET upgradeslots = :upgradeSlots, level = :level,
                    str = :str, dex = :dex, `int` = :int, luk = :luk, hp = :hp, mp = :mp,
                    watk = :watk, matk = :matk, wdef = :wdef, mdef = :mdef, acc = :acc,
                    avoid = :avoid, hands = :hands, speed = :speed, jump = :jump,
                    locked = :locked, vicious = :vicious, itemlevel = :itemLevel, itemexp = :itemExp
                WHERE inventoryitemid = :inventoryItemId
                """, equipmentParameters(inventoryItemId, body.stats()));
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inventory item is not equipment");
        }
        Map<String, Object> after = inventoryItem(inventoryItemId, characterId);
        audit.record(principal, "EQUIPMENT_UPDATE", "INVENTORY_ITEM", inventoryItemId, body.reason(),
                before, after, "SAVED_OFFLINE", request);
        return after;
    }

    private void insertEquipment(long inventoryItemId, EquipmentStats stats) {
        game.update("""
                INSERT INTO inventoryequipment(inventoryitemid, upgradeslots, level, str, dex, `int`, luk,
                    hp, mp, watk, matk, wdef, mdef, acc, avoid, hands, speed, jump, locked, vicious,
                    itemlevel, itemexp, ringid)
                VALUES (:inventoryItemId, :upgradeSlots, :level, :str, :dex, :int, :luk,
                    :hp, :mp, :watk, :matk, :wdef, :mdef, :acc, :avoid, :hands, :speed, :jump,
                    :locked, :vicious, :itemLevel, :itemExp, -1)
                """, equipmentParameters(inventoryItemId, stats));
    }

    private void insertStarterEquipment(int characterId, CharacterCreate body) {
        insertEquippedItem(characterId, body.top(), -5);
        insertEquippedItem(characterId, body.bottom(), -6);
        insertEquippedItem(characterId, body.shoes(), -7);
        insertEquippedItem(characterId, body.weapon(), -11);
    }

    private void insertEquippedItem(int characterId, int itemId, int position) {
        if (itemId <= 0) {
            return;
        }
        KeyHolder key = new GeneratedKeyHolder();
        game.update("""
                INSERT INTO inventoryitems(type, characterid, accountid, itemid, inventorytype, position,
                    quantity, owner, petid, flag, expiration, giftFrom)
                VALUES (1, :characterId, NULL, :itemId, -1, :position, 1, '', -1, 0, -1, '')
                """, new MapSqlParameterSource()
                .addValue("characterId", characterId).addValue("itemId", itemId)
                .addValue("position", position), key, new String[]{"inventoryitemid"});
        insertEquipment(key.getKey().longValue(), starterEquipmentStats(itemId));
    }

    private EquipmentStats starterEquipmentStats(int itemId) {
        List<String> rows = game.queryForList("""
                SELECT properties_json FROM cosmic_database_console.catalog_entities
                WHERE entity_type='ITEM' AND entity_id=:itemId
                """, Map.of("itemId", itemId), String.class);
        if (rows.isEmpty() || rows.getFirst() == null || rows.getFirst().isBlank()) {
            return EquipmentStats.empty();
        }
        try {
            JsonNode root = json.readTree(rows.getFirst());
            JsonNode ranges = root.path("statRanges");
            return new EquipmentStats(
                    root.path("tuc").asInt(0), 0,
                    statAverage(ranges, "incSTR"), statAverage(ranges, "incDEX"),
                    statAverage(ranges, "incINT"), statAverage(ranges, "incLUK"),
                    statAverage(ranges, "incMHP"), statAverage(ranges, "incMMP"),
                    statAverage(ranges, "incPAD"), statAverage(ranges, "incMAD"),
                    statAverage(ranges, "incPDD"), statAverage(ranges, "incMDD"),
                    statAverage(ranges, "incACC"), statAverage(ranges, "incEVA"),
                    statAverage(ranges, "incCraft"), statAverage(ranges, "incSpeed"),
                    statAverage(ranges, "incJump"), 0, 0, 1, 0);
        } catch (Exception e) {
            return EquipmentStats.empty();
        }
    }

    private int statAverage(JsonNode ranges, String key) {
        JsonNode range = ranges.path(key);
        if (range.isMissingNode()) {
            return 0;
        }
        return range.path("average").asInt(0);
    }

    private void insertDefaultKeyMap(int characterId) {
        for (int i = 0; i < DEFAULT_KEYS.length; i++) {
            game.update("""
                    INSERT INTO keymap(characterid, `key`, `type`, `action`)
                    VALUES (:characterId, :key, :type, :action)
                    """, new MapSqlParameterSource()
                    .addValue("characterId", characterId).addValue("key", DEFAULT_KEYS[i])
                    .addValue("type", DEFAULT_KEY_TYPES[i]).addValue("action", DEFAULT_KEY_ACTIONS[i]));
        }
    }

    private void ensureStorage(int accountId, int world) {
        Integer exists = game.queryForObject("""
                SELECT COUNT(*) FROM storages WHERE accountid=:accountId AND world=:world
                """, Map.of("accountId", accountId, "world", world), Integer.class);
        if (exists == null || exists == 0) {
            game.update("INSERT INTO storages(accountid, world, slots, meso) VALUES (:accountId, :world, 48, 0)",
                    Map.of("accountId", accountId, "world", world));
        }
    }

    private String characterNameProblem(String name) {
        if (name.isBlank()) {
            return "IGN is required";
        }
        if (!CHARACTER_NAME.matcher(name).matches()) {
            return "IGN must be 4 to 13 letters/numbers and start with a letter";
        }
        return null;
    }

    private boolean characterNameAvailable(String name) {
        Integer existing = game.queryForObject("SELECT COUNT(*) FROM characters WHERE LOWER(name)=LOWER(:name)",
                Map.of("name", name), Integer.class);
        return existing == null || existing == 0;
    }

    private MapSqlParameterSource equipmentParameters(long inventoryItemId, EquipmentStats stats) {
        return new MapSqlParameterSource()
                .addValue("inventoryItemId", inventoryItemId).addValue("upgradeSlots", stats.upgradeSlots())
                .addValue("level", stats.level()).addValue("str", stats.str()).addValue("dex", stats.dex())
                .addValue("int", stats.intStat()).addValue("luk", stats.luk()).addValue("hp", stats.hp())
                .addValue("mp", stats.mp()).addValue("watk", stats.watk()).addValue("matk", stats.matk())
                .addValue("wdef", stats.wdef()).addValue("mdef", stats.mdef()).addValue("acc", stats.acc())
                .addValue("avoid", stats.avoid()).addValue("hands", stats.hands())
                .addValue("speed", stats.speed()).addValue("jump", stats.jump())
                .addValue("locked", stats.locked())
                .addValue("vicious", stats.vicious()).addValue("itemLevel", stats.itemLevel())
                .addValue("itemExp", stats.itemExp());
    }

    private Map<String, Object> inventoryItem(long inventoryItemId, int characterId) {
        List<Map<String, Object>> rows = game.queryForList("""
                SELECT i.*, e.* FROM inventoryitems i
                LEFT JOIN inventoryequipment e ON e.inventoryitemid = i.inventoryitemid
                WHERE i.inventoryitemid = :inventoryItemId AND i.characterid = :characterId
                """, Map.of("inventoryItemId", inventoryItemId, "characterId", characterId));
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found");
        }
        return rows.getFirst();
    }

    private Map<String, Object> storageItem(long inventoryItemId, int storageId) {
        List<Map<String, Object>> rows = game.queryForList("""
                SELECT i.*, e.* FROM inventoryitems i
                LEFT JOIN inventoryequipment e ON e.inventoryitemid=i.inventoryitemid
                WHERE i.inventoryitemid=:itemId AND i.type=2 AND i.accountid=:storageId
                """, Map.of("itemId", inventoryItemId, "storageId", storageId));
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Storage item not found");
        return rows.getFirst();
    }

    private Map<String, Object> account(int id) {
        List<Map<String, Object>> rows = game.queryForList("""
                SELECT id, name, email, loggedin, banned, banreason, mute, nxCredit, maplePoint,
                    nxPrepaid, characterslots, lastlogin, createdat
                FROM accounts WHERE id = :id
                """, Map.of("id", id));
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }
        return rows.getFirst();
    }

    private void lockCharacter(int characterId) {
        List<Map<String, Object>> rows = game.queryForList(
                "SELECT id FROM characters WHERE id = :id FOR UPDATE", Map.of("id", characterId));
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found");
        }
    }

    private void lockAccount(int accountId) {
        if (game.queryForList("SELECT id FROM accounts WHERE id=:id FOR UPDATE", Map.of("id", accountId)).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }
    }

    private Map<String, Object> character(int id) {
        List<Map<String, Object>> rows = game.queryForList("""
                SELECT c.*, a.name account_name, a.loggedin, j.job_name, m.name map_name
                FROM characters c JOIN accounts a ON a.id = c.accountid
                LEFT JOIN cosmic_database_console.catalog_jobs j ON j.job_id=c.job
                LEFT JOIN cosmic_database_console.catalog_entities m ON m.entity_type='MAP' AND m.entity_id=c.map
                WHERE c.id = :id
                """, Map.of("id", id));
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found");
        }
        return rows.getFirst();
    }

    private List<Integer> jobTree(int jobId) {
        List<Integer> jobs = new ArrayList<>();
        jobs.add(0);
        if (jobId <= 0) {
            return jobs;
        }
        int base = jobId >= 1000 ? jobId / 1000 * 1000 : 0;
        if (base != 0 && !jobs.contains(base)) {
            jobs.add(base);
        }
        int first = jobId >= 1000 ? base + (jobId % 1000 / 100) * 100 : jobId / 100 * 100;
        if (first > 0 && !jobs.contains(first)) {
            jobs.add(first);
        }
        int second = jobId >= 1000 ? base + (jobId % 1000 / 10) * 10 : jobId / 10 * 10;
        if (second > first && !jobs.contains(second)) {
            jobs.add(second);
        }
        if (jobId > second && !jobs.contains(jobId)) {
            jobs.add(jobId);
        }
        return jobs;
    }

    private void requireOffline(int accountId) {
        Integer loggedIn = game.queryForObject("SELECT loggedin FROM accounts WHERE id = :id",
                Map.of("id", accountId), Integer.class);
        if (loggedIn != null && loggedIn != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Account is marked online; use the live bridge or disconnect it first");
        }
    }

    public record CharacterPatch(
            @PositiveOrZero int level, @PositiveOrZero int job, @PositiveOrZero int gm, @PositiveOrZero int str,
            @PositiveOrZero int dex, @PositiveOrZero int intStat, @PositiveOrZero int luk,
            @PositiveOrZero int hp, @PositiveOrZero int mp, @PositiveOrZero int maxHp,
            @PositiveOrZero int maxMp, @PositiveOrZero int ap, String sp, @PositiveOrZero int meso,
            int fame, @PositiveOrZero int map, @NotBlank String reason) {}

    public record AppearancePatch(@PositiveOrZero int hair, @PositiveOrZero int face,
                                  @PositiveOrZero int skincolor, @PositiveOrZero int gender,
                                  @NotBlank String reason) {}

    public record SkillPatch(@PositiveOrZero int skillLevel, @PositiveOrZero int masterLevel,
                             long expiration, @NotBlank String reason) {}

    public record AccountPatch(boolean banned, String banReason, boolean mute,
                               @PositiveOrZero int nxCredit, @PositiveOrZero int maplePoint,
                               @PositiveOrZero int nxPrepaid, @PositiveOrZero int characterSlots,
                               @NotBlank String reason) {}

    public record AccountCreate(@NotBlank String name, @NotBlank String password, String email,
                                @PositiveOrZero int gender, @Min(1) @Max(15) int characterSlots,
                                @NotBlank String reason) {
        public AccountCreate {
            email = email == null ? "" : email;
        }
    }

    public record CharacterCreate(@NotBlank String name, @PositiveOrZero int world,
                                  @PositiveOrZero int skincolor, @PositiveOrZero int gender,
                                  @PositiveOrZero int gm,
                                  @PositiveOrZero int hair, @PositiveOrZero int face,
                                  @PositiveOrZero int top, @PositiveOrZero int bottom,
                                  @PositiveOrZero int shoes, @PositiveOrZero int weapon,
                                  @NotBlank String reason) {}

    public record InventoryItemCreate(
            @Min(1) int itemId, int position, @Min(1) int quantity,
            String owner, @PositiveOrZero int flag, long expiration, String giftFrom,
            EquipmentStats equipment, @NotBlank String reason) {
        public InventoryItemCreate {
            owner = owner == null ? "" : owner;
            giftFrom = giftFrom == null ? "" : giftFrom;
        }
    }

    public record InventoryItemPatch(@Min(1) int itemId, int position, @Min(1) int quantity, String owner,
                                     @PositiveOrZero int flag, long expiration, String giftFrom,
                                     EquipmentStats equipment, @NotBlank String reason) {
        public InventoryItemPatch {
            owner = owner == null ? "" : owner;
            giftFrom = giftFrom == null ? "" : giftFrom;
        }
    }

    public record ReasonRequest(@NotBlank String reason) {}

    public record ItemSwap(@PositiveOrZero long firstItemId, @PositiveOrZero long secondItemId,
                           @NotBlank String reason) {}

    public record StorageItemSwap(@PositiveOrZero int world, @PositiveOrZero long firstItemId,
                                  @PositiveOrZero long secondItemId, @NotBlank String reason) {}

    public record StoragePatch(@PositiveOrZero int world, @Min(4) @Max(48) int slots,
                               @PositiveOrZero int meso,
                               @NotBlank String reason) {}

    public record StorageItemCreate(@PositiveOrZero int world, @Min(1) int itemId,
                                    @PositiveOrZero int position, @Min(1) int quantity,
                                    EquipmentStats equipment, @NotBlank String reason) {}

    public record StorageItemPatch(@PositiveOrZero int world, @PositiveOrZero int position,
                                   @Min(1) int quantity, EquipmentStats equipment,
                                   @NotBlank String reason) {}

    public record EquipmentPatch(@Valid @NotNull EquipmentStats stats, @NotBlank String reason) {}

    public record EquipmentStats(
            @PositiveOrZero int upgradeSlots, @PositiveOrZero int level, int str, int dex, int intStat,
            int luk, int hp, int mp, int watk, int matk, int wdef, int mdef, int acc, int avoid,
            int hands, int speed, int jump, @PositiveOrZero int locked, @PositiveOrZero int vicious,
            @PositiveOrZero int itemLevel, @PositiveOrZero int itemExp) {
        static EquipmentStats empty() {
            return new EquipmentStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0);
        }
    }
}
