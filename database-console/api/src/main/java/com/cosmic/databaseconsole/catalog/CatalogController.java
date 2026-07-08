package com.cosmic.databaseconsole.catalog;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/search")
    Map<String, Object> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String type,
            @RequestParam(defaultValue = "") String subtype,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(required = false) Integer minLevel,
            @RequestParam(required = false) Integer maxLevel,
            @RequestParam(required = false) Integer jobId,
            @RequestParam(defaultValue = "") String region,
            @RequestParam(defaultValue = "false") boolean usedOnly,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "48") int size) {
        return catalog.search(q, type.toUpperCase(), subtype.toUpperCase(), category, minLevel, maxLevel, jobId, region, usedOnly,
                sort, direction, Math.max(page, 0), Math.clamp(size, 12, 500));
    }

    @GetMapping("/suggest")
    List<Map<String, Object>> suggest(@RequestParam(defaultValue = "") String q,
                                      @RequestParam(defaultValue = "") String type,
                                      @RequestParam(defaultValue = "") String subtype,
                                      @RequestParam(defaultValue = "12") int limit) {
        return catalog.suggest(q, type.toUpperCase(), subtype.toUpperCase(), Math.clamp(limit, 1, 500));
    }

    @GetMapping("/{type}/{id}")
    Map<String, Object> detail(@PathVariable String type, @PathVariable int id) {
        return catalog.detail(type.toUpperCase(), id);
    }

    @GetMapping("/regions")
    List<Map<String, Object>> regions() {
        return catalog.regions();
    }

    @GetMapping("/regions/{region}/mobs")
    List<Map<String, Object>> regionMobs(@PathVariable String region) {
        return catalog.regionMobs(region);
    }

    @GetMapping("/regions/{region}/maps")
    List<Map<String, Object>> regionMaps(@PathVariable String region) {
        return catalog.regionMaps(region);
    }

    @GetMapping("/jobs")
    List<Map<String, Object>> jobs() {
        return catalog.jobs();
    }

    @PostMapping("/import")
    Map<String, Object> importCatalog() {
        return catalog.importCatalog();
    }
}
