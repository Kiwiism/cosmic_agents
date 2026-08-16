package server.agents.economy.social;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Two-party, public, auditable negotiation state machine. It does not mutate inventories. */
public final class PublicNegotiationSession {
    public enum State { INVITED, NEGOTIATING, AGREED, REJECTED, EXPIRED, EXECUTED, FAILED }
    private final String sessionId;
    private final String initiator;
    private final String counterparty;
    private final Instant expiresAt;
    private final List<Message> transcript = new ArrayList<>();
    private State state = State.INVITED;
    private Proposal proposal;

    public PublicNegotiationSession(String sessionId, String initiator, String counterparty,
                                    Instant startedAt, Duration timeout) {
        if (sessionId == null || sessionId.isBlank() || initiator == null || initiator.isBlank()
                || counterparty == null || counterparty.isBlank() || initiator.equals(counterparty)
                || startedAt == null || timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("invalid negotiation session");
        }
        this.sessionId = sessionId;
        this.initiator = initiator;
        this.counterparty = counterparty;
        this.expiresAt = startedAt.plus(timeout);
        transcript.add(new Message(startedAt, initiator, "TRADE_INVITE", "", null));
    }

    public synchronized void propose(String speaker, TradeOffer initiatorOffers,
                                     TradeOffer counterpartyOffers, String publicText, Instant now) {
        requireParticipant(speaker);
        requireActive(now);
        proposal = new Proposal(initiatorOffers, counterpartyOffers);
        state = State.NEGOTIATING;
        transcript.add(new Message(now, speaker, "PROPOSAL", publicText, proposal));
    }

    public synchronized void agree(String speaker, String publicText, Instant now) {
        requireParticipant(speaker);
        requireActive(now);
        if (proposal == null) throw new IllegalStateException("No proposal to accept");
        state = State.AGREED;
        transcript.add(new Message(now, speaker, "ACCEPT", publicText, proposal));
    }

    public synchronized void reject(String speaker, String publicText, Instant now) {
        requireParticipant(speaker);
        requireActive(now);
        state = State.REJECTED;
        transcript.add(new Message(now, speaker, "REJECT", publicText, proposal));
    }

    public synchronized void markExecution(boolean succeeded, String evidence, Instant now) {
        if (state != State.AGREED) throw new IllegalStateException("Trade is not agreed");
        state = succeeded ? State.EXECUTED : State.FAILED;
        transcript.add(new Message(now, "SYSTEM", succeeded ? "EXECUTED" : "FAILED", evidence, proposal));
    }

    public synchronized State stateAt(Instant now) {
        if ((state == State.INVITED || state == State.NEGOTIATING) && !now.isBefore(expiresAt)) {
            state = State.EXPIRED;
            transcript.add(new Message(now, "SYSTEM", "EXPIRED", "", proposal));
        }
        return state;
    }

    public synchronized List<Message> transcript() { return List.copyOf(transcript); }
    public String sessionId() { return sessionId; }
    public String initiator() { return initiator; }
    public String counterparty() { return counterparty; }
    public synchronized Proposal agreedProposal() {
        if (state != State.AGREED) throw new IllegalStateException("No executable agreement");
        return proposal;
    }

    private void requireParticipant(String speaker) {
        if (!initiator.equals(speaker) && !counterparty.equals(speaker))
            throw new IllegalArgumentException("Speaker is not a participant");
    }

    private void requireActive(Instant now) {
        State current = stateAt(now);
        if (current != State.INVITED && current != State.NEGOTIATING)
            throw new IllegalStateException("Negotiation is not active: " + current);
    }

    public record Proposal(TradeOffer initiatorOffers, TradeOffer counterpartyOffers) { }
    public record Message(Instant logicalTime, String speakerAgentId, String intent,
                          String publicText, Proposal proposal) { }
}
