package com.cosmic.databaseconsole.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class CatalogService {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate game;
    private final ObjectMapper json;
    private final Path wzPath;

    public CatalogService(JdbcTemplate jdbc, @Qualifier("gameJdbc") NamedParameterJdbcTemplate game,
                          ObjectMapper json, @Value("${cosmic.wz-path}") String wzPath) {
        this.jdbc = jdbc;
        this.game = game;
        this.json = json;
        this.wzPath = Path.of(wzPath).toAbsolutePath().normalize();
    }

    public Map<String, Object> search(String query, String type, String subtype, String category, Integer minLevel,
                                      Integer maxLevel, Integer jobId, String region, boolean usedOnly,
                                      String sort, String direction,
                                      int page, int size) {
        String order = switch (sort) {
            case "id" -> "entity_id";
            case "level" -> "COALESCE(level_value, 0)";
            case "type" -> "entity_type";
            default -> "name";
        };
        String dir = "desc".equalsIgnoreCase(direction) ? "DESC" : "ASC";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("type", type).addValue("subtype", subtype).addValue("category", category)
                .addValue("query", "%" + query.toLowerCase(Locale.ROOT) + "%")
                .addValue("minLevel", minLevel).addValue("maxLevel", maxLevel)
                .addValue("jobId", jobId).addValue("usedOnly", usedOnly)
                .addValue("limit", size).addValue("offset", page * size);
        params.addValue("region", region.toLowerCase(Locale.ROOT));
        String where = """
                WHERE (:type = '' OR entity_type = :type)
                  AND (:subtype = '' OR subtype = :subtype)
                  AND (
                    :category = ''
                    OR category = :category
                    OR (:category = 'All Weapons' AND category IN (
                      'Weapon','One-Handed Sword','One-Handed Axe','One-Handed Mace','Dagger',
                      'Wand','Staff','Two-Handed Sword','Two-Handed Axe','Two-Handed Mace',
                      'Spear','Polearm','Bow','Crossbow','Claw','Knuckle','Gun','Cash Weapon',
                      'Cash One-Handed Sword','Cash One-Handed Axe','Cash One-Handed Mace',
                      'Cash Dagger','Cash Wand','Cash Staff','Cash Two-Handed Sword',
                      'Cash Two-Handed Axe','Cash Two-Handed Mace','Cash Spear','Cash Polearm',
                      'Cash Bow','Cash Crossbow','Cash Claw','Cash Knuckle','Cash Gun'
                    ))
                    OR (:category = 'All Armors' AND category IN (
                      'Hat','Accessory','Top','Overall','Bottom','Shoes','Gloves','Shield',
                      'Cape','Ring','Cash Hat','Cash Accessory','Cash Top','Cash Overall',
                      'Cash Bottom','Cash Shoes','Cash Gloves','Cash Shield','Cash Cape','Cash Ring'
                    ))
                  )
                  AND (:minLevel IS NULL OR level_value >= :minLevel)
                  AND (:maxLevel IS NULL OR level_value <= :maxLevel)
                  AND (:jobId IS NULL OR job_id = :jobId)
                  AND (:usedOnly = FALSE OR used_in_game = TRUE)
                  AND (:region = '' OR entity_type <> 'MOB' OR EXISTS (
                      SELECT 1 FROM catalog_map_life ml WHERE ml.life_type='m'
                        AND ml.entity_id=catalog_entities.entity_id AND ml.region_code=:region))
                  AND (:query = '%%' OR LOWER(name) LIKE :query OR CAST(entity_id AS CHAR) LIKE :query
                       OR LOWER(search_text) LIKE :query)
                """;
        List<Map<String, Object>> rows = new NamedParameterJdbcTemplate(jdbc).queryForList("""
                SELECT entity_type, entity_id, name, description, category, subtype, level_value,
                       job_id, (SELECT job_name FROM catalog_jobs j WHERE j.job_id=catalog_entities.job_id) job_name,
                       (SELECT m.name FROM catalog_map_life ml JOIN catalog_entities m
                          ON m.entity_type='MAP' AND m.entity_id=ml.map_id
                        WHERE catalog_entities.entity_type='NPC' AND ml.life_type='n'
                          AND ml.entity_id=catalog_entities.entity_id ORDER BY ml.spawn_count DESC,m.name LIMIT 1) location_name,
                       used_in_game, source_path, properties_json, indexed_at
                FROM catalog_entities
                """ + where + " ORDER BY " + order + " " + dir + ", entity_id ASC LIMIT :limit OFFSET :offset", params);
        Long total = new NamedParameterJdbcTemplate(jdbc).queryForObject(
                "SELECT COUNT(*) FROM catalog_entities " + where, params, Long.class);
        return Map.of("items", rows, "page", page, "size", size, "total", total == null ? 0 : total,
                "pages", total == null ? 0 : (total + size - 1) / size);
    }

    public List<Map<String, Object>> suggest(String query, String type, String subtype, int limit) {
        return new NamedParameterJdbcTemplate(jdbc).queryForList("""
                SELECT entity_type, entity_id, name, description, subtype, level_value, properties_json
                FROM catalog_entities
                WHERE (:type = '' OR entity_type = :type)
                  AND (:subtype = '' OR subtype = :subtype)
                  AND (LOWER(name) LIKE :query OR CAST(entity_id AS CHAR) LIKE :query)
                ORDER BY CASE WHEN CAST(entity_id AS CHAR) = :exact THEN 0
                              WHEN LOWER(name) = LOWER(:exact) THEN 1 ELSE 2 END, name
                LIMIT :limit
                """, new MapSqlParameterSource().addValue("type", type).addValue("subtype", subtype)
                .addValue("query", "%" + query.toLowerCase(Locale.ROOT) + "%")
                .addValue("exact", query).addValue("limit", limit));
    }

    public Map<String, Object> detail(String type, int id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT * FROM catalog_entities WHERE entity_type = ? AND entity_id = ?
                """, type, id);
        if (rows.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Catalog entity not found");
        }
        Map<String, Object> result = new LinkedHashMap<>(rows.getFirst());
        if (result.get("job_id") != null) {
            List<Map<String, Object>> jobs = jdbc.queryForList(
                    "SELECT job_name FROM catalog_jobs WHERE job_id=?", result.get("job_id"));
            if (!jobs.isEmpty()) result.put("job_name", jobs.getFirst().get("job_name"));
        }
        if ("ITEM".equals(type)) {
            result.put("droppedBy", game.queryForList("""
                    SELECT d.dropperid AS id, m.name, m.level_value AS level, d.chance,
                           d.minimum_quantity, d.maximum_quantity
                    FROM drop_data d LEFT JOIN cosmic_database_console.catalog_entities m
                      ON m.entity_type='MOB' AND m.entity_id=d.dropperid
                    WHERE d.itemid=:id ORDER BY d.chance DESC
                    """, Map.of("id", id)));
            result.put("soldBy", game.queryForList("""
                    SELECT s.shopid, s.npcid AS id, n.name, si.price, si.position,
                           GROUP_CONCAT(DISTINCT CONCAT(ml.map_id, ':', COALESCE(m.name, ml.map_id))
                             ORDER BY m.name SEPARATOR ' | ') locations
                    FROM shopitems si JOIN shops s ON s.shopid=si.shopid
                    LEFT JOIN cosmic_database_console.catalog_entities n ON n.entity_type='NPC' AND n.entity_id=s.npcid
                    LEFT JOIN cosmic_database_console.catalog_map_life ml ON ml.life_type='n' AND ml.entity_id=s.npcid
                    LEFT JOIN cosmic_database_console.catalog_entities m ON m.entity_type='MAP' AND m.entity_id=ml.map_id
                    WHERE si.itemid=:id GROUP BY s.shopid,s.npcid,n.name,si.price,si.position
                    ORDER BY n.name, si.position
                    """, Map.of("id", id)));
            result.put("ownedBy", game.queryForList("""
                    SELECT c.id, c.name, i.quantity, i.inventorytype, i.position
                    FROM inventoryitems i JOIN characters c ON c.id=i.characterid
                    WHERE i.itemid=:id ORDER BY c.name LIMIT 200
                    """, Map.of("id", id)));
            result.put("gachapon", jdbc.queryForList("""
                    SELECT g.location_code, g.npc_id, g.tier, g.position, g.source_kind,
                           counts.tier_count
                    FROM console_gachapon_entries g
                    JOIN (
                        SELECT location_code, tier, COUNT(*) tier_count
                        FROM console_gachapon_entries
                        WHERE enabled=TRUE
                        GROUP BY location_code, tier
                    ) counts ON counts.location_code=g.location_code AND counts.tier=g.tier
                    WHERE g.item_id=? AND g.enabled=TRUE
                    ORDER BY g.location_code,g.tier,g.position
                    """, id));
        } else if ("MOB".equals(type)) {
            result.put("drops", game.queryForList("""
                    SELECT d.*, i.name AS item_name, i.description
                    FROM drop_data d LEFT JOIN cosmic_database_console.catalog_entities i
                      ON i.entity_type='ITEM' AND i.entity_id=d.itemid
                    WHERE d.dropperid=:id ORDER BY d.chance DESC
                    """, Map.of("id", id)));
            result.put("spawns", jdbc.queryForList("""
                    SELECT ml.map_id, m.name map_name, ml.region_code, ml.region_name, ml.spawn_count,
                           ml.source_path
                    FROM catalog_map_life ml LEFT JOIN catalog_entities m
                      ON m.entity_type='MAP' AND m.entity_id=ml.map_id
                    WHERE ml.life_type='m' AND ml.entity_id=? ORDER BY ml.region_name,m.name
                    """, id));
        } else if ("NPC".equals(type)) {
            result.put("shops", game.queryForList("""
                    SELECT s.shopid, COUNT(si.shopitemid) item_count FROM shops s
                    LEFT JOIN shopitems si ON si.shopid=s.shopid WHERE s.npcid=:id GROUP BY s.shopid
                    """, Map.of("id", id)));
            result.put("locations", jdbc.queryForList("""
                    SELECT ml.map_id, m.name map_name, ml.region_code, ml.region_name, ml.spawn_count,
                           ml.source_path
                    FROM catalog_map_life ml LEFT JOIN catalog_entities m
                      ON m.entity_type='MAP' AND m.entity_id=ml.map_id
                    WHERE ml.life_type='n' AND ml.entity_id=? ORDER BY ml.region_name,m.name
                    """, id));
        } else if ("SKILL".equals(type)) {
            result.put("levels", jdbc.queryForList("""
                    SELECT skill_level, properties_json, source_path FROM catalog_skill_levels
                    WHERE skill_id=? ORDER BY skill_level
                    """, id));
        } else if ("MAP".equals(type)) {
            result.put("mobs", jdbc.queryForList("""
                    SELECT ml.entity_id, e.name, e.level_value, ml.spawn_count, ml.region_name
                    FROM catalog_map_life ml LEFT JOIN catalog_entities e
                      ON e.entity_type='MOB' AND e.entity_id=ml.entity_id
                    WHERE ml.map_id=? AND ml.life_type='m' ORDER BY e.level_value,e.name
                    """, id));
            result.put("npcs", jdbc.queryForList("""
                    SELECT ml.entity_id, e.name, ml.spawn_count, ml.region_name
                    FROM catalog_map_life ml LEFT JOIN catalog_entities e
                      ON e.entity_type='NPC' AND e.entity_id=ml.entity_id
                    WHERE ml.map_id=? AND ml.life_type='n' ORDER BY e.name
                    """, id));
            result.put("portals", jdbc.queryForList("""
                    SELECT p.portal_index, p.portal_name, p.portal_type, p.target_map_id,
                           p.target_portal_name, p.x, p.y, p.source_path, m.name target_map_name
                    FROM catalog_map_portals p LEFT JOIN catalog_entities m
                      ON m.entity_type='MAP' AND m.entity_id=p.target_map_id
                    WHERE p.map_id=? AND p.target_map_id<>999999999 AND p.target_map_id<>p.map_id
                      AND m.entity_id IS NOT NULL
                    ORDER BY p.portal_index
                    """, id));
        }
        return result;
    }

    public List<Map<String, Object>> regions() {
        return jdbc.queryForList("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(e.properties_json,'$.regionCode')) region_code,
                       MAX(JSON_UNQUOTE(JSON_EXTRACT(e.properties_json,'$.regionName'))) region_name,
                       COUNT(DISTINCT e.entity_id) map_count,
                       COUNT(DISTINCT CASE WHEN ml.life_type='m' THEN ml.entity_id END) mob_count,
                       MIN(e.entity_id) representative_map_id
                FROM catalog_entities e LEFT JOIN catalog_map_life ml ON ml.map_id=e.entity_id
                WHERE e.entity_type='MAP'
                  AND JSON_UNQUOTE(JSON_EXTRACT(e.properties_json,'$.regionCode')) IS NOT NULL
                GROUP BY JSON_UNQUOTE(JSON_EXTRACT(e.properties_json,'$.regionCode'))
                ORDER BY region_name
                """);
    }

    public List<Map<String, Object>> regionMobs(String region) {
        return jdbc.queryForList("""
                SELECT ml.entity_id, m.name, m.level_value, m.properties_json, SUM(ml.spawn_count) spawn_count,
                       COUNT(DISTINCT ml.map_id) map_count, MAX(ml.region_name) region_name
                FROM catalog_map_life ml JOIN catalog_entities m
                  ON m.entity_type='MOB' AND m.entity_id=ml.entity_id
                WHERE ml.life_type='m' AND ml.region_code=?
                GROUP BY ml.entity_id,m.name,m.level_value,m.properties_json ORDER BY m.level_value,m.name
                """, region.toLowerCase(Locale.ROOT));
    }

    public List<Map<String, Object>> regionMaps(String region) {
        return jdbc.queryForList("""
                SELECT e.entity_id, e.name, e.description, e.properties_json, e.source_path,
                       COUNT(DISTINCT CASE WHEN ml.life_type='m' THEN ml.entity_id END) mob_count,
                       COUNT(DISTINCT CASE WHEN ml.life_type='n' THEN ml.entity_id END) npc_count,
                       COALESCE(SUM(CASE WHEN ml.life_type='m' THEN ml.spawn_count ELSE 0 END),0) spawn_count,
                       JSON_UNQUOTE(JSON_EXTRACT(e.properties_json,'$.townName')) town_name,
                       COALESCE(JSON_EXTRACT(e.properties_json,'$.isTown')=TRUE,FALSE) is_town
                FROM catalog_entities e
                LEFT JOIN catalog_map_life ml ON ml.map_id=e.entity_id
                WHERE e.entity_type='MAP'
                  AND JSON_UNQUOTE(JSON_EXTRACT(e.properties_json,'$.regionCode'))=?
                GROUP BY e.entity_id,e.name,e.description,e.properties_json,e.source_path
                ORDER BY is_town DESC, town_name, e.name,e.entity_id
                """, region.toLowerCase(Locale.ROOT));
    }

    public List<Map<String, Object>> jobs() {
        return jdbc.queryForList("""
                SELECT j.job_id,j.job_name,j.source_path,MIN(e.entity_id) icon_skill_id
                FROM catalog_jobs j LEFT JOIN catalog_entities e
                  ON e.entity_type='SKILL' AND e.job_id=j.job_id
                GROUP BY j.job_id,j.job_name,j.source_path ORDER BY j.job_id
                """);
    }

    @Transactional
    public Map<String, Object> importCatalog() {
        long runId = startRun();
        int entities = 0;
        int files = 0;
        int errors = 0;
        List<String> errorMessages = new ArrayList<>();
        List<StringFile> definitions = List.of(
                new StringFile("ITEM", "EQUIP", wzPath.resolve("String.wz/Eqp.img.xml")),
                new StringFile("ITEM", "CONSUME", wzPath.resolve("String.wz/Consume.img.xml")),
                new StringFile("ITEM", "ETC", wzPath.resolve("String.wz/Etc.img.xml")),
                new StringFile("ITEM", "SETUP", wzPath.resolve("String.wz/Ins.img.xml")),
                new StringFile("ITEM", "CASH", wzPath.resolve("String.wz/Cash.img.xml")),
                new StringFile("MOB", "MONSTER", wzPath.resolve("String.wz/Mob.img.xml")),
                new StringFile("NPC", "NPC", wzPath.resolve("String.wz/Npc.img.xml")),
                new StringFile("SKILL", "SKILL", wzPath.resolve("String.wz/Skill.img.xml")),
                new StringFile("MAP", "MAP", wzPath.resolve("String.wz/Map.img.xml"))
        );
        for (StringFile definition : definitions) {
            if (!Files.isRegularFile(definition.path())) {
                errors++;
                continue;
            }
            files++;
            try {
                entities += importStringFile(definition);
            } catch (Exception exception) {
                errors++;
                errorMessages.add(relative(definition.path()) + ": " + exception.getMessage());
            }
        }
        try { files += enrichMobs(); } catch (Exception exception) {
            errors++; errorMessages.add("Mob.wz: " + exception.getMessage());
        }
        try { files += enrichEquips(); } catch (Exception exception) {
            errors++; errorMessages.add("Character.wz: " + exception.getMessage());
        }
        try { files += enrichItems(); } catch (Exception exception) {
            errors++; errorMessages.add("Item.wz: " + exception.getMessage());
        }
        try { files += enrichSkills(); } catch (Exception exception) {
            errors++; errorMessages.add("Skill.wz: " + exception.getMessage());
        }
        try { files += enrichMaps(); } catch (Exception exception) {
            errors++; errorMessages.add("Map.wz: " + exception.getMessage());
        }
        try { enrichJobs(); } catch (Exception exception) {
            errors++; errorMessages.add("Jobs: " + exception.getMessage());
        }
        try { refreshUsageFlags(); } catch (Exception exception) {
            errors++; errorMessages.add("Usage flags: " + exception.getMessage());
        }
        jdbc.update("""
                UPDATE catalog_import_runs SET status = ?, files_seen = ?, entities_written = ?,
                errors = ?, finished_at = CURRENT_TIMESTAMP, message = ? WHERE id = ?
                """, errors == 0 ? "COMPLETED" : "COMPLETED_WITH_ERRORS", files, entities, errors,
                "Imported String, Mob, Item, Character, Skill and Map metadata", runId);
        return Map.of("runId", runId, "files", files, "entities", entities, "errors", errors,
                "errorMessages", errorMessages, "wzRoot", wzPath.toString());
    }

    private int importStringFile(StringFile definition) throws Exception {
        Document document = factory().newDocumentBuilder().parse(definition.path().toFile());
        List<CatalogEntity> entities = new ArrayList<>();
        collectEntities(document.getDocumentElement(), definition, entities);
        jdbc.batchUpdate("""
                INSERT INTO catalog_entities(entity_type, entity_id, name, description, category, subtype,
                    source_path, properties_json, search_text)
                VALUES (?, ?, ?, ?, ?, ?, ?, JSON_OBJECT('stringSource', ?), ?)
                ON DUPLICATE KEY UPDATE name=VALUES(name), description=VALUES(description),
                    category=VALUES(category), subtype=VALUES(subtype), source_path=VALUES(source_path),
                    search_text=VALUES(search_text),
                    properties_json=JSON_SET(COALESCE(properties_json, JSON_OBJECT()), '$.stringSource', VALUES(source_path))
                """, entities, 500, (PreparedStatement ps, CatalogEntity entity) -> {
            ps.setString(1, entity.type()); ps.setInt(2, entity.id()); ps.setString(3, entity.name());
            ps.setString(4, entity.description()); ps.setString(5, entity.category());
            ps.setString(6, entity.subtype()); ps.setString(7, relative(definition.path()));
            ps.setString(8, relative(definition.path()));
            ps.setString(9, entity.id() + " " + entity.name() + " " + entity.description());
        });
        return entities.size();
    }

    private int enrichMobs() throws Exception {
        int files = 0;
        try (Stream<Path> paths = Files.list(wzPath.resolve("Mob.wz"))) {
            for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".img.xml")).toList()) {
                files++;
                Element root = factory().newDocumentBuilder().parse(path.toFile()).getDocumentElement();
                int id = numericFileId(path);
                Element info = directDirectory(root, "info");
                if (info == null) continue;
                Map<String, Object> values = scalarChildren(info);
                String imageAction = childDirectories(root).stream()
                        .map(element -> element.getAttribute("name"))
                        .filter(name -> Set.of("stand", "move", "fly").contains(name))
                        .findFirst().orElse("stand");
                values.put("imageAction", imageAction);
                Integer level = number(values.get("level"));
                updateMetadata("MOB", id, "MONSTER", level, null, path, values);
            }
        }
        return files;
    }

    private int enrichEquips() throws Exception {
        int files = 0;
        try (Stream<Path> paths = Files.walk(wzPath.resolve("Character.wz"))) {
            for (Path path : paths.filter(p -> p.getFileName().toString().matches("\\d{8}\\.img\\.xml")).toList()) {
                files++;
                Element root = factory().newDocumentBuilder().parse(path.toFile()).getDocumentElement();
                Element info = directDirectory(root, "info");
                if (info == null) continue;
                int id = numericFileId(path);
                Map<String, Object> values = scalarChildren(info);
                values.put("statRanges", equipmentRanges(values));
                values.put("rangeFormulaSource", "src/main/java/server/ItemInformationProvider.java#randomizeStats");
                String folder = path.getParent().getFileName().toString();
                String subtype = switch (folder) {
                    case "Face" -> "FACE";
                    case "Hair" -> "HAIR";
                    default -> "EQUIP";
                };
                boolean cash = number(values.get("cash")) != null && number(values.get("cash")) == 1;
                values.put("cashEquip", cash);
                updateMetadata("ITEM", id, subtype, number(values.get("reqLevel")), number(values.get("reqJob")),
                        path, values);
                jdbc.update("UPDATE catalog_entities SET category=? WHERE entity_type='ITEM' AND entity_id=?",
                        equipCategory(folder, id, cash), id);
            }
        }
        return files;
    }

    private int enrichItems() throws Exception {
        int files = 0;
        for (String folder : List.of("Consume", "Etc", "Install", "Cash")) {
            Path rootPath = wzPath.resolve("Item.wz").resolve(folder);
            if (!Files.isDirectory(rootPath)) continue;
            try (Stream<Path> paths = Files.walk(rootPath)) {
                for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".img.xml")).toList()) {
                    files++;
                    Element root = factory().newDocumentBuilder().parse(path.toFile()).getDocumentElement();
                    NodeList children = root.getChildNodes();
                    for (int i = 0; i < children.getLength(); i++) {
                        if (!(children.item(i) instanceof Element item) || !item.getAttribute("name").matches("\\d{8}")) continue;
                        int id = Integer.parseInt(item.getAttribute("name"));
                        Map<String, Object> values = new LinkedHashMap<>();
                        Element info = directDirectory(item, "info");
                        Element spec = directDirectory(item, "spec");
                        if (info != null) values.putAll(scalarChildren(info));
                        if (spec != null) values.put("effects", scalarChildren(spec));
                        String subtype = "Install".equals(folder)
                                ? "SETUP"
                                : folder.toUpperCase(Locale.ROOT);
                        updateMetadata("ITEM", id, subtype, null, null, path, values);
                        jdbc.update("UPDATE catalog_entities SET category=? WHERE entity_type='ITEM' AND entity_id=?",
                                itemCategory(folder, id, values), id);
                    }
                }
            }
        }
        return files;
    }

    private int enrichSkills() throws Exception {
        jdbc.update("DELETE FROM catalog_skill_levels");
        int files = 0;
        try (Stream<Path> paths = Files.list(wzPath.resolve("Skill.wz"))) {
            for (Path path : paths.filter(p -> p.getFileName().toString().matches("\\d+\\.img\\.xml")).toList()) {
                files++;
                int jobId = numericFileId(path);
                Element root = factory().newDocumentBuilder().parse(path.toFile()).getDocumentElement();
                Element skills = directDirectory(root, "skill");
                if (skills == null) continue;
                for (Element skill : childDirectories(skills)) {
                    if (!skill.getAttribute("name").matches("\\d+")) continue;
                    int skillId = Integer.parseInt(skill.getAttribute("name"));
                    Element levels = directDirectory(skill, "level");
                    int maxLevel = 0;
                    if (levels != null) {
                        for (Element level : childDirectories(levels)) {
                            if (!level.getAttribute("name").matches("\\d+")) continue;
                            int value = Integer.parseInt(level.getAttribute("name"));
                            maxLevel = Math.max(maxLevel, value);
                            jdbc.update("""
                                    INSERT INTO catalog_skill_levels(skill_id, skill_level, properties_json, source_path)
                                    VALUES (?, ?, CAST(? AS JSON), ?)
                                    """, skillId, value, encode(scalarChildren(level)), relative(path));
                        }
                    }
                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("maxLevel", maxLevel);
                    updateMetadata("SKILL", skillId, "SKILL", maxLevel, jobId, path, metadata);
                }
            }
        }
        return files;
    }

    private int enrichMaps() throws Exception {
        jdbc.update("DELETE FROM catalog_map_life");
        jdbc.update("DELETE FROM catalog_map_portals");
        Map<Integer, RegionMap> mapRegions = readMapRegions();
        int files = 0;
        try (Stream<Path> paths = Files.walk(wzPath.resolve("Map.wz/Map"))) {
            for (Path path : paths.filter(p -> p.getFileName().toString().matches("\\d{9}\\.img\\.xml")).toList()) {
                files++;
                int mapId = numericFileId(path);
                RegionMap region = refineRegion(mapId, mapRegions.getOrDefault(mapId, inferRegion(mapId)));
                Element root = factory().newDocumentBuilder().parse(path.toFile()).getDocumentElement();
                Element info = directDirectory(root, "info");
                Map<String, Object> mapValues = info == null ? new LinkedHashMap<>() : scalarChildren(info);
                mapValues.put("regionCode", region.code());
                mapValues.put("regionName", region.name());
                String town = townName(mapId, mapValues);
                mapValues.put("townName", town);
                mapValues.put("isTown", isTownMap(mapId, mapValues));
                mapValues.put("mapXmlSource", relative(path));
                updateMetadata("MAP", mapId, "MAP", null, null, path, mapValues);
                Element life = directDirectory(root, "life");
                if (life != null) {
                    Map<String, Integer> counts = new HashMap<>();
                    for (Element spawn : childDirectories(life)) {
                        String type = directValue(spawn, "type");
                        String entity = directValue(spawn, "id");
                        if (!Set.of("m", "n").contains(type) || entity == null || !entity.matches("\\d+")) continue;
                        counts.merge(type + ":" + entity, 1, Integer::sum);
                    }
                    for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                        String[] key = entry.getKey().split(":");
                        jdbc.update("""
                                INSERT INTO catalog_map_life(map_id,region_code,region_name,life_type,
                                    entity_id,spawn_count,source_path) VALUES (?,?,?,?,?,?,?)
                                """, mapId, region.code(), region.name(), key[0], Integer.parseInt(key[1]),
                                entry.getValue(), relative(path));
                    }
                }
                Element portals = directDirectory(root, "portal");
                if (portals != null) {
                    for (Element portal : childDirectories(portals)) {
                        Map<String, Object> values = scalarChildren(portal);
                        Integer target = number(values.get("tm"));
                        if (target == null) continue;
                        jdbc.update("""
                                INSERT INTO catalog_map_portals(map_id,portal_index,portal_name,portal_type,
                                    target_map_id,target_portal_name,x,y,source_path)
                                VALUES (?,?,?,?,?,?,?,?,?)
                                """, mapId, Integer.parseInt(portal.getAttribute("name")),
                                String.valueOf(values.getOrDefault("pn", "")),
                                number(values.get("pt")) == null ? 0 : number(values.get("pt")), target,
                                String.valueOf(values.getOrDefault("tn", "")),
                                number(values.get("x")) == null ? 0 : number(values.get("x")),
                                number(values.get("y")) == null ? 0 : number(values.get("y")), relative(path));
                    }
                }
            }
        }
        return files;
    }

    private Map<Integer, RegionMap> readMapRegions() throws Exception {
        Map<Integer, RegionMap> result = new HashMap<>();
        Path path = wzPath.resolve("String.wz/Map.img.xml");
        Element root = factory().newDocumentBuilder().parse(path.toFile()).getDocumentElement();
        for (Element region : childDirectories(root)) {
            String code = region.getAttribute("name").toLowerCase(Locale.ROOT);
            String name = regionName(code);
            for (Element map : childDirectories(region)) {
                if (!map.getAttribute("name").matches("\\d{5,9}")) continue;
                int id = Integer.parseInt(map.getAttribute("name"));
                result.put(id, new RegionMap(code, name));
                Map<String, Object> values = scalarChildren(map);
                values.put("regionCode", code);
                values.put("regionName", name);
                updateMetadata("MAP", id, "MAP", null, null, path, values);
            }
        }
        return result;
    }

    private RegionMap inferRegion(int mapId) {
        return new RegionMap("unclassified", "Unclassified");
    }

    private RegionMap refineRegion(int mapId, RegionMap original) {
        if (mapId / 1_000_000 == 130) return new RegionMap("ereve", "Ereve");
        if (mapId / 1_000_000 == 140) return new RegionMap("rien", "Rien");
        if (mapId / 1_000_000 == 500) return new RegionMap("thailand", "Thailand");
        if (mapId / 1_000_000 == 540 || mapId / 1_000_000 == 541)
            return new RegionMap("singapore", "Singapore");
        if (mapId / 1_000_000 == 550 || mapId / 1_000_000 == 551)
            return new RegionMap("malaysia", "Malaysia");
        if (mapId / 1_000_000 == 701 || mapId / 1_000_000 == 702)
            return new RegionMap("china", "China");
        if (mapId / 1_000_000 == 740 || mapId / 1_000_000 == 741)
            return new RegionMap("taiwan", "Taiwan");
        return original;
    }

    private String regionName(String code) {
        return switch (code) {
            case "maple" -> "Maple Island";
            case "victoria" -> "Victoria Island";
            case "ossyria" -> "Ossyria";
            case "elin" -> "Ellin Forest";
            case "weddinggl" -> "Wedding";
            case "masteriagl" -> "Masteria";
            case "halloweengl" -> "Halloween";
            case "jp" -> "Japan";
            case "singapore" -> "Singapore";
            case "ereve" -> "Ereve";
            case "rien" -> "Rien";
            case "thailand" -> "Thailand";
            case "malaysia" -> "Malaysia";
            case "china" -> "China";
            case "taiwan" -> "Taiwan";
            case "episode1gl" -> "Episode 1";
            case "event" -> "Event";
            case "etc" -> "Other";
            default -> code.replace('_', ' ');
        };
    }

    private void enrichJobs() throws Exception {
        jdbc.update("DELETE FROM catalog_jobs");
        Path source = wzPath.getParent().resolve("src/main/java/client/Job.java");
        String text = Files.readString(source);
        Matcher matcher = Pattern.compile("([A-Z][A-Z0-9_]*)\\((\\d+)\\)").matcher(text);
        Set<Integer> seen = new HashSet<>();
        while (matcher.find()) {
            int id = Integer.parseInt(matcher.group(2));
            if (!seen.add(id)) continue;
            String name = jobName(matcher.group(1));
            jdbc.update("INSERT INTO catalog_jobs(job_id,job_name,source_path) VALUES (?,?,?)",
                    id, name, "src/main/java/client/Job.java");
        }
    }

    private String jobName(String value) {
        String raw = value.replaceAll("\\d+$", "").replace('_', ' ').toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private void refreshUsageFlags() {
        jdbc.update("UPDATE catalog_entities SET used_in_game=FALSE");
        markUsed("ITEM", game.getJdbcTemplate().queryForList("""
                SELECT itemid FROM drop_data UNION SELECT itemid FROM shopitems
                UNION SELECT itemid FROM inventoryitems
                """, Integer.class));
        markUsed("MOB", game.getJdbcTemplate().queryForList(
                "SELECT DISTINCT dropperid FROM drop_data", Integer.class));
        markUsed("NPC", game.getJdbcTemplate().queryForList(
                "SELECT DISTINCT npcid FROM shops", Integer.class));
        markUsed("MOB", jdbc.queryForList(
                "SELECT DISTINCT entity_id FROM catalog_map_life WHERE life_type='m'", Integer.class));
        markUsed("NPC", jdbc.queryForList(
                "SELECT DISTINCT entity_id FROM catalog_map_life WHERE life_type='n'", Integer.class));
    }

    private void markUsed(String type, List<Integer> ids) {
        for (int start = 0; start < ids.size(); start += 500) {
            List<Integer> chunk = ids.subList(start, Math.min(start + 500, ids.size()));
            MapSqlParameterSource parameters = new MapSqlParameterSource()
                    .addValue("type", type).addValue("ids", chunk);
            new NamedParameterJdbcTemplate(jdbc).update("""
                    UPDATE catalog_entities SET used_in_game=TRUE
                    WHERE entity_type=:type AND entity_id IN (:ids)
                    """, parameters);
        }
    }

    private void updateMetadata(String type, int id, String subtype, Integer level, Integer jobId,
                                Path source, Map<String, Object> values) {
        Map<String, Object> metadata = new LinkedHashMap<>(values);
        metadata.put("dataSource", relative(source));
        jdbc.update("""
                UPDATE catalog_entities SET subtype=?, level_value=?, job_id=?, source_path=?,
                    properties_json=JSON_MERGE_PATCH(COALESCE(properties_json, JSON_OBJECT()), CAST(? AS JSON))
                WHERE entity_type=? AND entity_id=?
                """, subtype, level, jobId, relative(source), encode(metadata), type, id);
    }

    private Map<String, Object> equipmentRanges(Map<String, Object> values) {
        Map<String, Object> ranges = new LinkedHashMap<>();
        Map<String, Integer> caps = Map.ofEntries(
                Map.entry("incSTR", 5), Map.entry("incDEX", 5), Map.entry("incINT", 5),
                Map.entry("incLUK", 5), Map.entry("incPAD", 5), Map.entry("incMAD", 5),
                Map.entry("incACC", 5), Map.entry("incEVA", 5), Map.entry("incSpeed", 5),
                Map.entry("incJump", 5), Map.entry("incPDD", 10), Map.entry("incMDD", 10),
                Map.entry("incMHP", 10), Map.entry("incMMP", 10));
        for (Map.Entry<String, Integer> entry : caps.entrySet()) {
            Integer base = number(values.get(entry.getKey()));
            if (base == null || base == 0) continue;
            int range = Math.min((int) Math.ceil(Math.abs(base) * 0.1), entry.getValue());
            ranges.put(entry.getKey(), Map.of("average", base, "min", base - range, "max", base + range));
        }
        return ranges;
    }

    private void collectEntities(Element element, StringFile definition, List<CatalogEntity> entities) {
        String nodeName = element.getAttribute("name");
        if ("imgdir".equals(element.getTagName()) && nodeName.matches("\\d{5,9}")) {
            String name = directValue(element, "name");
            if ("MAP".equals(definition.type())) name = directValue(element, "mapName");
            if (name != null && !name.isBlank()) {
                String description = directValue(element, "desc");
                if ("MAP".equals(definition.type())) description = directValue(element, "mapDesc");
                if (description == null) description = directValue(element, "msg");
                entities.add(new CatalogEntity(definition.type(), Integer.parseInt(nodeName), name,
                        description == null ? "" : description, category(definition.path()), definition.subtype()));
            }
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child) collectEntities(child, definition, entities);
        }
    }

    private DocumentBuilderFactory factory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory;
    }

    private Element directDirectory(Element parent, String name) {
        for (Element child : childElements(parent)) {
            if ("imgdir".equals(child.getTagName()) && name.equals(child.getAttribute("name"))) return child;
        }
        return null;
    }

    private List<Element> childDirectories(Element parent) {
        return childElements(parent).stream().filter(e -> "imgdir".equals(e.getTagName())).toList();
    }

    private List<Element> childElements(Element parent) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child) result.add(child);
        }
        return result;
    }

    private Map<String, Object> scalarChildren(Element parent) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Element child : childElements(parent)) {
            if (!child.hasAttribute("value")) continue;
            String value = child.getAttribute("value");
            Object typed = value.matches("-?\\d+") ? Long.parseLong(value)
                    : value.matches("-?\\d+\\.\\d+") ? Double.parseDouble(value) : value;
            values.put(child.getAttribute("name"), typed);
        }
        return values;
    }

    private String directValue(Element parent, String name) {
        for (Element child : childElements(parent)) {
            if (name.equals(child.getAttribute("name")) && child.hasAttribute("value")) {
                return child.getAttribute("value");
            }
        }
        return null;
    }

    private Integer number(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private int numericFileId(Path path) {
        return Integer.parseInt(path.getFileName().toString().replace(".img.xml", ""));
    }

    private String relative(Path path) {
        return wzPath.relativize(path).toString().replace('\\', '/');
    }

    private String category(Path source) {
        String file = source.getFileName().toString();
        return file.substring(0, file.indexOf('.'));
    }

    private String equipCategory(String folder, int id, boolean cash) {
        if ("Weapon".equals(folder)) {
            return weaponCategory(id, cash);
        }
        String name = switch (folder) {
            case "Cap" -> "Hat";
            case "Accessory" -> "Accessory";
            case "Coat" -> "Top";
            case "Longcoat" -> "Overall";
            case "Pants" -> "Bottom";
            case "Shoes" -> "Shoes";
            case "Glove" -> "Gloves";
            case "Shield" -> "Shield";
            case "Cape" -> "Cape";
            case "Ring" -> "Ring";
            case "Weapon" -> "Weapon";
            case "PetEquip" -> "Pet Equip";
            case "TamingMob" -> "Mount";
            case "Dragon" -> "Dragon Equip";
            case "Face" -> "Face";
            case "Hair" -> "Hair";
            default -> folder;
        };
        return cash ? "Cash " + name : name;
    }

    private String weaponCategory(int id, boolean cash) {
        int group = id / 10_000;
        String name = switch (group) {
            case 130 -> "One-Handed Sword";
            case 131 -> "One-Handed Axe";
            case 132 -> "One-Handed Mace";
            case 133 -> "Dagger";
            case 137 -> "Wand";
            case 138 -> "Staff";
            case 139 -> "Unused Weapon";
            case 140 -> "Two-Handed Sword";
            case 141 -> "Two-Handed Axe";
            case 142 -> "Two-Handed Mace";
            case 143 -> "Spear";
            case 144 -> "Polearm";
            case 145 -> "Bow";
            case 146 -> "Crossbow";
            case 147 -> "Claw";
            case 148 -> "Knuckle";
            case 149 -> "Gun";
            case 160 -> "Skill Effect";
            case 170 -> "Cash Weapon";
            default -> "Weapon";
        };
        if (cash && group != 170 && group != 160) return "Cash " + name;
        return name;
    }

    private String itemCategory(String folder, int id, Map<String, Object> values) {
        int group = id / 10_000;
        return switch (folder) {
            case "Consume" -> switch (group) {
                case 200 -> "Potion";
                case 201, 202 -> "Food";
                case 203 -> "Return Scroll";
                case 204, 234 -> "Equipment Scroll";
                case 205 -> "Status Cure";
                case 206 -> "Arrow";
                case 207 -> "Throwing Star";
                case 221 -> "Transformation";
                case 233 -> "Bullet";
                case 228, 229 -> "Skill Book";
                default -> "Other Consume";
            };
            case "Install" -> group == 301 ? "Chair" : group == 399 ? "Event Setup" : "Other Setup";
            case "Etc" -> switch (group) {
                case 400 -> "Monster Drop";
                case 401 -> "Ore";
                case 402 -> "Plate / Jewel";
                case 403 -> "Quest Item";
                case 413 -> "Skill Book";
                case 416 -> "Book";
                case 417 -> "Certificate";
                default -> "Other Etc";
            };
            case "Cash" -> switch (group) {
                case 500 -> "Pet";
                case 501 -> "Package";
                case 502 -> "Effect";
                case 503 -> "Store Permit";
                case 504 -> "Teleport";
                case 505 -> "Character Reset";
                case 506 -> "Megaphone";
                case 507 -> "Message";
                case 508 -> "Messenger";
                case 509 -> "Note";
                case 510 -> "Music";
                case 511 -> "Weather";
                case 512 -> "Character";
                case 513 -> "Safety Charm";
                case 514 -> "Shop";
                case 515 -> "Beauty";
                case 516 -> "Emotion";
                case 517 -> "Pet";
                case 518 -> "Pet Consumable";
                case 519 -> "Pet Name";
                case 520 -> "Currency";
                case 521 -> "EXP Coupon";
                case 522 -> "Gachapon";
                case 523 -> "Item Search";
                case 524 -> "Wedding";
                case 525 -> "Map Effect";
                case 530 -> "Morph";
                case 536 -> "Drop Coupon";
                case 537 -> "Chalkboard";
                case 539 -> "Megaphone";
                default -> "Other Cash";
            };
            default -> folder;
        };
    }

    private boolean isTownMap(int mapId, Map<String, Object> values) {
        if (Set.of(1_000_000, 2_000_000, 3_000_000, 300_000_000, 541_000_000,
                551_000_000, 680_000_000).contains(mapId)) return true;
        String name = String.valueOf(values.getOrDefault("mapName", "")).toLowerCase(Locale.ROOT);
        if (name.endsWith(" town") || name.equals("amherst") || name.equals("southperry")
                || name.equals("mushroom town") || name.equals("boat quay town")) return true;
        return Set.of(100000000, 101000000, 102000000, 103000000, 104000000, 105040300, 120000000,
                130000000, 140000000, 200000000, 211000000, 220000000, 221000000, 222000000,
                230000000, 240000000, 250000000, 251000000, 260000000, 261000000, 500000000,
                540000000, 550000000, 600000000, 701000000, 740000000, 800000000, 801000000).contains(mapId);
    }

    private String townName(int mapId, Map<String, Object> values) {
        Map<Integer, String> known = Map.ofEntries(
                Map.entry(100000000, "Henesys"), Map.entry(101000000, "Ellinia"),
                Map.entry(1_000_000, "Amherst"), Map.entry(2_000_000, "Southperry"),
                Map.entry(3_000_000, "Mushroom Town"), Map.entry(541_000_000, "Boat Quay Town"),
                Map.entry(300_000_000, "Altaire Camp"), Map.entry(551_000_000, "Kampung Village"),
                Map.entry(680_000_000, "Amoria"),
                Map.entry(102000000, "Perion"), Map.entry(103000000, "Kerning City"),
                Map.entry(104000000, "Lith Harbor"), Map.entry(105040300, "Sleepywood"),
                Map.entry(120000000, "Nautilus Harbor"), Map.entry(130000000, "Ereve"),
                Map.entry(140000000, "Rien"), Map.entry(200000000, "Orbis"),
                Map.entry(211000000, "El Nath"), Map.entry(220000000, "Ludibrium"),
                Map.entry(221000000, "Omega Sector"), Map.entry(222000000, "Korean Folk Town"),
                Map.entry(230000000, "Aquarium"), Map.entry(240000000, "Leafre"),
                Map.entry(250000000, "Mu Lung"), Map.entry(251000000, "Herb Town"),
                Map.entry(260000000, "Ariant"), Map.entry(261000000, "Magatia"),
                Map.entry(500000000, "Floating Market"), Map.entry(540000000, "Singapore"),
                Map.entry(550000000, "Malaysia"), Map.entry(600000000, "New Leaf City"),
                Map.entry(701000000, "China"), Map.entry(740000000, "Taipei"),
                Map.entry(800000000, "Mushroom Shrine"), Map.entry(801000000, "Showa Town"));
        if (known.containsKey(mapId)) return known.get(mapId);
        Object street = values.get("streetName");
        return street == null ? "" : String.valueOf(street);
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to encode catalog metadata", exception);
        }
    }

    private long startRun() {
        jdbc.update("INSERT INTO catalog_import_runs(status) VALUES ('RUNNING')");
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private record StringFile(String type, String subtype, Path path) {}
    private record CatalogEntity(String type, int id, String name, String description,
                                 String category, String subtype) {}
    private record RegionMap(String code, String name) {}
}
