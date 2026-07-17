package server.maps;

import server.life.Monster;
import server.life.NPC;

import java.util.List;

/** Immutable map-object membership shared by Agent perception reads. */
public record MapPerceptionSnapshot(
        long revision,
        List<Monster> monsters,
        List<MapItem> items,
        List<NPC> npcs
) {
    public MapPerceptionSnapshot {
        monsters = List.copyOf(monsters);
        items = List.copyOf(items);
        npcs = List.copyOf(npcs);
    }
}
