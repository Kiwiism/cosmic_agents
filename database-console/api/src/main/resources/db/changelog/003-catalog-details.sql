--liquibase formatted sql
--changeset cosmic-database-console:003

ALTER TABLE catalog_entities
    ADD COLUMN subtype VARCHAR(64) NULL AFTER category,
    ADD COLUMN level_value INT NULL AFTER subtype,
    ADD COLUMN job_id INT NULL AFTER level_value,
    ADD COLUMN used_in_game BOOLEAN NOT NULL DEFAULT FALSE AFTER job_id,
    ADD KEY ix_catalog_filters (entity_type, subtype, level_value, job_id);

CREATE TABLE catalog_skill_levels (
    skill_id INT NOT NULL,
    skill_level INT NOT NULL,
    properties_json JSON NOT NULL,
    source_path VARCHAR(500) NOT NULL,
    PRIMARY KEY (skill_id, skill_level)
);

CREATE TABLE console_gachapon_entries (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    location_code VARCHAR(64) NOT NULL,
    npc_id INT NULL,
    tier TINYINT NOT NULL,
    item_id INT NOT NULL,
    position INT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    source_kind VARCHAR(24) NOT NULL DEFAULT 'JAVA_SOURCE',
    source_path VARCHAR(500) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_gacha_entry (location_code, tier, item_id, position),
    KEY ix_gacha_location (location_code, tier, position)
);
