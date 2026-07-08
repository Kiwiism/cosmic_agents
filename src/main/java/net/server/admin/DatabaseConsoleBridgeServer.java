package net.server.admin;

import client.Character;
import client.SkinColor;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.ModifyInventory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.server.Server;
import net.server.world.World;
import server.ItemInformationProvider;
import server.ShopFactory;
import server.life.MonsterInformationProvider;
import server.monitoring.CharacterSaveDiagnostics.SaveReason;
import server.monitoring.ServerMetricsSnapshot;
import tools.PacketCreator;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class DatabaseConsoleBridgeServer {
    private static final int DEFAULT_PORT = 8787;
    private static final String DEFAULT_TOKEN = "development-only-change-me";

    private final ObjectMapper json = new ObjectMapper();
    private final HttpServer server;
    private final String token;

    public DatabaseConsoleBridgeServer() throws IOException {
        int port = intEnv("COSMIC_BRIDGE_PORT", DEFAULT_PORT);
        this.token = stringEnv("COSMIC_BRIDGE_TOKEN", DEFAULT_TOKEN);
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        this.server.setExecutor(Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "database-console-bridge");
            thread.setDaemon(true);
            return thread;
        }));
        this.server.createContext("/internal/admin", this::handle);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!authorized(exchange)) {
                send(exchange, 401, Map.of("status", "UNAUTHORIZED"));
                return;
            }
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            if ("GET".equals(method) && "/internal/admin/health".equals(path)) {
                ServerMetricsSnapshot metrics = Server.getInstance().buildMetricsSnapshot();
                send(exchange, 200, Map.of(
                        "status", Server.getInstance().isOnline() ? "UP" : "STARTING",
                        "checkedAt", Instant.now().toString(),
                        "onlinePlayers", metrics.onlinePlayers(),
                        "loadedMaps", metrics.loadedMaps(),
                        "loadLevel", metrics.loadLevel().name()));
                return;
            }
            if ("POST".equals(method) && "/internal/admin/cache/drops/reload".equals(path)) {
                MonsterInformationProvider.getInstance().clearDrops();
                send(exchange, 200, Map.of("status", "OK", "reloaded", "drops"));
                return;
            }
            if ("POST".equals(method) && "/internal/admin/cache/shops/reload".equals(path)) {
                ShopFactory.getInstance().reloadShops();
                send(exchange, 200, Map.of("status", "OK", "reloaded", "shops"));
                return;
            }
            if ("POST".equals(method) && path.matches("/internal/admin/characters/\\d+/appearance")) {
                int characterId = characterId(path, "/appearance");
                updateAppearance(exchange, characterId);
                return;
            }
            if ("POST".equals(method) && path.matches("/internal/admin/characters/\\d+/equipped-items")) {
                int characterId = characterId(path, "/equipped-items");
                upsertEquippedItem(exchange, characterId);
                return;
            }
            send(exchange, 404, Map.of("status", "NOT_FOUND"));
        } catch (IllegalArgumentException exception) {
            send(exchange, 400, Map.of("status", "BAD_REQUEST", "message", exception.getMessage()));
        } catch (IllegalStateException exception) {
            send(exchange, 409, Map.of("status", "CONFLICT", "message", exception.getMessage()));
        } catch (Exception exception) {
            send(exchange, 500, Map.of("status", "ERROR", "message", exception.getClass().getSimpleName()));
        }
    }

    private void updateAppearance(HttpExchange exchange, int characterId) throws IOException {
        JsonNode body = readBody(exchange);
        Character chr = onlineCharacter(characterId);
        SkinColor skinColor = SkinColor.getById(requiredInt(body, "skincolor"));
        if (skinColor == null) {
            throw new IllegalArgumentException("Unknown skin color");
        }
        chr.setHair(requiredInt(body, "hair"));
        chr.setFace(requiredInt(body, "face"));
        chr.setSkinColor(skinColor);
        chr.setGender(requiredInt(body, "gender"));
        chr.equipChanged();
        chr.saveCharToDB(true, SaveReason.FULL_SAVE);
        send(exchange, 200, Map.of("status", "OK", "characterId", characterId));
    }

    private void upsertEquippedItem(HttpExchange exchange, int characterId) throws IOException {
        JsonNode body = readBody(exchange);
        Character chr = onlineCharacter(characterId);
        int itemId = requiredInt(body, "itemId");
        short position = (short) requiredInt(body, "position");
        if (itemId / 1_000_000 != 1 || position >= 0 || position == 0) {
            throw new IllegalArgumentException("Only equipped equipment slots are supported while online");
        }

        Inventory equipped = chr.getInventory(InventoryType.EQUIPPED);
        Short sourcePosition = body.hasNonNull("sourcePosition") ? (short) body.path("sourcePosition").asInt() : null;
        Item source = sourcePosition == null ? null : equipped.getItem(sourcePosition);
        Item target = equipped.getItem(position);
        if (target != null && (source == null || target != source)) {
            throw new IllegalStateException("Target equipped slot is occupied");
        }
        if (source != null) {
            equipped.removeSlot(source.getPosition());
        }

        Equip equip = createEquip(body, itemId, position);
        equipped.addItemFromDB(equip);
        chr.sendPacket(PacketCreator.modifyInventory(true, List.of(new ModifyInventory(0, equip))));
        chr.equipChanged();
        chr.saveCharToDB(true, SaveReason.FULL_SAVE);
        send(exchange, 200, Map.of("status", "OK", "characterId", characterId,
                "itemId", itemId, "position", (int) position));
    }

    private Equip createEquip(JsonNode body, int itemId, short position) {
        Equip base = (Equip) ItemInformationProvider.getInstance().getEquipById(itemId);
        Equip equip = base == null ? new Equip(itemId, position) : (Equip) base.copy();
        equip.setPosition(position);
        equip.setQuantity((short) 1);
        equip.setOwner(body.path("owner").asText(""));
        equip.setFlag((short) body.path("flag").asInt(0));
        equip.setExpiration(body.path("expiration").asLong(-1));
        equip.setGiftFrom(body.path("giftFrom").asText(""));

        JsonNode stats = body.path("equipment");
        if (!stats.isMissingNode() && !stats.isNull()) {
            equip.setUpgradeSlots(stats.path("upgradeSlots").asInt(equip.getUpgradeSlots()));
            equip.setLevel((byte) stats.path("level").asInt(equip.getLevel()));
            equip.setStr((short) stats.path("str").asInt(equip.getStr()));
            equip.setDex((short) stats.path("dex").asInt(equip.getDex()));
            equip.setInt((short) stats.path("intStat").asInt(equip.getInt()));
            equip.setLuk((short) stats.path("luk").asInt(equip.getLuk()));
            equip.setHp((short) stats.path("hp").asInt(equip.getHp()));
            equip.setMp((short) stats.path("mp").asInt(equip.getMp()));
            equip.setWatk((short) stats.path("watk").asInt(equip.getWatk()));
            equip.setMatk((short) stats.path("matk").asInt(equip.getMatk()));
            equip.setWdef((short) stats.path("wdef").asInt(equip.getWdef()));
            equip.setMdef((short) stats.path("mdef").asInt(equip.getMdef()));
            equip.setAcc((short) stats.path("acc").asInt(equip.getAcc()));
            equip.setAvoid((short) stats.path("avoid").asInt(equip.getAvoid()));
            equip.setHands((short) stats.path("hands").asInt(equip.getHands()));
            equip.setSpeed((short) stats.path("speed").asInt(equip.getSpeed()));
            equip.setJump((short) stats.path("jump").asInt(equip.getJump()));
            equip.setVicious((short) stats.path("vicious").asInt(equip.getVicious()));
            equip.setItemLevel((byte) stats.path("itemLevel").asInt(equip.getItemLevel()));
            equip.setItemExp(stats.path("itemExp").asInt(equip.getItemExp()));
        }
        return equip;
    }

    private Character onlineCharacter(int characterId) {
        for (World world : Server.getInstance().getWorlds()) {
            Character chr = world.getPlayerStorage().getCharacterById(characterId);
            if (chr != null) {
                return chr;
            }
        }
        throw new IllegalStateException("Character is not online in the live server");
    }

    private boolean authorized(HttpExchange exchange) {
        List<String> values = exchange.getRequestHeaders().getOrDefault("Authorization", new ArrayList<>());
        return values.stream().anyMatch(value -> value.equals("Bearer " + token));
    }

    private JsonNode readBody(HttpExchange exchange) throws IOException {
        return json.readTree(exchange.getRequestBody());
    }

    private int requiredInt(JsonNode body, String field) {
        if (!body.has(field) || !body.get(field).canConvertToInt()) {
            throw new IllegalArgumentException("Missing numeric field: " + field);
        }
        return body.get(field).asInt();
    }

    private int characterId(String path, String suffix) {
        String withoutSuffix = path.substring(0, path.length() - suffix.length());
        int lastSlash = withoutSuffix.lastIndexOf('/');
        return Integer.parseInt(withoutSuffix.substring(lastSlash + 1));
    }

    private void send(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] payload = json.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream response = exchange.getResponseBody()) {
            response.write(payload);
        }
    }

    private static String stringEnv(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int intEnv(String name, int fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
