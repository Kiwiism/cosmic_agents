package com.cosmic.databaseconsole.players;

import com.cosmic.databaseconsole.audit.AuditService;
import com.cosmic.databaseconsole.bridge.BridgeClient;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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
    private final BridgeClient bridge;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper json;
    private final Path wzPath;
    private Map<Integer, Map<String, Object>> questMetadataCache;
    private Map<Integer, Integer> questInfoNumberOwners;

    public PlayerController(@Qualifier("gameJdbc") NamedParameterJdbcTemplate game, AuditService audit,
                            BridgeClient bridge, PasswordEncoder passwordEncoder, ObjectMapper json,
                            @Value("${cosmic.wz-path}") String wzPath) {
        this.game = game;
        this.audit = audit;
        this.bridge = bridge;
        this.passwordEncoder = passwordEncoder;
        this.json = json;
        this.wzPath = Path.of(wzPath).toAbsolutePath().normalize();
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
    Map<String, Object> checkCharacterName(@RequestParam(defaultValue = "") String name,
                                           @RequestParam(required = false) Integer characterId) {
        String trimmed = name.trim();
        String message = characterNameProblem(trimmed);
        if (message != null) {
            return Map.of("name", trimmed, "valid", false, "available", false, "message", message);
        }
        boolean available = characterNameAvailable(trimmed, characterId);
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
        if (!characterNameAvailable(name, null)) {
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
        Map<String, Object> before = character(characterId);
        if (isOnline(before)) {
            bridge.updateAppearance(characterId, appearanceBridgeBody(body));
            Map<String, Object> after = characterForUpdate(characterId);
            audit.record(principal, "CHARACTER_APPEARANCE_UPDATE", "CHARACTER", characterId, body.reason(), before, after,
                    "SAVED_LIVE", request);
            return after;
        }
        lockCharacter(characterId);
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

    @PatchMapping("/characters/{characterId}/name")
    @Transactional("gameTransactionManager")
    Map<String, Object> updateCharacterName(@PathVariable int characterId, @Valid @RequestBody CharacterNamePatch body,
                                            Principal principal, HttpServletRequest request) {
        lockCharacter(characterId);
        Map<String, Object> before = character(characterId);
        requireOffline(((Number) before.get("accountid")).intValue());
        String name = body.name().trim();
        String nameProblem = characterNameProblem(name);
        if (nameProblem != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, nameProblem);
        }
        if (!characterNameAvailable(name, characterId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "IGN is already taken");
        }
        if (!name.equals(before.get("name"))) {
            game.update("UPDATE characters SET name = :name WHERE id = :id",
                    new MapSqlParameterSource().addValue("id", characterId).addValue("name", name));
        }
        Map<String, Object> after = character(characterId);
        audit.record(principal, "CHARACTER_RENAME", "CHARACTER", characterId, body.reason(), before, after,
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

    @GetMapping("/characters/{characterId}/quests")
    Map<String, Object> characterQuests(@PathVariable int characterId) {
        character(characterId);
        List<Map<String, Object>> quests = game.queryForList("""
                SELECT qs.queststatusid, qs.quest, qs.status, qs.time, qs.expires,
                       qs.forfeited, qs.completed, qs.info,
                       GROUP_CONCAT(CONCAT(qp.progressid, ':', qp.progress)
                           ORDER BY qp.progressid SEPARATOR '|') progress_summary
                FROM queststatus qs
                LEFT JOIN questprogress qp ON qp.queststatusid = qs.queststatusid
                WHERE qs.characterid = :characterId
                GROUP BY qs.queststatusid, qs.quest, qs.status, qs.time, qs.expires,
                         qs.forfeited, qs.completed, qs.info
                ORDER BY qs.status, qs.quest
                """, Map.of("characterId", characterId));
        List<Map<String, Object>> progress = game.queryForList("""
                SELECT qp.id, qp.queststatusid, qp.progressid, qp.progress
                FROM questprogress qp
                WHERE qp.characterid = :characterId
                ORDER BY qp.queststatusid, qp.progressid
                """, Map.of("characterId", characterId));
        return Map.of("quests", enrichQuestRows(quests, progress), "progress", progress);
    }

    @PatchMapping("/characters/{characterId}/quests/{questId}")
    @Transactional("gameTransactionManager")
    Map<String, Object> updateCharacterQuest(@PathVariable int characterId, @PathVariable int questId,
                                             @Valid @RequestBody QuestStatusPatch body, Principal principal,
                                             HttpServletRequest request) {
        lockCharacter(characterId);
        Map<String, Object> ownerCharacter = character(characterId);
        requireOffline(((Number) ownerCharacter.get("accountid")).intValue());
        List<Map<String, Object>> beforeRows = game.queryForList("""
                SELECT * FROM queststatus WHERE characterid = :characterId AND quest = :quest
                ORDER BY queststatusid DESC LIMIT 1
                """, Map.of("characterId", characterId, "quest", questId));
        Map<String, Object> before = beforeRows.isEmpty() ? null : beforeRows.getFirst();
        if (body.status() < 0 || body.status() > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quest status must be 0, 1, or 2");
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("characterId", characterId).addValue("quest", questId)
                .addValue("status", body.status()).addValue("time", body.time())
                .addValue("expires", body.expires()).addValue("forfeited", body.forfeited())
                .addValue("completed", body.completed()).addValue("info", body.info());
        if (before == null) {
            game.update("""
                    INSERT INTO queststatus(characterid, quest, status, time, expires, forfeited, completed, info)
                    VALUES (:characterId, :quest, :status, :time, :expires, :forfeited, :completed, :info)
                    """, params);
        } else {
            game.update("""
                    UPDATE queststatus
                    SET status = :status, time = :time, expires = :expires, forfeited = :forfeited,
                        completed = :completed, info = :info
                    WHERE queststatusid = :questStatusId
                    """, params.addValue("questStatusId", before.get("queststatusid")));
        }
        Map<String, Object> after = game.queryForList("""
                SELECT * FROM queststatus WHERE characterid = :characterId AND quest = :quest
                ORDER BY queststatusid DESC LIMIT 1
                """, Map.of("characterId", characterId, "quest", questId)).getFirst();
        audit.record(principal, "CHARACTER_QUEST_STATUS_UPDATE", "CHARACTER_QUEST", questId,
                body.reason(), before, after, "SAVED_OFFLINE", request);
        return after;
    }

    @PostMapping("/characters/{characterId}/quests/{questId}/start")
    @Transactional("gameTransactionManager")
    Map<String, Object> startCharacterQuest(@PathVariable int characterId, @PathVariable int questId,
                                            @Valid @RequestBody ReasonRequest body, Principal principal,
                                            HttpServletRequest request) {
        lockCharacter(characterId);
        Map<String, Object> ownerCharacter = character(characterId);
        requireOffline(((Number) ownerCharacter.get("accountid")).intValue());
        Map<String, Object> before = latestQuestStatus(characterId, questId);
        int now = nowSeconds();
        if (before == null) {
            game.update("""
                    INSERT INTO queststatus(characterid, quest, status, time, expires, forfeited, completed, info)
                    VALUES (:characterId, :quest, 1, :time, 0, 0, 0, 0)
                    """, Map.of("characterId", characterId, "quest", questId, "time", now));
        } else {
            game.update("""
                    UPDATE queststatus
                    SET status = 1, time = :time, expires = 0
                    WHERE queststatusid = :questStatusId
                    """, Map.of("time", now, "questStatusId", before.get("queststatusid")));
        }
        Map<String, Object> after = latestQuestStatus(characterId, questId);
        audit.record(principal, "CHARACTER_QUEST_START", "CHARACTER_QUEST", questId,
                body.reason(), before, after, "SAVED_OFFLINE", request);
        return enrichedQuestRow(after);
    }

    @PostMapping("/characters/{characterId}/quests/{questId}/forfeit")
    @Transactional("gameTransactionManager")
    Map<String, Object> forfeitCharacterQuest(@PathVariable int characterId, @PathVariable int questId,
                                              @Valid @RequestBody ReasonRequest body, Principal principal,
                                              HttpServletRequest request) {
        lockCharacter(characterId);
        Map<String, Object> ownerCharacter = character(characterId);
        requireOffline(((Number) ownerCharacter.get("accountid")).intValue());
        Map<String, Object> before = latestQuestStatus(characterId, questId);
        if (before == null || ((Number) before.get("status")).intValue() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only in-progress quests can be forfeited");
        }
        int forfeited = ((Number) before.getOrDefault("forfeited", 0)).intValue() + 1;
        game.update("""
                UPDATE queststatus
                SET status = 0, time = :time, expires = 0, forfeited = :forfeited, completed = 0
                WHERE queststatusid = :questStatusId
                """, Map.of("time", nowSeconds(), "forfeited", forfeited, "questStatusId", before.get("queststatusid")));
        deleteQuestProgress(characterId, ((Number) before.get("queststatusid")).intValue());
        Map<String, Object> after = latestQuestStatus(characterId, questId);
        audit.record(principal, "CHARACTER_QUEST_FORFEIT", "CHARACTER_QUEST", questId,
                body.reason(), before, after, "SAVED_OFFLINE", request);
        return enrichedQuestRow(after);
    }

    @PostMapping("/characters/{characterId}/quests/{questId}/reset")
    @Transactional("gameTransactionManager")
    Map<String, Object> resetCharacterQuest(@PathVariable int characterId, @PathVariable int questId,
                                            @Valid @RequestBody ReasonRequest body, Principal principal,
                                            HttpServletRequest request) {
        lockCharacter(characterId);
        Map<String, Object> ownerCharacter = character(characterId);
        requireOffline(((Number) ownerCharacter.get("accountid")).intValue());
        Map<String, Object> before = latestQuestStatus(characterId, questId);
        if (before == null) {
            game.update("""
                    INSERT INTO queststatus(characterid, quest, status, time, expires, forfeited, completed, info)
                    VALUES (:characterId, :quest, 0, :time, 0, 0, 0, 0)
                    """, Map.of("characterId", characterId, "quest", questId, "time", nowSeconds()));
        } else {
            game.update("""
                    UPDATE queststatus
                    SET status = 0, time = :time, expires = 0, forfeited = 0, completed = 0, info = 0
                    WHERE queststatusid = :questStatusId
                    """, Map.of("time", nowSeconds(), "questStatusId", before.get("queststatusid")));
            deleteQuestProgress(characterId, ((Number) before.get("queststatusid")).intValue());
        }
        Map<String, Object> after = latestQuestStatus(characterId, questId);
        audit.record(principal, "CHARACTER_QUEST_RESET", "CHARACTER_QUEST", questId,
                body.reason(), before, after, "SAVED_OFFLINE", request);
        return enrichedQuestRow(after);
    }

    @PatchMapping("/characters/{characterId}/quests/{questId}/progress/{progressId}")
    @Transactional("gameTransactionManager")
    Map<String, Object> updateCharacterQuestProgress(@PathVariable int characterId, @PathVariable int questId,
                                                     @PathVariable int progressId,
                                                     @Valid @RequestBody QuestProgressPatch body,
                                                     Principal principal, HttpServletRequest request) {
        lockCharacter(characterId);
        Map<String, Object> ownerCharacter = character(characterId);
        requireOffline(((Number) ownerCharacter.get("accountid")).intValue());
        Map<String, Object> status = questStatusForUpdate(characterId, questId);
        int questStatusId = ((Number) status.get("queststatusid")).intValue();
        List<Map<String, Object>> beforeRows = game.queryForList("""
                SELECT * FROM questprogress
                WHERE characterid = :characterId AND queststatusid = :questStatusId AND progressid = :progressId
                """, new MapSqlParameterSource().addValue("characterId", characterId)
                .addValue("questStatusId", questStatusId).addValue("progressId", progressId));
        Map<String, Object> before = beforeRows.isEmpty() ? null : beforeRows.getFirst();
        if (body.progress().isBlank()) {
            game.update("""
                    DELETE FROM questprogress
                    WHERE characterid = :characterId AND queststatusid = :questStatusId AND progressid = :progressId
                    """, new MapSqlParameterSource().addValue("characterId", characterId)
                    .addValue("questStatusId", questStatusId).addValue("progressId", progressId));
        } else {
            MapSqlParameterSource params = new MapSqlParameterSource().addValue("characterId", characterId)
                    .addValue("questStatusId", questStatusId).addValue("progressId", progressId)
                    .addValue("progress", body.progress());
            if (before == null) {
                game.update("""
                        INSERT INTO questprogress(characterid, queststatusid, progressid, progress)
                        VALUES (:characterId, :questStatusId, :progressId, :progress)
                        """, params);
            } else {
                game.update("""
                        UPDATE questprogress SET progress = :progress
                        WHERE characterid = :characterId AND queststatusid = :questStatusId
                          AND progressid = :progressId
                        """, params);
            }
        }
        Map<String, Object> after = game.queryForList("""
                SELECT * FROM questprogress
                WHERE characterid = :characterId AND queststatusid = :questStatusId AND progressid = :progressId
                """, new MapSqlParameterSource().addValue("characterId", characterId)
                .addValue("questStatusId", questStatusId).addValue("progressId", progressId))
                .stream().findFirst().orElse(Map.of());
        audit.record(principal, "CHARACTER_QUEST_PROGRESS_UPDATE", "CHARACTER_QUEST", questId,
                body.reason(), before, after, "SAVED_OFFLINE", request);
        return after;
    }

    @GetMapping("/quests/{questId}")
    Map<String, Object> questDetail(@PathVariable int questId) {
        Element info = questElement("QuestInfo.img.xml", questId);
        Element check = questElement("Check.img.xml", questId);
        Element act = questElement("Act.img.xml", questId);
        if (info == null && check == null && act == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quest not found in WZ");
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", questId);
        detail.put("name", questInfoName(info, questId));
        detail.put("overview", info == null ? List.of() : questInfoText(info));
        detail.put("properties", info == null ? Map.of() : scalarChildren(info));
        detail.put("requirements", questPhase(check, "0"));
        detail.put("completionCriteria", questPhase(check, "1"));
        detail.put("startActions", questPhase(act, "0"));
        detail.put("completionRewards", questPhase(act, "1"));
        detail.put("technical", Map.of(
                "questInfoSource", "Quest.wz/QuestInfo.img.xml",
                "checkSource", "Quest.wz/Check.img.xml",
                "actSource", "Quest.wz/Act.img.xml",
                "questInfoRaw", info == null ? Map.of() : elementMap(info),
                "checkRaw", check == null ? Map.of() : elementMap(check),
                "actRaw", act == null ? Map.of() : elementMap(act)
        ));
        return detail;
    }

    @GetMapping("/characters/{characterId}/monster-book")
    Map<String, Object> characterMonsterBook(@PathVariable int characterId) {
        Map<String, Object> owner = character(characterId);
        List<Map<String, Object>> cards = game.queryForList("""
                SELECT md.cardid, COALESCE(mb.level, 0) level, md.mobid,
                       COALESCE(m.name, CONCAT('Monster ', md.mobid)) mob_name,
                       COALESCE(m.level_value, 0) level_value,
                       CASE
                         WHEN COALESCE(m.level_value, 0) < 20 THEN 'Beginner'
                         WHEN COALESCE(m.level_value, 0) < 40 THEN 'Basic'
                         WHEN COALESCE(m.level_value, 0) < 70 THEN 'Intermediate'
                         WHEN COALESCE(m.level_value, 0) < 100 THEN 'Advanced'
                         ELSE 'Master'
                       END book_tab
                FROM monstercarddata md
                LEFT JOIN monsterbook mb ON mb.cardid = md.cardid AND mb.charid = :characterId
                LEFT JOIN cosmic_database_console.catalog_entities m
                  ON m.entity_type = 'MOB' AND m.entity_id = md.mobid
                ORDER BY book_tab, level_value, mobid
                """, Map.of("characterId", characterId));
        return Map.of("cover", owner.getOrDefault("monsterbookcover", 0), "cards", cards);
    }

    @PatchMapping("/characters/{characterId}/monster-book/{cardId}")
    @Transactional("gameTransactionManager")
    Map<String, Object> updateMonsterBookCard(@PathVariable int characterId, @PathVariable int cardId,
                                              @Valid @RequestBody MonsterBookPatch body,
                                              Principal principal, HttpServletRequest request) {
        lockCharacter(characterId);
        Map<String, Object> ownerCharacter = character(characterId);
        requireOffline(((Number) ownerCharacter.get("accountid")).intValue());
        List<Map<String, Object>> beforeRows = game.queryForList("""
                SELECT * FROM monsterbook WHERE charid = :characterId AND cardid = :cardId
                """, Map.of("characterId", characterId, "cardId", cardId));
        Map<String, Object> before = beforeRows.isEmpty() ? null : beforeRows.getFirst();
        if (body.level() <= 0) {
            game.update("DELETE FROM monsterbook WHERE charid = :characterId AND cardid = :cardId",
                    Map.of("characterId", characterId, "cardId", cardId));
        } else if (before == null) {
            game.update("""
                    INSERT INTO monsterbook(charid, cardid, level)
                    VALUES (:characterId, :cardId, :level)
                    """, Map.of("characterId", characterId, "cardId", cardId, "level", body.level()));
        } else {
            game.update("""
                    UPDATE monsterbook SET level = :level
                    WHERE charid = :characterId AND cardid = :cardId
                    """, Map.of("characterId", characterId, "cardId", cardId, "level", body.level()));
        }
        Map<String, Object> after = game.queryForList("""
                SELECT * FROM monsterbook WHERE charid = :characterId AND cardid = :cardId
                """, Map.of("characterId", characterId, "cardId", cardId)).stream().findFirst().orElse(Map.of());
        audit.record(principal, "CHARACTER_MONSTER_BOOK_UPDATE", "MONSTER_BOOK_CARD", cardId,
                body.reason(), before, after, "SAVED_OFFLINE", request);
        return after;
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
    @Transactional(transactionManager = "gameTransactionManager", isolation = Isolation.READ_COMMITTED)
    Map<String, Object> updateInventoryItem(@PathVariable int characterId, @PathVariable long inventoryItemId,
                                            @Valid @RequestBody InventoryItemPatch body, Principal principal,
                                            HttpServletRequest request) {
        Map<String, Object> ownerCharacter = character(characterId);
        Map<String, Object> before = inventoryItem(inventoryItemId, characterId);
        int itemInventoryType = ((Number) before.get("itemid")).intValue() / 1_000_000;
        if (body.itemId() / 1_000_000 != itemInventoryType) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Replacement item must belong to the same inventory category");
        }
        if (body.position() == 0 || itemInventoryType != 1 && body.position() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    itemInventoryType == 1 ? "Equipment position cannot be zero" : "Inventory position must be positive");
        }
        if (isOnline(ownerCharacter)) {
            int currentInventoryType = ((Number) before.get("inventorytype")).intValue();
            bridge.mutateInventory(characterId, inventoryUpsertBridgeBody(body,
                    ((Number) before.get("position")).intValue(), currentInventoryType,
                    ((Number) before.get("itemid")).intValue()));
            Map<String, Object> after = inventoryItemAt(characterId,
                    storedInventoryTypeForPosition(itemInventoryType, body.position()), body.position());
            audit.record(principal, "INVENTORY_ITEM_UPDATE", "INVENTORY_ITEM", inventoryItemId, body.reason(),
                    before, after, "SAVED_LIVE", request);
            return after;
        }
        lockCharacter(characterId);
        requireOffline(((Number) ownerCharacter.get("accountid")).intValue());
        int targetInventoryType = storedInventoryTypeForPosition(itemInventoryType, body.position());
        Integer conflict = game.queryForObject("""
                SELECT COUNT(*) FROM inventoryitems WHERE characterid=:characterId
                  AND inventorytype=:inventoryType AND position=:position AND inventoryitemid<>:itemId
                """, new MapSqlParameterSource().addValue("characterId", characterId)
                .addValue("inventoryType", targetInventoryType).addValue("position", body.position())
                .addValue("itemId", inventoryItemId), Integer.class);
        if (conflict != null && conflict > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Target inventory slot is occupied");
        }
        game.update("""
                UPDATE inventoryitems SET itemid=:replacementItemId, inventorytype=:inventoryType,
                    position=:position, quantity=:quantity, owner=:owner, flag=:flag,
                    expiration=:expiration, giftFrom=:giftFrom
                WHERE inventoryitemid=:itemId AND characterid=:characterId
                """, new MapSqlParameterSource().addValue("replacementItemId", body.itemId())
                .addValue("inventoryType", targetInventoryType)
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
    @Transactional(transactionManager = "gameTransactionManager", isolation = Isolation.READ_COMMITTED)
    Map<String, Object> duplicateInventoryItem(@PathVariable int characterId, @PathVariable long inventoryItemId,
                                               @RequestBody ReasonRequest body, Principal principal,
                                               HttpServletRequest request) {
        Map<String, Object> ownerCharacter = character(characterId);
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
        if (isOnline(ownerCharacter)) {
            bridge.mutateInventory(characterId, inventoryRowUpsertBridgeBody(before, nextSlot));
            Map<String, Object> after = inventoryItemAt(characterId, inventoryType, nextSlot);
            audit.record(principal, "INVENTORY_ITEM_DUPLICATE", "INVENTORY_ITEM", after.get("inventoryitemid"),
                    body.reason(), before, after, "SAVED_LIVE", request);
            return after;
        }
        lockCharacter(characterId);
        requireOffline(((Number) ownerCharacter.get("accountid")).intValue());
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
    @Transactional(transactionManager = "gameTransactionManager", isolation = Isolation.READ_COMMITTED)
    Map<String, Object> swapInventoryItems(@PathVariable int characterId,
                                            @Valid @RequestBody ItemSwap body,
                                            Principal principal, HttpServletRequest request) {
        Map<String, Object> ownerCharacter = character(characterId);
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
        if (isOnline(ownerCharacter)) {
            bridge.mutateInventory(characterId, inventorySwapBridgeBody(first, second));
            Map<String, Object> after = Map.of(
                    "first", inventoryItemAt(characterId, firstType, secondPosition),
                    "second", inventoryItemAt(characterId, secondType, firstPosition));
            audit.record(principal, "INVENTORY_ITEMS_SWAP", "CHARACTER", characterId, body.reason(),
                    Map.of("first", first, "second", second), after, "SAVED_LIVE", request);
            return after;
        }
        lockCharacter(characterId);
        requireOffline(((Number) ownerCharacter.get("accountid")).intValue());
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
    @Transactional(transactionManager = "gameTransactionManager", isolation = Isolation.READ_COMMITTED)
    Map<String, Object> updateStorage(@PathVariable int accountId, @Valid @RequestBody StoragePatch body,
                                      Principal principal, HttpServletRequest request) {
        List<Map<String, Object>> rows = game.queryForList("""
                SELECT storageid, accountid, world, slots, meso FROM storages
                WHERE accountid=:accountId AND world=:world
                """, Map.of("accountId", accountId, "world", body.world()));
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Storage not found");
        }
        Map<String, Object> before = rows.getFirst();
        if (accountOnline(accountId)) {
            bridge.mutateStorage(accountId, storageUpdateBridgeBody(body));
            Map<String, Object> after = storageDetails(accountId, body.world());
            audit.record(principal, "STORAGE_UPDATE", "STORAGE", before.get("storageid"), body.reason(),
                    before, after, "SAVED_LIVE", request);
            return after;
        }
        lockAccount(accountId);
        requireOffline(accountId);
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
    @Transactional(transactionManager = "gameTransactionManager", isolation = Isolation.READ_COMMITTED)
    Map<String, Object> addStorageItem(@PathVariable int accountId, @Valid @RequestBody StorageItemCreate body,
                                       Principal principal, HttpServletRequest request) {
        List<Map<String, Object>> storages = game.queryForList("""
                SELECT * FROM storages WHERE accountid=:accountId AND world=:world
                """, Map.of("accountId", accountId, "world", body.world()));
        if (accountOnline(accountId)) {
            if (storages.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Online account storage is not loaded");
            }
            bridge.mutateStorage(accountId, storageUpsertBridgeBody(body, null, null));
            Map<String, Object> after = storageItemAt(accountId, body.world(), body.position());
            audit.record(principal, "STORAGE_ITEM_CREATE", "STORAGE_ITEM", after.get("inventoryitemid"),
                    body.reason(), null, after, "SAVED_LIVE", request);
            return after;
        }
        lockAccount(accountId);
        requireOffline(accountId);
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
        if (body.position() < 1 || body.position() > slots || occupied != null && occupied > 0) {
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
    @Transactional(transactionManager = "gameTransactionManager", isolation = Isolation.READ_COMMITTED)
    Map<String, Object> updateStorageItem(@PathVariable int accountId, @PathVariable long inventoryItemId,
                                          @Valid @RequestBody StorageItemPatch body, Principal principal,
                                          HttpServletRequest request) {
        Integer storageId = game.queryForObject("""
                SELECT storageid FROM storages WHERE accountid=:accountId AND world=:world
                """, Map.of("accountId", accountId, "world", body.world()), Integer.class);
        if (storageId == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Storage not found");
        Map<String, Object> before = storageItem(inventoryItemId, storageId);
        if (accountOnline(accountId)) {
            bridge.mutateStorage(accountId, storageRowUpsertBridgeBody(before, body));
            Map<String, Object> after = storageItemAt(accountId, body.world(), body.position());
            audit.record(principal, "STORAGE_ITEM_UPDATE", "STORAGE_ITEM", inventoryItemId, body.reason(),
                    before, after, "SAVED_LIVE", request);
            return after;
        }
        lockAccount(accountId);
        requireOffline(accountId);
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
    @Transactional(transactionManager = "gameTransactionManager", isolation = Isolation.READ_COMMITTED)
    Map<String, Object> deleteStorageItem(@PathVariable int accountId, @PathVariable long inventoryItemId,
                                          @RequestParam int world, @RequestParam @NotBlank String reason,
                                          Principal principal, HttpServletRequest request) {
        Integer storageId = game.queryForObject("""
                SELECT storageid FROM storages WHERE accountid=:accountId AND world=:world
                """, Map.of("accountId", accountId, "world", world), Integer.class);
        if (storageId == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Storage not found");
        Map<String, Object> before = storageItem(inventoryItemId, storageId);
        if (accountOnline(accountId)) {
            bridge.mutateStorage(accountId, storageDeleteBridgeBody(before, world));
            audit.record(principal, "STORAGE_ITEM_DELETE", "STORAGE_ITEM", inventoryItemId, reason,
                    before, null, "SAVED_LIVE", request);
            return Map.of("deleted", true);
        }
        lockAccount(accountId);
        requireOffline(accountId);
        game.update("DELETE FROM inventoryequipment WHERE inventoryitemid=:id", Map.of("id", inventoryItemId));
        game.update("DELETE FROM inventoryitems WHERE inventoryitemid=:id AND type=2 AND accountid=:storageId",
                Map.of("id", inventoryItemId, "storageId", storageId));
        audit.record(principal, "STORAGE_ITEM_DELETE", "STORAGE_ITEM", inventoryItemId, reason, before, null,
                "SAVED_OFFLINE", request);
        return Map.of("deleted", true);
    }

    @PostMapping("/accounts/{accountId}/storage/swap")
    @Transactional(transactionManager = "gameTransactionManager", isolation = Isolation.READ_COMMITTED)
    Map<String, Object> swapStorageItems(@PathVariable int accountId,
                                         @Valid @RequestBody StorageItemSwap body,
                                         Principal principal, HttpServletRequest request) {
        Integer storageId = game.queryForObject("""
                SELECT storageid FROM storages WHERE accountid=:accountId AND world=:world
                """, Map.of("accountId", accountId, "world", body.world()), Integer.class);
        if (storageId == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Storage not found");
        Map<String, Object> first = storageItem(body.firstItemId(), storageId);
        Map<String, Object> second = storageItem(body.secondItemId(), storageId);
        int firstPosition = ((Number) first.get("position")).intValue();
        int secondPosition = ((Number) second.get("position")).intValue();
        if (accountOnline(accountId)) {
            bridge.mutateStorage(accountId, storageSwapBridgeBody(first, second, body.world()));
            Map<String, Object> after = Map.of(
                    "first", storageItemAt(accountId, body.world(), secondPosition),
                    "second", storageItemAt(accountId, body.world(), firstPosition));
            audit.record(principal, "STORAGE_ITEMS_SWAP", "ACCOUNT", accountId, body.reason(),
                    Map.of("first", first, "second", second), after, "SAVED_LIVE", request);
            return after;
        }
        lockAccount(accountId);
        requireOffline(accountId);
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
    @Transactional(transactionManager = "gameTransactionManager", isolation = Isolation.READ_COMMITTED)
    Map<String, Object> deleteInventoryItem(@PathVariable int characterId, @PathVariable long inventoryItemId,
                                            @RequestParam @NotBlank String reason, Principal principal,
                                            HttpServletRequest request) {
        Map<String, Object> ownerCharacter = character(characterId);
        List<Map<String, Object>> rows = game.queryForList("""
                SELECT * FROM inventoryitems i LEFT JOIN inventoryequipment e
                    ON e.inventoryitemid = i.inventoryitemid
                WHERE i.inventoryitemid = :itemId AND i.characterid = :characterId
                """, Map.of("itemId", inventoryItemId, "characterId", characterId));
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found");
        }
        Map<String, Object> before = rows.getFirst();
        if (isOnline(ownerCharacter)) {
            bridge.mutateInventory(characterId, inventoryDeleteBridgeBody(before));
            audit.record(principal, "INVENTORY_ITEM_DELETE", "INVENTORY_ITEM", inventoryItemId, reason,
                    before, null, "SAVED_LIVE", request);
            return Map.of("deleted", true);
        }
        lockCharacter(characterId);
        requireOffline(((Number) ownerCharacter.get("accountid")).intValue());
        game.update("DELETE FROM inventoryequipment WHERE inventoryitemid = :itemId",
                Map.of("itemId", inventoryItemId));
        game.update("DELETE FROM inventoryitems WHERE inventoryitemid = :itemId AND characterid = :characterId",
                Map.of("itemId", inventoryItemId, "characterId", characterId));
        audit.record(principal, "INVENTORY_ITEM_DELETE", "INVENTORY_ITEM", inventoryItemId, reason,
                before, null, "SAVED_OFFLINE", request);
        return Map.of("deleted", true);
    }

    @PostMapping("/characters/{characterId}/inventory")
    @Transactional(transactionManager = "gameTransactionManager", isolation = Isolation.READ_COMMITTED)
    Map<String, Object> addInventoryItem(@PathVariable int characterId, @Valid @RequestBody InventoryItemCreate body,
                                         Principal principal, HttpServletRequest request) {
        Map<String, Object> ownerCharacter = character(characterId);
        int inventoryType = body.itemId() / 1_000_000;
        if (inventoryType < 1 || inventoryType > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item ID does not map to a valid inventory");
        }
        if (body.position() == 0 || inventoryType != 1 && body.position() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    inventoryType == 1 ? "Equipment position cannot be zero" : "Inventory position must be positive");
        }
        int storedInventoryType = storedInventoryTypeForPosition(inventoryType, body.position());
        Integer catalogMatches = game.queryForObject("""
                SELECT COUNT(*) FROM cosmic_database_console.catalog_entities
                WHERE entity_type = 'ITEM' AND entity_id = :itemId
                """, Map.of("itemId", body.itemId()), Integer.class);
        if (catalogMatches == null || catalogMatches == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown item ID. Import the WZ catalog before granting items");
        }
        if (isOnline(ownerCharacter)) {
            bridge.mutateInventory(characterId, inventoryUpsertBridgeBody(body, null, null, null));
            Map<String, Object> after = inventoryItemAt(characterId, storedInventoryType, body.position());
            audit.record(principal, "INVENTORY_ITEM_CREATE", "INVENTORY_ITEM", after.get("inventoryitemid"),
                    body.reason(), null, after, "SAVED_LIVE", request);
            return after;
        }

        lockCharacter(characterId);
        requireOffline(((Number) ownerCharacter.get("accountid")).intValue());
        Integer occupied = game.queryForObject("""
                SELECT COUNT(*) FROM inventoryitems
                WHERE characterid = :characterId AND inventorytype = :inventoryType AND position = :position
                """, Map.of("characterId", characterId, "inventoryType", storedInventoryType,
                "position", body.position()), Integer.class);
        if (occupied != null && occupied > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That inventory slot is already occupied");
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        game.update("""
                INSERT INTO inventoryitems(type, characterid, accountid, itemid, inventorytype, position,
                    quantity, owner, petid, flag, expiration, giftFrom)
                VALUES (1, :characterId, NULL, :itemId, :inventoryType, :position,
                    :quantity, :owner, -1, :flag, :expiration, :giftFrom)
                """, new MapSqlParameterSource()
                .addValue("characterId", characterId).addValue("itemId", body.itemId())
                .addValue("inventoryType", storedInventoryType).addValue("position", body.position())
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
    @Transactional(transactionManager = "gameTransactionManager", isolation = Isolation.READ_COMMITTED)
    Map<String, Object> updateEquipment(@PathVariable int characterId, @PathVariable long inventoryItemId,
                                        @Valid @RequestBody EquipmentPatch body, Principal principal,
                                        HttpServletRequest request) {
        Map<String, Object> ownerCharacter = character(characterId);
        Map<String, Object> before = inventoryItem(inventoryItemId, characterId);
        if (((Number) before.get("itemid")).intValue() / 1_000_000 != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inventory item is not equipment");
        }
        if (isOnline(ownerCharacter)) {
            int inventoryType = ((Number) before.get("inventorytype")).intValue();
            int position = ((Number) before.get("position")).intValue();
            bridge.mutateInventory(characterId, inventoryRowUpsertBridgeBody(before, position, body.stats()));
            Map<String, Object> after = inventoryItemAt(characterId, inventoryType, position);
            audit.record(principal, "EQUIPMENT_UPDATE", "INVENTORY_ITEM", inventoryItemId, body.reason(),
                    before, after, "SAVED_LIVE", request);
            return after;
        }
        lockCharacter(characterId);
        requireOffline(((Number) ownerCharacter.get("accountid")).intValue());
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

    private boolean isOnline(Map<String, Object> character) {
        Object loggedIn = character.get("loggedin");
        return loggedIn instanceof Number number && number.intValue() != 0;
    }

    private Map<String, Object> appearanceBridgeBody(AppearancePatch body) {
        return Map.of(
                "hair", body.hair(),
                "face", body.face(),
                "skincolor", body.skincolor(),
                "gender", body.gender(),
                "reason", body.reason());
    }

    private boolean accountOnline(int accountId) {
        Integer loggedIn = game.queryForObject("SELECT loggedin FROM accounts WHERE id=:id",
                Map.of("id", accountId), Integer.class);
        return loggedIn != null && loggedIn != 0;
    }

    private Map<String, Object> inventoryUpsertBridgeBody(InventoryItemCreate body, Integer sourcePosition,
                                                           Integer sourceInventoryType, Integer expectedItemId) {
        return inventoryUpsertBridgeBody(body.itemId(), body.position(), body.quantity(), body.owner(), body.flag(),
                body.expiration(), body.giftFrom(), body.equipment(), sourcePosition, sourceInventoryType,
                expectedItemId);
    }

    private Map<String, Object> inventoryUpsertBridgeBody(InventoryItemPatch body, Integer sourcePosition,
                                                           Integer sourceInventoryType, Integer expectedItemId) {
        return inventoryUpsertBridgeBody(body.itemId(), body.position(), body.quantity(), body.owner(), body.flag(),
                body.expiration(), body.giftFrom(), body.equipment(), sourcePosition, sourceInventoryType,
                expectedItemId);
    }

    private Map<String, Object> inventoryUpsertBridgeBody(int itemId, int position, int quantity, String owner,
                                                           int flag, long expiration, String giftFrom,
                                                           EquipmentStats equipment, Integer sourcePosition,
                                                           Integer sourceInventoryType, Integer expectedItemId) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("operation", "UPSERT");
        values.put("itemId", itemId);
        values.put("position", position);
        values.put("quantity", quantity);
        values.put("owner", owner);
        values.put("flag", flag);
        values.put("expiration", expiration);
        values.put("giftFrom", giftFrom);
        values.put("equipment", equipment);
        if (sourcePosition != null) {
            values.put("sourcePosition", sourcePosition);
            values.put("sourceInventoryType", sourceInventoryType);
            values.put("expectedItemId", expectedItemId);
        }
        return values;
    }

    private Map<String, Object> inventoryRowUpsertBridgeBody(Map<String, Object> row, int position) {
        return inventoryUpsertBridgeBody(number(row, "itemid"), position, number(row, "quantity"),
                string(row, "owner"), number(row, "flag"), longNumber(row, "expiration"),
                string(row, "giftFrom"), equipmentStats(row), null, null, null);
    }

    private Map<String, Object> inventoryRowUpsertBridgeBody(Map<String, Object> row, int position,
                                                              EquipmentStats equipment) {
        int inventoryType = number(row, "inventorytype");
        return inventoryUpsertBridgeBody(number(row, "itemid"), position, number(row, "quantity"),
                string(row, "owner"), number(row, "flag"), longNumber(row, "expiration"),
                string(row, "giftFrom"), equipment, number(row, "position"), inventoryType,
                number(row, "itemid"));
    }

    private Map<String, Object> inventoryDeleteBridgeBody(Map<String, Object> row) {
        return Map.of("operation", "DELETE", "inventoryType", number(row, "inventorytype"),
                "position", number(row, "position"), "expectedItemId", number(row, "itemid"));
    }

    private Map<String, Object> inventorySwapBridgeBody(Map<String, Object> first,
                                                         Map<String, Object> second) {
        return Map.of("operation", "SWAP", "inventoryType", number(first, "inventorytype"),
                "firstPosition", number(first, "position"), "firstItemId", number(first, "itemid"),
                "secondPosition", number(second, "position"), "secondItemId", number(second, "itemid"));
    }

    private Map<String, Object> storageUpdateBridgeBody(StoragePatch body) {
        return Map.of("operation", "UPDATE", "world", body.world(), "slots", body.slots(), "meso", body.meso());
    }

    private Map<String, Object> storageUpsertBridgeBody(StorageItemCreate body, Integer sourcePosition,
                                                         Integer expectedItemId) {
        Map<String, Object> values = inventoryUpsertBridgeBody(body.itemId(), body.position(), body.quantity(),
                "", 0, -1, "", body.equipment(), null, null, null);
        values.put("world", body.world());
        if (sourcePosition != null) {
            values.put("sourcePosition", sourcePosition);
            values.put("expectedItemId", expectedItemId);
        }
        return values;
    }

    private Map<String, Object> storageRowUpsertBridgeBody(Map<String, Object> row, StorageItemPatch body) {
        Map<String, Object> values = inventoryUpsertBridgeBody(number(row, "itemid"), body.position(),
                body.quantity(), string(row, "owner"), number(row, "flag"), longNumber(row, "expiration"),
                string(row, "giftFrom"), body.equipment() == null ? equipmentStats(row) : body.equipment(),
                null, null, null);
        values.put("world", body.world());
        values.put("sourcePosition", number(row, "position"));
        values.put("expectedItemId", number(row, "itemid"));
        return values;
    }

    private Map<String, Object> storageDeleteBridgeBody(Map<String, Object> row, int world) {
        return Map.of("operation", "DELETE", "world", world, "position", number(row, "position"),
                "expectedItemId", number(row, "itemid"));
    }

    private Map<String, Object> storageSwapBridgeBody(Map<String, Object> first, Map<String, Object> second,
                                                       int world) {
        return Map.of("operation", "SWAP", "world", world,
                "firstPosition", number(first, "position"), "firstItemId", number(first, "itemid"),
                "secondPosition", number(second, "position"), "secondItemId", number(second, "itemid"));
    }

    private EquipmentStats equipmentStats(Map<String, Object> row) {
        if (number(row, "itemid") / 1_000_000 != 1) {
            return null;
        }
        return new EquipmentStats(number(row, "upgradeslots"), number(row, "level"), number(row, "str"),
                number(row, "dex"), number(row, "int"), number(row, "luk"), number(row, "hp"),
                number(row, "mp"), number(row, "watk"), number(row, "matk"), number(row, "wdef"),
                number(row, "mdef"), number(row, "acc"), number(row, "avoid"), number(row, "hands"),
                number(row, "speed"), number(row, "jump"), number(row, "locked"), number(row, "vicious"),
                number(row, "itemlevel"), number(row, "itemexp"));
    }

    private int number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private long longNumber(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.longValue() : -1;
    }

    private String string(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : value.toString();
    }

    private int storedInventoryTypeForPosition(int inventoryType, int position) {
        return inventoryType == 1 && position < 0 ? -1 : inventoryType;
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

    private boolean characterNameAvailable(String name, Integer characterId) {
        Integer existing = game.queryForObject("""
                SELECT COUNT(*) FROM characters
                WHERE LOWER(name)=LOWER(:name)
                  AND (:characterId IS NULL OR id <> :characterId)
                """, new MapSqlParameterSource().addValue("name", name).addValue("characterId", characterId),
                Integer.class);
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
                SELECT i.*, e.*, c.name AS item_name, c.description, c.properties_json
                FROM inventoryitems i
                LEFT JOIN inventoryequipment e ON e.inventoryitemid = i.inventoryitemid
                LEFT JOIN cosmic_database_console.catalog_entities c
                  ON c.entity_type = 'ITEM' AND c.entity_id = i.itemid
                WHERE i.inventoryitemid = :inventoryItemId AND i.characterid = :characterId
                """, Map.of("inventoryItemId", inventoryItemId, "characterId", characterId));
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found");
        }
        return rows.getFirst();
    }

    private Map<String, Object> inventoryItemAt(int characterId, int inventoryType, int position) {
        List<Map<String, Object>> rows = game.queryForList("""
                SELECT i.*, e.*, c.name AS item_name, c.description, c.properties_json
                FROM inventoryitems i
                LEFT JOIN inventoryequipment e ON e.inventoryitemid = i.inventoryitemid
                LEFT JOIN cosmic_database_console.catalog_entities c
                  ON c.entity_type = 'ITEM' AND c.entity_id = i.itemid
                WHERE i.characterid = :characterId AND i.inventorytype = :inventoryType AND i.position = :position
                ORDER BY i.inventoryitemid DESC LIMIT 1
                """, Map.of("characterId", characterId, "inventoryType", inventoryType, "position", position));
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item was not saved by the live bridge");
        }
        return rows.getFirst();
    }

    private Map<String, Object> storageDetails(int accountId, int world) {
        List<Map<String, Object>> rows = game.queryForList("""
                SELECT storageid, accountid, world, slots, meso FROM storages
                WHERE accountid=:accountId AND world=:world
                """, Map.of("accountId", accountId, "world", world));
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Storage not found");
        }
        return rows.getFirst();
    }

    private Map<String, Object> storageItemAt(int accountId, int world, int position) {
        List<Map<String, Object>> rows = game.queryForList("""
                SELECT i.*, e.*, c.name AS item_name, c.description, c.properties_json
                FROM storages s
                JOIN inventoryitems i ON i.type=2 AND i.accountid=s.storageid
                LEFT JOIN inventoryequipment e ON e.inventoryitemid=i.inventoryitemid
                LEFT JOIN cosmic_database_console.catalog_entities c
                  ON c.entity_type='ITEM' AND c.entity_id=i.itemid
                WHERE s.accountid=:accountId AND s.world=:world AND i.position=:position
                ORDER BY i.inventoryitemid DESC LIMIT 1
                """, Map.of("accountId", accountId, "world", world, "position", position));
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Storage item was not saved by the live bridge");
        }
        return rows.getFirst();
    }

    private Map<String, Object> storageItem(long inventoryItemId, int storageId) {
        List<Map<String, Object>> rows = game.queryForList("""
                SELECT i.*, e.*, c.name AS item_name, c.description, c.properties_json
                FROM inventoryitems i
                LEFT JOIN inventoryequipment e ON e.inventoryitemid=i.inventoryitemid
                LEFT JOIN cosmic_database_console.catalog_entities c ON c.entity_type='ITEM' AND c.entity_id=i.itemid
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

    private Map<String, Object> characterForUpdate(int id) {
        List<Map<String, Object>> rows = game.queryForList("""
                SELECT c.*, a.name account_name, a.loggedin, j.job_name, m.name map_name
                FROM characters c JOIN accounts a ON a.id = c.accountid
                LEFT JOIN cosmic_database_console.catalog_jobs j ON j.job_id=c.job
                LEFT JOIN cosmic_database_console.catalog_entities m ON m.entity_type='MAP' AND m.entity_id=c.map
                WHERE c.id = :id FOR UPDATE
                """, Map.of("id", id));
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found");
        }
        return rows.getFirst();
    }

    private Map<String, Object> questStatusForUpdate(int characterId, int questId) {
        List<Map<String, Object>> rows = game.queryForList("""
                SELECT * FROM queststatus
                WHERE characterid = :characterId AND quest = :quest
                ORDER BY queststatusid DESC LIMIT 1 FOR UPDATE
                """, Map.of("characterId", characterId, "quest", questId));
        if (rows.isEmpty()) {
            game.update("""
                    INSERT INTO queststatus(characterid, quest, status, time, expires, forfeited, completed, info)
                    VALUES (:characterId, :quest, 1, 0, 0, 0, 0, 0)
                    """, Map.of("characterId", characterId, "quest", questId));
            rows = game.queryForList("""
                    SELECT * FROM queststatus
                    WHERE characterid = :characterId AND quest = :quest
                    ORDER BY queststatusid DESC LIMIT 1 FOR UPDATE
                    """, Map.of("characterId", characterId, "quest", questId));
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

    private List<Map<String, Object>> enrichQuestRows(List<Map<String, Object>> quests) {
        return enrichQuestRows(quests, List.of());
    }

    private List<Map<String, Object>> enrichQuestRows(List<Map<String, Object>> quests,
                                                      List<Map<String, Object>> progressRows) {
        List<Map<String, Object>> enriched = new ArrayList<>();
        Map<Integer, Integer> infoOwners = questInfoNumberOwners();
        Map<Integer, List<Map<String, Object>>> infoRowsByOwner = new HashMap<>();
        Map<Integer, List<Map<String, Object>>> progressByStatusId = new HashMap<>();
        for (Map<String, Object> progress : progressRows) {
            if (progress.get("queststatusid") instanceof Number statusId) {
                progressByStatusId.computeIfAbsent(statusId.intValue(), ignored -> new ArrayList<>()).add(progress);
            }
        }
        for (Map<String, Object> quest : quests) {
            Integer questId = quest.get("quest") instanceof Number number ? number.intValue() : null;
            Integer owner = questId == null ? null : infoOwners.get(questId);
            if (owner != null) {
                Map<String, Object> infoRow = new LinkedHashMap<>(quest);
                infoRow.put("info_number", questId);
                infoRow.put("owner_quest", owner);
                if (quest.get("queststatusid") instanceof Number statusId) {
                    infoRow.put("progress_rows", progressByStatusId.getOrDefault(statusId.intValue(), List.of()));
                }
                infoRowsByOwner.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(infoRow);
                continue;
            }
            Map<String, Object> row = enrichedQuestRow(quest);
            if (questId != null) {
                List<Map<String, Object>> infoRows = infoRowsByOwner.get(questId);
                if (infoRows != null) {
                    row.put("info_storage", infoRows);
                }
            }
            enriched.add(row);
        }
        for (Map<String, Object> row : enriched) {
            Object questId = row.get("quest");
            if (questId instanceof Number number && !row.containsKey("info_storage")) {
                List<Map<String, Object>> infoRows = infoRowsByOwner.get(number.intValue());
                if (infoRows != null) {
                    row.put("info_storage", infoRows);
                }
            }
        }
        return enriched;
    }

    private Map<String, Object> enrichedQuestRow(Map<String, Object> quest) {
        if (quest == null) {
            return Map.of();
        }
        Map<String, Object> row = new LinkedHashMap<>(quest);
        Object id = row.get("quest");
        if (id instanceof Number number) {
            row.putAll(questMetadata(number.intValue()));
        }
        return row;
    }

    private Map<String, Object> latestQuestStatus(int characterId, int questId) {
        List<Map<String, Object>> rows = game.queryForList("""
                SELECT qs.queststatusid, qs.quest, qs.status, qs.time, qs.expires,
                       qs.forfeited, qs.completed, qs.info,
                       GROUP_CONCAT(CONCAT(qp.progressid, ':', qp.progress)
                           ORDER BY qp.progressid SEPARATOR '|') progress_summary
                FROM queststatus qs
                LEFT JOIN questprogress qp ON qp.queststatusid = qs.queststatusid
                WHERE qs.characterid = :characterId AND qs.quest = :quest
                GROUP BY qs.queststatusid, qs.quest, qs.status, qs.time, qs.expires,
                         qs.forfeited, qs.completed, qs.info
                ORDER BY qs.queststatusid DESC LIMIT 1
                """, Map.of("characterId", characterId, "quest", questId));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private void deleteQuestProgress(int characterId, int questStatusId) {
        game.update("""
                DELETE FROM questprogress
                WHERE characterid = :characterId AND queststatusid = :questStatusId
                """, Map.of("characterId", characterId, "questStatusId", questStatusId));
        game.update("""
                DELETE FROM medalmaps
                WHERE characterid = :characterId AND queststatusid = :questStatusId
                """, Map.of("characterId", characterId, "questStatusId", questStatusId));
    }

    private int nowSeconds() {
        return (int) (System.currentTimeMillis() / 1000);
    }

    private Map<String, Object> questMetadata(int questId) {
        Map<String, Object> found = questMetadataCache().get(questId);
        if (found != null) {
            return found;
        }
        return Map.of("quest_name", "Quest " + questId, "quest_npcs", List.of());
    }

    private synchronized Map<Integer, Map<String, Object>> questMetadataCache() {
        if (questMetadataCache != null) {
            return questMetadataCache;
        }
        Map<Integer, String> names = new HashMap<>();
        Map<Integer, Set<Integer>> npcIds = new HashMap<>();
        readQuestNames(names);
        readQuestNpcIds(npcIds);
        Map<Integer, String> npcNames = catalogNpcNames();
        Map<Integer, Map<String, Object>> next = new HashMap<>();
        Set<Integer> questIds = new HashSet<>();
        questIds.addAll(names.keySet());
        questIds.addAll(npcIds.keySet());
        for (Integer questId : questIds) {
            List<Map<String, Object>> npcs = new ArrayList<>();
            for (Integer npcId : npcIds.getOrDefault(questId, Set.of())) {
                npcs.add(Map.of("id", npcId, "name", npcNames.getOrDefault(npcId, "NPC " + npcId)));
            }
            next.put(questId, Map.of(
                    "quest_name", names.getOrDefault(questId, "Quest " + questId),
                    "quest_npcs", npcs
            ));
        }
        questMetadataCache = next;
        return questMetadataCache;
    }

    private void readQuestNames(Map<Integer, String> names) {
        Path file = wzPath.resolve("Quest.wz").resolve("QuestInfo.img.xml");
        if (!Files.isRegularFile(file)) {
            return;
        }
        Document document = parseXml(file);
        if (document != null) {
            collectQuestNames(document.getDocumentElement(), names);
        }
    }

    private void readQuestNpcIds(Map<Integer, Set<Integer>> npcIds) {
        Path file = wzPath.resolve("Quest.wz").resolve("Check.img.xml");
        if (!Files.isRegularFile(file)) {
            return;
        }
        Document document = parseXml(file);
        if (document != null) {
            collectQuestNpcIds(document.getDocumentElement(), npcIds);
        }
    }

    private synchronized Map<Integer, Integer> questInfoNumberOwners() {
        if (questInfoNumberOwners != null) {
            return questInfoNumberOwners;
        }
        Map<Integer, Integer> owners = new HashMap<>();
        Path file = wzPath.resolve("Quest.wz").resolve("Check.img.xml");
        if (Files.isRegularFile(file)) {
            Document document = parseXml(file);
            if (document != null) {
                collectQuestInfoNumberOwners(document.getDocumentElement(), owners);
            }
        }
        questInfoNumberOwners = owners;
        return questInfoNumberOwners;
    }

    private Document parseXml(Path file) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(file.toFile());
        } catch (Exception e) {
            return null;
        }
    }

    private void collectQuestNames(Element element, Map<Integer, String> names) {
        Integer questId = numericName(element);
        if (questId != null) {
            for (Element child : elementChildren(element)) {
                if ("string".equals(child.getTagName()) && "name".equals(child.getAttribute("name"))) {
                    names.put(questId, child.getAttribute("value"));
                    break;
                }
            }
        }
        for (Element child : elementChildren(element)) {
            collectQuestNames(child, names);
        }
    }

    private void collectQuestNpcIds(Element root, Map<Integer, Set<Integer>> npcIds) {
        for (Element quest : elementChildren(root)) {
            Integer questId = numericName(quest);
            if (questId == null) {
                continue;
            }
            Set<Integer> ids = new HashSet<>();
            for (Element phase : elementChildren(quest)) {
                for (Element value : elementChildren(phase)) {
                    if ("int".equals(value.getTagName()) && "npc".equals(value.getAttribute("name"))) {
                        Integer npcId = parseInteger(value.getAttribute("value"));
                        if (npcId != null) {
                            ids.add(npcId);
                        }
                    }
                }
            }
            if (!ids.isEmpty()) {
                npcIds.put(questId, ids);
            }
        }
    }

    private void collectQuestInfoNumberOwners(Element root, Map<Integer, Integer> owners) {
        for (Element quest : elementChildren(root)) {
            Integer questId = numericName(quest);
            if (questId == null) {
                continue;
            }
            for (Element phase : elementChildren(quest)) {
                for (Element value : elementChildren(phase)) {
                    if ("int".equals(value.getTagName()) && "infoNumber".equals(value.getAttribute("name"))) {
                        Integer infoNumber = parseInteger(value.getAttribute("value"));
                        if (infoNumber != null) {
                            owners.put(infoNumber, questId);
                        }
                    }
                }
            }
        }
    }

    private List<Element> elementChildren(Element element) {
        List<Element> children = new ArrayList<>();
        NodeList nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element child) {
                children.add(child);
            }
        }
        return children;
    }

    private Integer numericName(Element element) {
        if (!"imgdir".equals(element.getTagName())) {
            return null;
        }
        return parseInteger(element.getAttribute("name"));
    }

    private Integer parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<Integer, String> catalogNpcNames() {
        Map<Integer, String> names = new HashMap<>();
        try {
            List<Map<String, Object>> rows = game.queryForList("""
                    SELECT entity_id, name
                    FROM cosmic_database_console.catalog_entities
                    WHERE entity_type = 'NPC'
                    """, Map.of());
            for (Map<String, Object> row : rows) {
                if (row.get("entity_id") instanceof Number id) {
                    names.put(id.intValue(), String.valueOf(row.getOrDefault("name", "NPC " + id.intValue())));
                }
            }
        } catch (Exception ignored) {
        }
        return names;
    }

    private Element questElement(String fileName, int questId) {
        Path file = wzPath.resolve("Quest.wz").resolve(fileName);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        Document document = parseXml(file);
        if (document == null) {
            return null;
        }
        for (Element child : elementChildren(document.getDocumentElement())) {
            Integer id = numericName(child);
            if (id != null && id == questId) {
                return child;
            }
        }
        return null;
    }

    private String questInfoName(Element info, int questId) {
        if (info != null) {
            for (Element child : elementChildren(info)) {
                if ("string".equals(child.getTagName()) && "name".equals(child.getAttribute("name"))) {
                    return child.getAttribute("value");
                }
            }
        }
        return String.valueOf(questMetadata(questId).getOrDefault("quest_name", "Quest " + questId));
    }

    private List<Map<String, Object>> questInfoText(Element info) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Element child : elementChildren(info)) {
            if ("string".equals(child.getTagName()) && child.getAttribute("name").matches("\\d+")) {
                rows.add(Map.of("stage", child.getAttribute("name"), "text", child.getAttribute("value")));
            }
        }
        return rows;
    }

    private Map<String, Object> questPhase(Element quest, String phaseName) {
        Element phase = namedChild(quest, phaseName);
        if (phase == null) {
            return Map.of("properties", Map.of(), "npcs", List.of(), "items", List.of(),
                    "mobs", List.of(), "quests", List.of(), "jobs", List.of(), "raw", Map.of());
        }
        Map<String, Object> properties = enrichQuestPhaseProperties(scalarChildren(phase));
        List<Map<String, Object>> npcs = new ArrayList<>();
        Object npc = properties.get("npc");
        if (npc instanceof Number id) {
            npcs.add(catalogRef("NPC", id.intValue(), 0, "npc"));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("properties", properties);
        result.put("npcs", npcs);
        result.put("items", questGroup(phase, "item", "ITEM"));
        result.put("mobs", questGroup(phase, "mob", "MOB"));
        result.put("quests", questGroup(phase, "quest", "QUEST"));
        result.put("jobs", questGroup(phase, "job", "JOB"));
        result.put("info", questGroup(phase, "infoex", "INFO"));
        result.put("raw", elementMap(phase));
        return result;
    }

    private List<Map<String, Object>> questGroup(Element phase, String groupName, String type) {
        Element group = namedChild(phase, groupName);
        if (group == null) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Element entry : elementChildren(group)) {
            Map<String, Object> values = scalarChildren(entry);
            int id = values.get("id") instanceof Number number ? number.intValue() : parseInteger(entry.getAttribute("value")) == null ? 0 : parseInteger(entry.getAttribute("value"));
            int count = values.get("count") instanceof Number number ? number.intValue() : 0;
            if ("JOB".equals(type)) {
                Integer direct = parseInteger(entry.getAttribute("value"));
                if (direct != null) {
                    id = direct;
                } else {
                    for (Element child : elementChildren(entry)) {
                        Integer value = parseInteger(child.getAttribute("value"));
                        if (value != null) {
                            id = value;
                            break;
                        }
                    }
                }
            }
            Map<String, Object> row = new LinkedHashMap<>(values);
            row.put("slot", entry.getAttribute("name"));
            row.put("type", type);
            row.put("id", id);
            row.put("count", count);
            row.put("name", questReferenceName(type, id));
            if ("QUEST".equals(type)) {
                addQuestIconNpc(row, id);
            }
            rows.add(row);
        }
        return rows;
    }

    private void addQuestIconNpc(Map<String, Object> row, int questId) {
        Object npcs = questMetadata(questId).get("quest_npcs");
        if (!(npcs instanceof List<?> list) || list.isEmpty() || !(list.getFirst() instanceof Map<?, ?> npc)) {
            return;
        }
        Object id = npc.get("id");
        if (id instanceof Number number) {
            Object name = npc.get("name");
            row.put("iconNpcId", number.intValue());
            row.put("iconNpcName", name == null ? "NPC " + number.intValue() : String.valueOf(name));
        }
    }

    private Map<String, Object> enrichQuestPhaseProperties(Map<String, Object> properties) {
        Map<String, Object> enriched = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            enriched.put(entry.getKey(), entry.getValue());
            if ("npc".equals(entry.getKey()) && entry.getValue() instanceof Number npcId) {
                enriched.put("npcName", questReferenceName("NPC", npcId.intValue()));
            }
            if ("nextQuest".equals(entry.getKey()) && entry.getValue() instanceof Number questId) {
                enriched.put("nextQuestName", questReferenceName("QUEST", questId.intValue()));
            }
        }
        return enriched;
    }

    private Map<String, Object> catalogRef(String type, int id, int count, String slot) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("slot", slot);
        row.put("type", type);
        row.put("id", id);
        row.put("count", count);
        row.put("name", questReferenceName(type, id));
        return row;
    }

    private String questReferenceName(String type, int id) {
        if ("JOB".equals(type)) {
            try {
                List<Map<String, Object>> rows = game.queryForList("""
                        SELECT job_name FROM cosmic_database_console.catalog_jobs WHERE job_id = :id
                        """, Map.of("id", id));
                if (!rows.isEmpty()) {
                    return String.valueOf(rows.getFirst().get("job_name"));
                }
            } catch (Exception ignored) {
            }
            return "Job " + id;
        }
        if (id <= 0) {
            return type + " " + id;
        }
        if ("QUEST".equals(type)) {
            return questInfoName(questElement("QuestInfo.img.xml", id), id);
        }
        try {
            List<Map<String, Object>> rows = game.queryForList("""
                    SELECT name FROM cosmic_database_console.catalog_entities
                    WHERE entity_type = :type AND entity_id = :id
                    """, Map.of("type", type, "id", id));
            if (!rows.isEmpty()) {
                return String.valueOf(rows.getFirst().get("name"));
            }
        } catch (Exception ignored) {
        }
        String stringName = stringWzName(type, id);
        if (stringName != null) {
            return stringName;
        }
        return type + " " + id;
    }

    private String stringWzName(String type, int id) {
        Path source = switch (type) {
            case "NPC" -> wzPath.resolve("String.wz").resolve("Npc.img.xml");
            case "MOB" -> wzPath.resolve("String.wz").resolve("Mob.img.xml");
            case "ITEM" -> null;
            default -> null;
        };
        if (source == null || !Files.isRegularFile(source)) {
            return null;
        }
        Document document = parseXml(source);
        if (document == null) {
            return null;
        }
        Element element = findNamedImgdir(document.getDocumentElement(), String.valueOf(id));
        if (element == null) {
            return null;
        }
        for (Element child : elementChildren(element)) {
            if ("string".equals(child.getTagName()) && "name".equals(child.getAttribute("name"))) {
                return child.getAttribute("value");
            }
        }
        return null;
    }

    private Element findNamedImgdir(Element element, String name) {
        if ("imgdir".equals(element.getTagName()) && name.equals(element.getAttribute("name"))) {
            return element;
        }
        for (Element child : elementChildren(element)) {
            Element found = findNamedImgdir(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private Element namedChild(Element element, String name) {
        if (element == null) {
            return null;
        }
        for (Element child : elementChildren(element)) {
            if ("imgdir".equals(child.getTagName()) && name.equals(child.getAttribute("name"))) {
                return child;
            }
        }
        return null;
    }

    private Map<String, Object> scalarChildren(Element element) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Element child : elementChildren(element)) {
            if ("int".equals(child.getTagName())) {
                Integer value = parseInteger(child.getAttribute("value"));
                values.put(child.getAttribute("name"), value == null ? child.getAttribute("value") : value);
            } else if ("string".equals(child.getTagName())) {
                values.put(child.getAttribute("name"), child.getAttribute("value"));
            }
        }
        return values;
    }

    private Map<String, Object> elementMap(Element element) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tag", element.getTagName());
        row.put("name", element.getAttribute("name"));
        if (element.hasAttribute("value")) {
            row.put("value", element.getAttribute("value"));
        }
        List<Map<String, Object>> children = new ArrayList<>();
        for (Element child : elementChildren(element)) {
            children.add(elementMap(child));
        }
        row.put("children", children);
        return row;
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

    public record CharacterNamePatch(@NotBlank String name, @NotBlank String reason) {}

    public record SkillPatch(@PositiveOrZero int skillLevel, @PositiveOrZero int masterLevel,
                             long expiration, @NotBlank String reason) {}

    public record QuestStatusPatch(@Min(0) @Max(2) int status, @PositiveOrZero int time,
                                   @PositiveOrZero long expires, @PositiveOrZero int forfeited,
                                   @PositiveOrZero int completed, @PositiveOrZero int info,
                                   @NotBlank String reason) {}

    public record QuestProgressPatch(@NotNull String progress, @NotBlank String reason) {}

    public record MonsterBookPatch(@Min(0) @Max(5) int level, @NotBlank String reason) {}

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
