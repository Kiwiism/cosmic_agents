package client.command.commands.gm6;

import client.Character;
import client.Client;
import client.command.Command;
import server.monitoring.HeapDumpService;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class HeapDumpCommand extends Command {
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    {
        setDescription("Write a heap dump for memory leak analysis.");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        String timestamp = LocalDateTime.now().format(FILE_TS);
        Path dumpPath = Path.of("logs", "heapdumps", "heap-" + timestamp + ".hprof");

        player.message("Writing heap dump to " + dumpPath + "...");
        boolean success = HeapDumpService.dumpHeap(dumpPath, true);
        player.message(success ? "Heap dump complete." : "Heap dump failed. Check server logs.");
    }
}
