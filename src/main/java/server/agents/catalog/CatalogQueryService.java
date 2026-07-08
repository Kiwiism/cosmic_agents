package server.agents.catalog;

public final class CatalogQueryService {
    private final NpcCatalogQuery npc;
    private final MapCatalogQuery map;
    private final MobCatalogQuery mob;
    private final ItemCatalogQuery item;
    private final QuestCatalogQuery quest;
    private final ReactorCatalogQuery reactor;
    private final MapleIslandMvpCatalogQuery mapleIslandMvp;

    CatalogQueryService(CatalogBundle bundle) {
        this.npc = new NpcCatalogQuery(bundle);
        this.map = new MapCatalogQuery(bundle, npc);
        this.mob = new MobCatalogQuery(bundle);
        this.item = new ItemCatalogQuery(bundle);
        this.quest = new QuestCatalogQuery(bundle);
        this.reactor = new ReactorCatalogQuery(bundle);
        this.mapleIslandMvp = new MapleIslandMvpCatalogQuery(bundle);
    }

    public NpcCatalogQuery npc() {
        return npc;
    }

    public MapCatalogQuery map() {
        return map;
    }

    public MobCatalogQuery mob() {
        return mob;
    }

    public ItemCatalogQuery item() {
        return item;
    }

    public QuestCatalogQuery quest() {
        return quest;
    }

    public ReactorCatalogQuery reactor() {
        return reactor;
    }

    public MapleIslandMvpCatalogQuery mapleIslandMvp() {
        return mapleIslandMvp;
    }
}
