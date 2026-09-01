package server.agents.capabilities.partyquest.opq;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Authoritative local Tower of Goddess content contract. */
public final class AgentOpqDefinition {
    public static final int STAGING_MAP = 200_080_100;
    public static final int RECRUIT_MAP = 200_080_101;
    public static final int ENTRY_NPC = 2_013_000;
    public static final int EAK_NPC = 2_013_001;
    public static final int MINERVA_NPC = 2_013_002;
    public static final int ENTRANCE_MAP = 920_010_000;
    public static final int CENTER_MAP = 920_010_100;
    public static final int WALKWAY_MAP = 920_010_200;
    public static final int STORAGE_MAP = 920_010_300;
    public static final int LOBBY_MAP = 920_010_400;
    public static final int SEALED_MAP = 920_010_500;
    public static final int LOUNGE_MAP = 920_010_600;
    public static final List<Integer> LOUNGE_ROOM_MAPS = List.of(
            920_010_601, 920_010_602, 920_010_603, 920_010_604);
    public static final int ON_WAY_UP_MAP = 920_010_700;
    public static final int GARDEN_MAP = 920_010_800;
    public static final int CLEAR_MAP = 920_011_300;
    public static final int EXIT_MAP = 920_011_200;
    public static final int MIN_LEVEL = 51;
    public static final int MAX_LEVEL = 70;
    public static final int PARTY_SIZE = 6;

    public static final int CLOUD_PIECE = 4_001_063;
    public static final int WALKWAY_FRAGMENT = 4_001_050;
    public static final int LOUNGE_FRAGMENT = 4_001_052;
    public static final int STRANGE_SEED = 4_001_053;
    public static final int EVEN_STRANGER_SEED = 4_001_054;
    public static final int ROOT_OF_LIFE = 4_001_055;
    public static final int TRANSPARENT_TRIGGER = 4_001_074;
    public static final int LOBBY_REWARD_BOX = 2_002_011;
    public static final int SEALED_REWARD_BOX = 2_002_012;
    public static final int WAY_UP_REWARD_BOX = 2_002_013;
    public static final int PAPA_PIXIE = 9_300_039;
    public static final Set<Integer> PAPA_PIXIE_SUMMONS = Set.of(9_300_054, 9_300_055, 9_300_056);
    public static final List<Integer> STATUE_PIECES = List.of(
            4_001_044, 4_001_045, 4_001_046, 4_001_047, 4_001_048, 4_001_049);
    public static final Set<Integer> LP_ITEMS = Set.of(
            4_001_056, 4_001_057, 4_001_058, 4_001_059,
            4_001_060, 4_001_061, 4_001_062);
    public static final Set<Integer> EXCLUSIVE_ITEMS = Set.of(
            CLOUD_PIECE, WALKWAY_FRAGMENT, LOUNGE_FRAGMENT, STRANGE_SEED,
            EVEN_STRANGER_SEED, ROOT_OF_LIFE, TRANSPARENT_TRIGGER,
            4_001_044, 4_001_045, 4_001_046, 4_001_047, 4_001_048, 4_001_049,
            4_001_056, 4_001_057, 4_001_058, 4_001_059,
            4_001_060, 4_001_061, 4_001_062);

    public static final Set<Integer> WALKWAY_MOBS = Set.of(9_300_045, 9_300_046, 9_300_047);
    public static final Set<Integer> LOUNGE_MOBS = Set.of(9_300_041, 9_300_042, 9_300_043);
    public static final Set<Integer> GARDEN_SETUP_MOBS = Set.of(
            9_300_048, 9_300_049, 9_300_054, 9_300_055, 9_300_056);
    public static final Set<Integer> GARDEN_MOBS = Set.of(
            9_300_048, 9_300_049, 9_300_054, 9_300_055, 9_300_056, PAPA_PIXIE);
    public static final Set<Integer> ALL_COMBAT_MOBS = Set.of(
            9_300_041, 9_300_042, 9_300_043, 9_300_045, 9_300_046,
            9_300_047, 9_300_048, 9_300_049, 9_300_054, 9_300_055,
            9_300_056, PAPA_PIXIE);

    /** Center portal ids; every transition executes the authored portal script. */
    public static final Map<Room, Integer> CENTER_PORTALS = Map.of(
            Room.WALKWAY, 4, Room.STORAGE, 12, Room.LOBBY, 5,
            Room.SEALED, 13, Room.LOUNGE, 15, Room.ON_WAY_UP, 14);
    public static final Map<Room, Integer> ROOM_EXIT_PORTALS = Map.of(
            Room.WALKWAY, 13, Room.STORAGE, 1, Room.LOBBY, 8,
            Room.SEALED, 3, Room.LOUNGE, 17, Room.ON_WAY_UP, 23);
    public static final Map<Integer, Integer> LOUNGE_SUBROOM_ENTRY_PORTALS = Map.of(
            920_010_601, 3, 920_010_602, 4, 920_010_603, 5, 920_010_604, 6);
    public static final int LOUNGE_SUBROOM_EXIT_PORTAL = 9;

    public static final Map<Integer, String> STATUE_SCAR_BY_ITEM = Map.of(
            4_001_044, "scar4", 4_001_045, "scar6", 4_001_046, "scar1",
            4_001_047, "scar3", 4_001_048, "scar5", 4_001_049, "scar2");
    public static final Map<String, Point> STATUE_SCAR_POSITION = Map.of(
            "scar1", new Point(-164, -1064), "scar2", new Point(99, -1048),
            "scar3", new Point(74, -843), "scar4", new Point(-145, -830),
            "scar5", new Point(-3, -1006), "scar6", new Point(-134, -937));

    public enum Room { WALKWAY, STORAGE, LOBBY, SEALED, LOUNGE, ON_WAY_UP }

    private AgentOpqDefinition() { }

    public static boolean isEventMap(int mapId) {
        return mapId >= ENTRANCE_MAP && mapId <= CLEAR_MAP;
    }

    public static Room roomForMap(int mapId) {
        if (mapId == WALKWAY_MAP) return Room.WALKWAY;
        if (mapId == STORAGE_MAP) return Room.STORAGE;
        if (mapId == LOBBY_MAP) return Room.LOBBY;
        if (mapId == SEALED_MAP) return Room.SEALED;
        if (mapId == LOUNGE_MAP || LOUNGE_ROOM_MAPS.contains(mapId)) return Room.LOUNGE;
        if (mapId == ON_WAY_UP_MAP) return Room.ON_WAY_UP;
        return null;
    }

    public static int roomMap(Room room) {
        return switch (room) {
            case WALKWAY -> WALKWAY_MAP;
            case STORAGE -> STORAGE_MAP;
            case LOBBY -> LOBBY_MAP;
            case SEALED -> SEALED_MAP;
            case LOUNGE -> LOUNGE_MAP;
            case ON_WAY_UP -> ON_WAY_UP_MAP;
        };
    }

    public static String stageProperty(Room room) {
        return "statusStg" + switch (room) {
            case WALKWAY -> 1;
            case STORAGE -> 2;
            case LOBBY -> 3;
            case SEALED -> 4;
            case LOUNGE -> 5;
            case ON_WAY_UP -> 6;
        };
    }

    public static int statuePiece(Room room) {
        return STATUE_PIECES.get(switch (room) {
            case WALKWAY -> 0;
            case STORAGE -> 1;
            case LOBBY -> 2;
            case SEALED -> 3;
            case LOUNGE -> 4;
            case ON_WAY_UP -> 5;
        });
    }
}
