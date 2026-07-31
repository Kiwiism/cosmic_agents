package server.agents.journey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Single bounded daemon writer so journey evidence never performs file I/O on Agent ticks. */
final class AgentJourneyStore {
    private static final Logger log = LoggerFactory.getLogger(AgentJourneyStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final Path ROOT = Path.of(".runtime", "agents", "journeys");
    private final int capacity;
    private final ArrayDeque<WriteRequest> queue = new ArrayDeque<>();
    private final Map<String, Long> droppedSamplesByRun = new HashMap<>();
    private final AtomicBoolean started = new AtomicBoolean();
    private int activeWrites;

    AgentJourneyStore(int capacity) {
        this.capacity = capacity;
    }

    Path createRun(AgentJourneyManifest manifest) throws IOException {
        Path directory = runDirectory(manifest.runId());
        Files.createDirectories(directory.resolve("agents"));
        Files.createDirectories(directory.resolve("failures"));
        Files.createDirectories(directory.resolve("summaries"));
        writeJson(directory.resolve("manifest.json"), manifest);
        ensureWriter();
        return directory;
    }

    void append(AgentJourneyEventRecord event) {
        enqueue(new WriteRequest(
                event.runId(),
                runDirectory(event.runId()).resolve("agents")
                        .resolve(event.agentId() + "-" + safe(event.agentName()) + ".jsonl"),
                jsonLine(event), event.critical(), true));
    }

    void writeFlightRecorder(String runId,
                             int agentId,
                             String agentName,
                             long episode,
                             List<AgentJourneyEventRecord> events) {
        Path destination = runDirectory(runId).resolve("failures")
                .resolve(agentId + "-" + safe(agentName) + "-" + episode + ".jsonl");
        StringBuilder content = new StringBuilder();
        for (AgentJourneyEventRecord event : events) {
            content.append(jsonLine(event));
        }
        enqueue(new WriteRequest(runId, destination, content.toString(), true, true));
    }

    void writeReportAsync(String runId, AgentJourneyReport report) {
        Path summaries = runDirectory(runId).resolve("summaries");
        enqueue(new WriteRequest(runId, summaries.resolve("cohort.json"),
                prettyJson(report), true, false));
        enqueue(new WriteRequest(runId, summaries.resolve("agents.csv"),
                report.agentsCsv(), true, false));
        enqueue(new WriteRequest(runId, summaries.resolve("quests.csv"),
                report.questsCsv(), true, false));
        enqueue(new WriteRequest(runId, summaries.resolve("maps.csv"),
                report.mapsCsv(), true, false));
        enqueue(new WriteRequest(runId, summaries.resolve("resources.csv"),
                report.resourcesCsv(), true, false));
        enqueue(new WriteRequest(runId, summaries.resolve("report.md"),
                report.markdown(), true, false));
    }

    Path runDirectory(String runId) {
        return ROOT.resolve(runId);
    }

    long droppedSamples(String runId) {
        synchronized (queue) {
            return droppedSamplesByRun.getOrDefault(runId, 0L);
        }
    }

    void awaitDrained(long timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        synchronized (queue) {
            while ((!queue.isEmpty() || activeWrites > 0)
                    && System.currentTimeMillis() < deadline) {
                try {
                    queue.wait(Math.min(100L, Math.max(1L, deadline - System.currentTimeMillis())));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void enqueue(WriteRequest request) {
        ensureWriter();
        synchronized (queue) {
            if (queue.size() >= capacity) {
                if (!request.critical() || !removeOldestNonCritical()) {
                    recordDrop(request.runId());
                    return;
                }
            }
            queue.addLast(request);
            queue.notifyAll();
        }
    }

    private boolean removeOldestNonCritical() {
        Iterator<WriteRequest> requests = queue.iterator();
        while (requests.hasNext()) {
            WriteRequest candidate = requests.next();
            if (!candidate.critical()) {
                requests.remove();
                recordDrop(candidate.runId());
                return true;
            }
        }
        return false;
    }

    private void ensureWriter() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        Thread writer = new Thread(this::writerLoop, "AgentJourneyWriter");
        writer.setDaemon(true);
        writer.start();
    }

    private void writerLoop() {
        while (true) {
            WriteRequest request;
            synchronized (queue) {
                while (queue.isEmpty()) {
                    try {
                        queue.wait();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                request = queue.removeFirst();
                activeWrites++;
            }
            try {
                Files.createDirectories(request.destination().getParent());
                if (request.append()) {
                    Files.writeString(request.destination(), request.content(),
                            StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND);
                } else {
                    Files.writeString(request.destination(), request.content(),
                            StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING);
                }
            } catch (IOException failure) {
                log.warn("Could not append Agent journey evidence to {}",
                        request.destination(), failure);
            } finally {
                synchronized (queue) {
                    activeWrites--;
                    if (queue.isEmpty() && activeWrites == 0) {
                        queue.notifyAll();
                    }
                }
            }
        }
    }

    private static void writeJson(Path destination, Object value) throws IOException {
        Files.createDirectories(destination.getParent());
        MAPPER.writeValue(destination.toFile(), value);
    }

    private static String jsonLine(Object value) {
        try {
            return MAPPER.writer().without(SerializationFeature.INDENT_OUTPUT)
                    .writeValueAsString(value) + System.lineSeparator();
        } catch (IOException failure) {
            throw new IllegalStateException("Could not serialize Agent journey evidence", failure);
        }
    }

    private static String prettyJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value) + System.lineSeparator();
        } catch (IOException failure) {
            throw new IllegalStateException("Could not serialize Agent journey report", failure);
        }
    }

    private void recordDrop(String runId) {
        droppedSamplesByRun.merge(runId, 1L, Long::sum);
    }

    private static String safe(String value) {
        String normalized = value == null ? "agent"
                : value.replaceAll("[^A-Za-z0-9_-]", "_");
        return normalized.isBlank() ? "agent" : normalized;
    }

    private record WriteRequest(
            String runId,
            Path destination,
            String content,
            boolean critical,
            boolean append) {
    }
}
