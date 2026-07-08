package server.agents.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class CatalogRecord {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final Map<String, Object> fields;

    private CatalogRecord(Map<String, Object> fields) {
        this.fields = immutableMap(fields);
    }

    public static CatalogRecord from(JsonNode node, ObjectMapper mapper) {
        if (node == null || !node.isObject()) {
            throw new CatalogLookupException("Catalog record node must be an object.");
        }
        return new CatalogRecord(mapper.convertValue(node, MAP_TYPE));
    }

    public Map<String, Object> fields() {
        return fields;
    }

    public Optional<Integer> intValue(String field) {
        Object value = fields.get(field);
        if (value instanceof Number number) {
            return Optional.of(number.intValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Optional.of(Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public Optional<Long> longValue(String field) {
        Object value = fields.get(field);
        if (value instanceof Number number) {
            return Optional.of(number.longValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Optional.of(Long.parseLong(text));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public Optional<Boolean> booleanValue(String field) {
        Object value = fields.get(field);
        if (value instanceof Boolean bool) {
            return Optional.of(bool);
        }
        if (value instanceof String text && !text.isBlank()) {
            return Optional.of(Boolean.parseBoolean(text));
        }
        return Optional.empty();
    }

    public Optional<String> stringValue(String field) {
        Object value = fields.get(field);
        if (value == null) {
            return Optional.empty();
        }
        String text = String.valueOf(value);
        return text.isBlank() ? Optional.empty() : Optional.of(text);
    }

    public String lowerString(String field) {
        return stringValue(field).map(text -> text.toLowerCase(Locale.ROOT)).orElse("");
    }

    public List<Integer> intList(String field) {
        Object value = fields.get(field);
        return intListFromValue(value);
    }

    public List<CatalogRecord> recordList(String field) {
        Object value = fields.get(field);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<CatalogRecord> records = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                records.add(new CatalogRecord(castStringMap(map)));
            }
        }
        return List.copyOf(records);
    }

    public Optional<CatalogRecord> record(String field) {
        Object value = fields.get(field);
        if (value instanceof Map<?, ?> map) {
            return Optional.of(new CatalogRecord(castStringMap(map)));
        }
        return Optional.empty();
    }

    static List<Integer> intListFromValue(Object value) {
        if (value instanceof Number number) {
            return List.of(number.intValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return List.of(Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                return List.of();
            }
        }
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Integer> values = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number number) {
                values.add(number.intValue());
            } else if (item instanceof String text && !text.isBlank()) {
                try {
                    values.add(Integer.parseInt(text));
                } catch (NumberFormatException ignored) {
                    // Ignore malformed catalog values while preserving the rest.
                }
            }
        }
        return List.copyOf(values);
    }

    private static Map<String, Object> castStringMap(Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copy;
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(entry.getKey(), immutableValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return immutableMap(castStringMap(map));
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            for (Object item : list) {
                copy.add(immutableValue(item));
            }
            return Collections.unmodifiableList(copy);
        }
        return value;
    }
}
