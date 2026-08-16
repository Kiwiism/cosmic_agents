package server.agents.economy.activity;

@FunctionalInterface
public interface ActivityCalibrationSink {
    void append(ActivityCalibrationSample sample);
}
