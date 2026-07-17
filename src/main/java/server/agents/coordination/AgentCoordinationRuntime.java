package server.agents.coordination;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** In-process event seam for cohort policy, telemetry, and optional dialogue projection. */
public final class AgentCoordinationRuntime {
    private static final CopyOnWriteArrayList<Consumer<AgentCoordinationMessage>> listeners =
            new CopyOnWriteArrayList<>();

    private AgentCoordinationRuntime() {
    }

    public static AutoCloseable subscribe(Consumer<AgentCoordinationMessage> listener) {
        Consumer<AgentCoordinationMessage> required = Objects.requireNonNull(listener);
        listeners.add(required);
        return () -> listeners.remove(required);
    }

    public static void publish(AgentCoordinationMessage message) {
        Objects.requireNonNull(message);
        for (Consumer<AgentCoordinationMessage> listener : listeners) {
            listener.accept(message);
        }
    }
}
