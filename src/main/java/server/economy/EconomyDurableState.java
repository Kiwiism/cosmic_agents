package server.economy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** The complete durable economy-owned state written with the journal commit. */
public final class EconomyDurableState {
    @FunctionalInterface
    interface Persistence {
        void persist(Connection connection) throws SQLException;
    }

    private final List<EconomyParticipantSnapshot> participants;
    private final List<EconomyAtomicPersistence> additionalPersistence;
    private final Persistence testPersistence;

    private EconomyDurableState(List<EconomyParticipantSnapshot> participants,
                                List<EconomyAtomicPersistence> additionalPersistence,
                                Persistence testPersistence) {
        this.participants = List.copyOf(participants);
        this.additionalPersistence = List.copyOf(additionalPersistence);
        this.testPersistence = testPersistence;
    }

    static EconomyDurableState capture(EconomyParticipantSnapshot primary,
                                       EconomyParticipantSnapshot secondary,
                                       List<EconomyAtomicPersistence> additionalPersistence) {
        return new EconomyDurableState(secondary == null ? List.of(primary) : List.of(primary, secondary),
                additionalPersistence, null);
    }

    static EconomyDurableState forTesting(Persistence persistence) {
        return new EconomyDurableState(List.of(), List.of(), persistence);
    }

    void persist(Connection connection) throws SQLException {
        if (testPersistence != null) {
            testPersistence.persist(connection);
            return;
        }
        for (EconomyParticipantSnapshot participant : participants) {
            participant.persist(connection);
        }
        for (EconomyAtomicPersistence persistence : additionalPersistence) {
            persistence.persist(connection);
        }
    }
}
