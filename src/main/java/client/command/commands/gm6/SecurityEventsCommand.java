package client.command.commands.gm6;

import client.Character;
import client.Client;
import client.command.Command;
import server.security.SecurityEventReviewRecord;
import server.security.SecurityEventReviewService;

import java.util.List;
import java.util.UUID;

public final class SecurityEventsCommand extends Command {
    {
        setDescription("List or review persistent security events.");
    }

    @Override
    public void execute(Client client, String[] params) {
        Character operator = client.getPlayer();
        try {
            if (params.length > 0 && "review".equalsIgnoreCase(params[0])) {
                review(operator, params);
                return;
            }
            int limit = params.length == 0 ? 10 : Integer.parseInt(params[0]);
            List<SecurityEventReviewRecord> events = SecurityEventReviewService.findOpen(limit);
            operator.dropMessage(6, "Open security events: " + events.size());
            for (SecurityEventReviewRecord event : events) {
                operator.dropMessage(6, event.eventId() + " " + event.severity() + " " + event.type()
                        + " chr=" + event.characterId() + " " + event.evidence());
            }
        } catch (RuntimeException failure) {
            operator.dropMessage(6, "Usage: !securityevents [1-100] | review <event-id> <note>");
            operator.dropMessage(6, failure.getMessage());
        }
    }

    private static void review(Character operator, String[] params) {
        if (params.length < 3) {
            throw new IllegalArgumentException("review requires an event id and note");
        }
        UUID eventId = UUID.fromString(params[1]);
        String note = String.join(" ", java.util.Arrays.copyOfRange(params, 2, params.length));
        boolean reviewed = SecurityEventReviewService.markReviewed(eventId, operator.getName(), note);
        operator.dropMessage(6, reviewed ? "Security event reviewed." : "Event is missing or already reviewed.");
    }
}
