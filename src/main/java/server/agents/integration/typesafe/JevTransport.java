package server.agents.integration.typesafe;

/** Synchronous wire adapter; the real implementation is HTTP, tests substitute a fake. */
@FunctionalInterface
public interface JevTransport {
    JevResponse send(JevRequest request, TypeSafeSettings settings) throws JevTransportException;
}
