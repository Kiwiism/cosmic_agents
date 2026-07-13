# Phase 2 Parity

Automated parity evidence:

- legacy mode continues to execute mailbox-dispatched commands inline;
- central mode holds the same action until the owning mailbox drains;
- targeted and untargeted chat preserve handled/fall-through decisions;
- reply-channel assignment still precedes typo and dialogue handling;
- follow targeting resolves the same targets and applies one action per entry;
- potion requests coalesce without mutating before mailbox drain;
- equipment packet responses are sent only after the queued action completes;
- pending-offer yes/no and expiration behavior is unchanged;
- formation routing and airshow validation/messages are unchanged;
- delayed callbacks preserve their configured delay and session-generation
  guard before mailbox delivery;
- mailbox action failure does not stop subsequent actions or another Agent.

No visible gameplay behavior was intentionally changed. Movement, combat,
loot, dialogue wording, random ranges, and capability ordering still use the
existing guarded tick and capability implementations.

Live-client validation was not run for this phase. It remains a gate before
central scheduling becomes the default.
