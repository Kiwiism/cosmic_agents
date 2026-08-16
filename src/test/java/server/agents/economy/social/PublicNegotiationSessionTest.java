package server.agents.economy.social;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PublicNegotiationSessionTest {
    @Test
    void agreementKeepsPublicReasonedTranscriptUntilRealExecution() {
        var session = new PublicNegotiationSession("n1", "buyer", "seller", Instant.EPOCH,
                Duration.ofMinutes(2));
        session.propose("buyer", new TradeOffer(1_000, Map.of()),
                new TradeOffer(0, Map.of(4000000, 10)), "1k for ten?", Instant.EPOCH.plusSeconds(10));
        session.agree("seller", "deal", Instant.EPOCH.plusSeconds(20));
        assertEquals(PublicNegotiationSession.State.AGREED, session.stateAt(Instant.EPOCH.plusSeconds(20)));
        assertEquals(3, session.transcript().size());
        session.markExecution(true, "cosmic transaction tx-1", Instant.EPOCH.plusSeconds(21));
        assertEquals(PublicNegotiationSession.State.EXECUTED, session.stateAt(Instant.EPOCH.plusSeconds(21)));
    }

    @Test
    void expiresWithoutTrade() {
        var session = new PublicNegotiationSession("n1", "a", "b", Instant.EPOCH, Duration.ofSeconds(1));
        assertEquals(PublicNegotiationSession.State.EXPIRED, session.stateAt(Instant.EPOCH.plusSeconds(1)));
    }
}
