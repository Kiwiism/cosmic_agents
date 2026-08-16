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

    private final List<ParticipantState> participants;
    private final List<EconomyAtomicPersistence> additionalPersistence;
    private final EconomyMutationEvidence evidence;
    private final Persistence testPersistence;

    private EconomyDurableState(List<ParticipantState> participants,
                                List<EconomyAtomicPersistence> additionalPersistence,
                                EconomyMutationEvidence evidence,
                                Persistence testPersistence) {
        this.participants = List.copyOf(participants);
        this.additionalPersistence = List.copyOf(additionalPersistence);
        this.evidence = evidence;
        this.testPersistence = testPersistence;
    }

    static EconomyDurableState capture(EconomyParticipantSnapshot primaryBefore,
                                       EconomyParticipantSnapshot primaryAfter,
                                       EconomyParticipantSnapshot secondaryBefore,
                                       EconomyParticipantSnapshot secondaryAfter,
                                       List<EconomyAtomicPersistence> additionalPersistence,
                                       java.util.Map<String, Object> operationEvidence) {
        return new EconomyDurableState(secondaryAfter == null
                ? List.of(new ParticipantState(primaryBefore, primaryAfter))
                : List.of(new ParticipantState(primaryBefore, primaryAfter),
                        new ParticipantState(secondaryBefore, secondaryAfter)),
                additionalPersistence, EconomyMutationEvidence.between(primaryBefore, primaryAfter,
                secondaryBefore, secondaryAfter, operationEvidence), null);
    }

    static EconomyDurableState forTesting(Persistence persistence) {
        return new EconomyDurableState(List.of(), List.of(), new EconomyMutationEvidence(List.of()), persistence);
    }

    void persist(Connection connection) throws SQLException {
        if (testPersistence != null) {
            testPersistence.persist(connection);
            return;
        }
        for (ParticipantState participant : participants) {
            participant.after().persist(connection, participant.before());
        }
        for (EconomyAtomicPersistence persistence : additionalPersistence) {
            persistence.persist(connection);
        }
    }

    String evidenceJson() { return evidence.json(); }

    private record ParticipantState(EconomyParticipantSnapshot before,
                                    EconomyParticipantSnapshot after) { }
}
