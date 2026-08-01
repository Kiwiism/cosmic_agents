package server.security;

@FunctionalInterface
public interface SecurityAlertSink {
    void deliver(SecurityEvent event);
}
