package server.observer;

import java.util.Optional;
import java.util.ServiceLoader;

public final class ObserverAdapters {
    private ObserverAdapters() {
    }

    public static Optional<ObserverNavGraphAdapter> navGraph() {
        return ObserverFeature.navGraphEnabled()
                ? NavGraphHolder.ADAPTER
                : Optional.empty();
    }

    public static Optional<ObserverInterestAdapter> interest() {
        return ObserverFeature.agentSignalsEnabled()
                ? InterestHolder.ADAPTER
                : Optional.empty();
    }

    public static Optional<ObserverCharacterDirectoryAdapter> characterDirectory() {
        return ObserverFeature.enabled()
                ? CharacterDirectoryHolder.ADAPTER
                : Optional.empty();
    }

    private static <T> Optional<T> first(Class<T> type) {
        return ServiceLoader.load(type).findFirst();
    }

    private static final class NavGraphHolder {
        private static final Optional<ObserverNavGraphAdapter> ADAPTER =
                first(ObserverNavGraphAdapter.class);
    }

    private static final class InterestHolder {
        private static final Optional<ObserverInterestAdapter> ADAPTER =
                first(ObserverInterestAdapter.class);
    }

    private static final class CharacterDirectoryHolder {
        private static final Optional<ObserverCharacterDirectoryAdapter> ADAPTER =
                first(ObserverCharacterDirectoryAdapter.class);
    }
}
