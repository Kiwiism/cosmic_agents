package com.cosmic.databaseconsole.bridge;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

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

    private boolean post(String path) {
        try {
            client.post().uri(path).retrieve().toBodilessEntity();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private Map<String, Object> offline(String reason) {
        return Map.of("status", "OFFLINE", "checkedAt", Instant.now().toString(), "reason", reason);
    }
}
