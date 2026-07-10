package com.cosmic.databaseconsole.bridge;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

@Service
public class BridgeClient {
    private final RestClient client;

    public BridgeClient(RestClient.Builder builder,
                        @Value("${cosmic.bridge.url}") String baseUrl,
                        @Value("${cosmic.bridge.token}") String token) {
        this.client = builder.baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
    }

    public Map<String, Object> health() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.get().uri("/internal/admin/health").retrieve().body(Map.class);
            return response == null ? offline("Empty bridge response") : response;
        } catch (Exception exception) {
            return offline(exception.getClass().getSimpleName());
        }
    }

    public boolean reloadDrops() {
        return post("/internal/admin/cache/drops/reload");
    }

    public boolean reloadShops() {
        return post("/internal/admin/cache/shops/reload");
    }

    public Map<String, Object> updateAppearance(int characterId, Map<String, Object> body) {
        return postForMap("/internal/admin/characters/" + characterId + "/appearance", body);
    }

    public Map<String, Object> upsertEquippedItem(int characterId, Map<String, Object> body) {
        return postForMap("/internal/admin/characters/" + characterId + "/equipped-items", body);
    }

    public Map<String, Object> mutateInventory(int characterId, Map<String, Object> body) {
        return postForMap("/internal/admin/characters/" + characterId + "/inventory-items", body);
    }

    public Map<String, Object> mutateStorage(int accountId, Map<String, Object> body) {
        return postForMap("/internal/admin/accounts/" + accountId + "/storage", body);
    }

    private boolean post(String path) {
        try {
            client.post().uri(path).retrieve().toBodilessEntity();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private Map<String, Object> postForMap(String path, Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post().uri(path).body(body).retrieve().body(Map.class);
            return response == null ? Map.of("status", "EMPTY") : response;
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Live bridge is offline. Restart the game server with the Database Console bridge enabled.", exception);
        }
    }

    private Map<String, Object> offline(String reason) {
        return Map.of("status", "OFFLINE", "checkedAt", Instant.now().toString(), "reason", reason);
    }
}
