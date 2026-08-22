package server.agents.social.provider;

import server.agents.social.contracts.DialogueRequest;
import server.agents.social.contracts.DialogueResult;

import java.util.Optional;

/** Optional read-only dialogue enrichment. Providers cannot execute Agent actions. */
@FunctionalInterface
public interface DialogueProvider {
    Optional<DialogueResult> generate(DialogueRequest request);
}
