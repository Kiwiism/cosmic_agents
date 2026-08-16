package server.agents.economy.persistence;

import server.agents.economy.market.MarketObservation;

import java.util.UUID;

public interface EconomyEvidenceJournal {
    void appendDecision(DecisionEvidence evidence);
    void appendObservation(UUID runId, MarketObservation observation);
    void appendSocial(SocialEvidence evidence);
}
