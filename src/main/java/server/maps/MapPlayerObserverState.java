package server.maps;

import client.BotClient;
import client.Character;

import java.util.concurrent.atomic.AtomicInteger;

/** Lock-free audience count for packet work that is useful only to real clients. */
final class MapPlayerObserverState {
    private final AtomicInteger count = new AtomicInteger();

    void characterAdded(Character character) {
        if (isObserver(character)) {
            count.incrementAndGet();
        }
    }

    void characterRemoved(Character character) {
        if (isObserver(character)) {
            count.updateAndGet(current -> Math.max(0, current - 1));
        }
    }

    boolean isObserved() {
        return count.get() > 0;
    }

    int count() {
        return count.get();
    }

    static boolean isObserver(Character character) {
        return character != null && !(character.getClient() instanceof BotClient);
    }
}
