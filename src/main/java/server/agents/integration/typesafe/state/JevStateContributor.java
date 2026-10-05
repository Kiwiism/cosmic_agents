package server.agents.integration.typesafe.state;

/** A source of one or more named state blocks (speaker, bot, field, history, evidence, policy). */
@FunctionalInterface
public interface JevStateContributor {
    void contribute(JevState state);
}
