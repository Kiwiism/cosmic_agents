package client;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillMacroPersistenceDirtyTest {
    @Test
    void directPersistedSkillChangesNotifyTheOwner() {
        SkillMacro macro = new SkillMacro(1, 2, 3, "macro", 0, 0);
        AtomicInteger dirtySignals = new AtomicInteger();
        macro.setPersistenceDirtyMarker(dirtySignals::incrementAndGet);

        macro.setSkill1(10);
        macro.setSkill2(20);
        macro.setSkill3(30);
        macro.setSkill3(30);

        assertEquals(3, dirtySignals.get());
    }
}
