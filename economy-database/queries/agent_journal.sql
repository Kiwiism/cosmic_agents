SELECT logical_time, 'DECISION' AS record_type, decision_kind AS detail,
       jsonb_build_object('chosen', chosen_action, 'alternatives', alternatives,
                          'beliefs', beliefs_used, 'needs', needs_used,
                          'utility', utility_breakdown) AS evidence
FROM decision_journal WHERE run_id = :run_id AND agent_id = :agent_id
UNION ALL
SELECT logical_time, 'SOCIAL', event_kind,
       jsonb_build_object('text', public_text, 'intent', structured_intent,
                          'target', target_agent_id)
FROM social_event WHERE run_id = :run_id AND speaker_agent_id = :agent_id
ORDER BY logical_time;
