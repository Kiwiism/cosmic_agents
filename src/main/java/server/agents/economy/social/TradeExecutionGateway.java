package server.agents.economy.social;

/** Cosmic adapter must execute this through EconomyTransactionCoordinator/real Trade primitives. */
public interface TradeExecutionGateway {
    Result execute(String idempotencyKey, String firstAgentId, TradeOffer firstOffer,
                   String secondAgentId, TradeOffer secondOffer);

    record Result(boolean succeeded, String transactionId, String evidence) { }
}
