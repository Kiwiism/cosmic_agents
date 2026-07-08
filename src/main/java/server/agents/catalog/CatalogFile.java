package server.agents.catalog;

import java.nio.file.Path;

enum CatalogFile {
    MAPS("maps", true, CatalogDirectory.GAME, "generated_map_catalog.json"),
    MOBS("mobs", true, CatalogDirectory.GAME, "generated_mob_catalog.json"),
    ITEMS("items", true, CatalogDirectory.GAME, "generated_item_catalog.json"),
    DROPS("drops", true, CatalogDirectory.GAME, "generated_drop_catalog.json"),
    QUESTS("quests", true, CatalogDirectory.GAME, "generated_quest_catalog.json"),
    SHOPS("shops", true, CatalogDirectory.GAME, "generated_shop_catalog.json"),
    SKILLS("skills", true, CatalogDirectory.GAME, "generated_skill_catalog.json"),
    NPCS("npcs", true, CatalogDirectory.NPC, "generated_npc_catalog.json"),
    NPC_PLACEMENTS("npcPlacements", true, CatalogDirectory.NPC, "generated_npc_placements.json"),
    NPC_ACTIONS("npcActions", true, CatalogDirectory.NPC, "generated_npc_action_catalog.json"),
    NPC_APPROACH_POINTS("npcApproachPoints", true, CatalogDirectory.NPC, "generated_npc_approach_points.json"),
    QUEST_DIALOGUE_TIMING("questDialogueTiming", true, CatalogDirectory.NPC, "generated_quest_dialogue_timing.json"),
    NPC_FAST_INDEXES("npcFastIndexes", true, CatalogDirectory.NPC, "generated_npc_fast_indexes.json"),
    PORTAL_GRAPH("portalGraph", true, CatalogDirectory.AGENT_LLM, "generated_portal_graph.json"),
    MAP_SUMMARY("mapSummary", true, CatalogDirectory.AGENT_LLM, "generated_map_summary_index.json"),
    MOB_SPAWN("mobSpawn", true, CatalogDirectory.AGENT_LLM, "generated_mob_spawn_catalog.json"),
    QUEST_OBJECTIVES("questObjectives", true, CatalogDirectory.AGENT_LLM, "generated_quest_objective_catalog.json"),
    ITEM_SOURCES("itemSources", true, CatalogDirectory.AGENT_LLM, "generated_item_source_index.json"),
    RESUPPLY("resupply", true, CatalogDirectory.AGENT_LLM, "generated_resupply_catalog.json"),
    ACTION_AFFORDANCES("actionAffordances", true, CatalogDirectory.AGENT_LLM, "generated_action_affordance_catalog.json"),
    MAPLE_ISLAND_MVP("mapleIslandMvp", true, CatalogDirectory.AGENT_LLM, "generated_maple_island_mvp_catalog.json"),
    MAPLE_ISLAND_MVP_FAST_INDEXES("mapleIslandMvpFastIndexes", true, CatalogDirectory.AGENT_LLM,
            "generated_maple_island_mvp_fast_indexes.json"),
    DROP_SOURCE_CLASSIFICATIONS("dropSourceClassifications", true, CatalogDirectory.OVERRIDES,
            "drop-source-classifications.catalog.json"),
    VICTORIA_LT30_QUEST_STATUS("victoriaLt30QuestStatus", false, CatalogDirectory.OVERRIDES,
            "victoria-lt30-quest-status.catalog.json"),
    REACTORS("reactors", false, CatalogDirectory.REACTOR, "generated_reactor_catalog.json");

    private final String key;
    private final boolean required;
    private final CatalogDirectory directory;
    private final String fileName;

    CatalogFile(String key, boolean required, CatalogDirectory directory, String fileName) {
        this.key = key;
        this.required = required;
        this.directory = directory;
        this.fileName = fileName;
    }

    String key() {
        return key;
    }

    boolean required() {
        return required;
    }

    Path resolve(CatalogLoadOptions options) {
        return directory.resolve(options).resolve(fileName);
    }
}

enum CatalogDirectory {
    GAME {
        @Override
        Path resolve(CatalogLoadOptions options) {
            return options.gameCatalogDir();
        }
    },
    NPC {
        @Override
        Path resolve(CatalogLoadOptions options) {
            return options.npcCatalogDir();
        }
    },
    AGENT_LLM {
        @Override
        Path resolve(CatalogLoadOptions options) {
            return options.agentLlmCatalogDir();
        }
    },
    OVERRIDES {
        @Override
        Path resolve(CatalogLoadOptions options) {
            return options.overridesDir();
        }
    },
    REACTOR {
        @Override
        Path resolve(CatalogLoadOptions options) {
            return options.reactorCatalogDir();
        }
    };

    abstract Path resolve(CatalogLoadOptions options);
}
