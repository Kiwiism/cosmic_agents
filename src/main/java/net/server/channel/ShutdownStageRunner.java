package net.server.channel;

import java.util.List;
import java.util.function.BiConsumer;

final class ShutdownStageRunner {
    private ShutdownStageRunner() {
    }

    static void run(List<Stage> stages, BiConsumer<String, RuntimeException> failureHandler) {
        for (Stage stage : stages) {
            try {
                stage.action().run();
            } catch (RuntimeException e) {
                failureHandler.accept(stage.name(), e);
            }
        }
    }

    record Stage(String name, Runnable action) {
    }
}
