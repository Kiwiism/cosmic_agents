package server.economy;

@FunctionalInterface
public interface EconomyMutation {
    void run(EconomyTransactionContext context);
}
