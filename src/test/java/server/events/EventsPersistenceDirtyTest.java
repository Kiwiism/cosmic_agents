package server.events;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventsPersistenceDirtyTest {
    @Test
    void rescueProgressMarksItsOwningRelatedSection() {
        RescueGaga event = new RescueGaga(4);
        AtomicInteger marks = new AtomicInteger();
        event.setPersistenceDirtyMarker(marks::incrementAndGet);

        event.complete();

        assertEquals(5, event.getCompleted());
        assertEquals(1, marks.get());
    }
}
