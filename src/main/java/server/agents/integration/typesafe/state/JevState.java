package server.agents.integration.typesafe.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the {@code state} object for a System One request from named, prioritised blocks and
 * enforces a token budget by dropping the lowest-priority blocks first.
 *
 * <p>Higher {@code priority} means "keep longer". Block order in the final object follows
 * insertion order so related material stays grouped the way the question criteria reference it
 * (for example {@code speaker.name}, {@code bot.activity}).
 */
public final class JevState {
    private final LinkedHashMap<String, Block> blocks = new LinkedHashMap<>();

    private record Block(String key, Object value, int priority, int tokens) {
    }

    /** A built state plus what had to be dropped to fit the budget. */
    public record Built(Map<String, Object> state, List<String> droppedKeys, int estimatedTokens) {
        public Built {
            state = Collections.unmodifiableMap(new LinkedHashMap<>(state));
            droppedKeys = List.copyOf(droppedKeys);
        }
    }

    public static JevState create() {
        return new JevState();
    }

    /** Adds a block; {@code null} values and blank strings are ignored so callers need not check. */
    public JevState put(String key, Object value, int priority) {
        if (key == null || key.isBlank() || value == null) {
            return this;
        }
        if (value instanceof String text && text.isBlank()) {
            return this;
        }
        if (value instanceof Map<?, ?> map && map.isEmpty()) {
            return this;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return this;
        }
        blocks.put(key, new Block(key, value, priority, JevTokenEstimator.estimate(value)));
        return this;
    }

    /** Adds every block of a contributor that has something to say. */
    public JevState add(JevStateContributor contributor) {
        if (contributor != null) {
            contributor.contribute(this);
        }
        return this;
    }

    public int estimatedTokens() {
        return blocks.values().stream().mapToInt(Block::tokens).sum();
    }

    public Built build(int budgetTokens) {
        List<Block> kept = new ArrayList<>(blocks.values());
        List<String> dropped = new ArrayList<>();
        int total = kept.stream().mapToInt(Block::tokens).sum();
        while (total > budgetTokens && kept.size() > 1) {
            Block victim = kept.stream().min(Comparator.comparingInt(Block::priority)).orElseThrow();
            kept.remove(victim);
            dropped.add(victim.key());
            total -= victim.tokens();
        }
        Map<String, Object> state = new LinkedHashMap<>();
        for (Block block : blocks.values()) {
            if (kept.contains(block)) {
                state.put(block.key(), block.value());
            }
        }
        return new Built(state, dropped, total);
    }
}
