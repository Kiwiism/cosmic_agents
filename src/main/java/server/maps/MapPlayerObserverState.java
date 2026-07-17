package server.maps;

import client.BotClient;
import client.Character;

import java.util.concurrent.atomic.AtomicInteger;

/** Lock-free audience count for packet work that is useful only to real clients. */
final class MapPlayerObserverState {
    private final AtomicInteger count = new AtomicInteger();

    boolean characterAdded(Character character) {
        if (isObserver(character)) {
            return count.getAndIncrement() == 0;
        }
        return false;
    }

    boolean characterRemoved(Character character) {
        if (isObserver(character)) {
            int previous = count.getAndUpdate(current -> Math.max(0, current - 1));
            return previous == 1;
        }
        return false;
    }

    boolean isObserved() {
        return count.get() > 0;
    }

    int count() {
        return count.get();
    }

    static boolean isObserver(Character character) {
        return character != null
                && character.getClient() != null
                && !(character.getClient() instanceof BotClient);
    }
}
