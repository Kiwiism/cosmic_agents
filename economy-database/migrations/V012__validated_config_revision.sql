ALTER TABLE economy_config_revision
    ADD COLUMN config_schema_version INTEGER,
    ADD COLUMN validation_result JSONB;

UPDATE economy_config_revision
SET config_schema_version = COALESCE((normalized_config ->> 'schemaVersion')::INTEGER, 1),
    validation_result = '{"valid":true,"validator":"LEGACY_VALIDATED_STARTUP"}'::jsonb
WHERE config_schema_version IS NULL OR validation_result IS NULL;

ALTER TABLE economy_config_revision
    ALTER COLUMN config_schema_version SET NOT NULL,
    ALTER COLUMN validation_result SET NOT NULL;
