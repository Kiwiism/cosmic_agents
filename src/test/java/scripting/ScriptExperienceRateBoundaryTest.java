package scripting;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ScriptExperienceRateBoundaryTest {
    @Test
    void scriptsDoNotManuallyMultiplyExperienceByCharacterRates() throws IOException {
        try (var paths = Files.walk(Path.of("scripts"))) {
            for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".js")).toList()) {
                String script = Files.readString(path);
                assertFalse(script.contains("getExpRate("),
                        () -> path + " manually applies EXP rate before a rate-aware script API");
                assertFalse(script.contains("getQuestExpRate("),
                        () -> path + " manually applies quest EXP rate before a rate-aware script API");
            }
        }
    }
}
