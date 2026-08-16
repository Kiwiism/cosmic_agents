-- Parameters: :run_id UUID, :agent_id TEXT, :item_id INTEGER (use 0 for every item)
SELECT jsonb_build_object(
    'reviews', COALESCE((
        SELECT jsonb_agg(jsonb_build_object(
            'reviewId', r.review_id,
            'logicalTime', r.logical_time,
            'purpose', r.purpose,
            'inventoryRevision', r.inventory_revision,
            'itemId', d.item_id,
            'slot', d.inventory_slot,
            'quantity', d.quantity,
            'disposition', d.disposition,
            'reason', d.reason,
            'legacyAction', d.legacy_action,
            'shadowAction', d.shadow_action,
            'shadowDisagreement', d.shadow_disagreement
        ) ORDER BY r.logical_time, d.inventory_type, d.inventory_slot)
        FROM inventory_review r
        JOIN item_disposition_decision d ON d.review_id = r.review_id
        WHERE r.run_id = :run_id AND r.agent_id = :agent_id
          AND (:item_id = 0 OR d.item_id = :item_id)
    ), '[]'::jsonb),
    'authorizations', COALESCE((
        SELECT jsonb_agg(to_jsonb(a) ORDER BY a.issued_at)
        FROM economic_action_authorization a
        WHERE a.run_id = :run_id AND a.agent_id = :agent_id
          AND (:item_id = 0 OR a.item_id = :item_id)
    ), '[]'::jsonb),
    'guardEvents', COALESCE((
        SELECT jsonb_agg(to_jsonb(g) ORDER BY g.logical_time)
        FROM economic_action_guard_event g
        WHERE g.run_id = :run_id AND g.agent_id = :agent_id
          AND (:item_id = 0 OR g.item_id = :item_id)
    ), '[]'::jsonb)
) AS inventory_ownership_trace;
