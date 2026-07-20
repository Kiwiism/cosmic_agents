package server.agents.events.journal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import server.agents.events.AgentEvent;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded non-blocking producer with one daemon JSON-lines writer. */
final class BoundedAgentEventJournal implements AutoCloseable {
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final Path path;
    private final long maxFileBytes;
    private final ArrayBlockingQueue<AgentEvent> queue;
    private final ObjectMapper mapper;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong written = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final Thread writerThread;

    BoundedAgentEventJournal(AgentEventJournalConfig config) {
        this(config, new ObjectMapper());
    }

    BoundedAgentEventJournal(AgentEventJournalConfig config, ObjectMapper mapper) {
        this.path = config.path();
        this.maxFileBytes = config.maxFileBytes();
        this.queue = new ArrayBlockingQueue<>(config.capacity());
        this.mapper = mapper;
        this.writerThread = new Thread(this::writeLoop, "AgentEventJournal");
        this.writerThread.setDaemon(true);
        this.writerThread.start();
    }

    boolean offer(AgentEvent event) {
        if (!running.get() || event == null || !AgentDurableEventPolicy.shouldJournal(event)) {
            return false;
        }
        boolean queued = queue.offer(event);
        if (queued) {
            accepted.incrementAndGet();
        } else {
            rejected.incrementAndGet();
        }
        return queued;
    }

    AgentEventJournalSnapshot snapshot() {
        return new AgentEventJournalSnapshot(true, queue.size() + queue.remainingCapacity(), queue.size(),
                accepted.get(), rejected.get(), written.get(), failures.get());
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        writerThread.interrupt();
        try {
            writerThread.join(5000L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void writeLoop() {
        BufferedWriter writer = null;
        long currentBytes = existingBytes();
        try {
            while (running.get() || !queue.isEmpty()) {
                AgentEvent event;
                try {
                    event = queue.poll(250L, TimeUnit.MILLISECONDS);
                } catch (InterruptedException interrupted) {
                    continue;
                }
                if (event == null) {
                    if (writer != null) {
                        writer.flush();
                    }
                    continue;
                }
                try {
                    String line = mapper.writeValueAsString(record(event));
                    long lineBytes = line.getBytes(StandardCharsets.UTF_8).length + 1L;
                    if (writer == null || currentBytes > 0 && currentBytes + lineBytes > maxFileBytes) {
                        closeQuietly(writer);
                        if (currentBytes > 0 && currentBytes + lineBytes > maxFileBytes) {
                            rotate();
                            currentBytes = 0L;
                        }
                        writer = openWriter();
                    }
                    writer.write(line);
                    writer.newLine();
                    currentBytes += lineBytes;
                    written.incrementAndGet();
                } catch (IOException | RuntimeException failure) {
                    failures.incrementAndGet();
                    closeQuietly(writer);
                    writer = null;
                    currentBytes = existingBytes();
                }
            }
        } catch (IOException failure) {
            failures.incrementAndGet();
        } finally {
            closeQuietly(writer);
        }
    }

    private AgentEventJournalRecord record(AgentEvent event) {
        return new AgentEventJournalRecord(UUID.randomUUID().toString(), event.agentId(),
                event.occurredAtMs(), event.type(), event.context(),
                mapper.convertValue(event, PAYLOAD_TYPE));
    }

    private BufferedWriter openWriter() throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private long existingBytes() {
        try {
            return Files.exists(path) ? Files.size(path) : 0L;
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private void rotate() throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Path rotated = path.resolveSibling(path.getFileName() + ".1");
        Files.move(path, rotated, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void closeQuietly(BufferedWriter writer) {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException ignored) {
        }
    }
}
