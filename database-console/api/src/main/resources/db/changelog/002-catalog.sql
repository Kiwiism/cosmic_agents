--liquibase formatted sql
--changeset cosmic-database-console:002

CREATE TABLE catalog_entities (
    entity_type VARCHAR(24) NOT NULL,
    entity_id INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    category VARCHAR(96) NULL,
    source_path VARCHAR(500) NOT NULL,
    properties_json JSON NULL,
    search_text TEXT NOT NULL,
    source_checksum CHAR(64) NULL,
    indexed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (entity_type, entity_id),
    FULLTEXT KEY ft_catalog_search (name, description, search_text),
    KEY ix_catalog_category (entity_type, category, name)
);

CREATE TABLE catalog_assets (
    entity_type VARCHAR(24) NOT NULL,
    entity_id INT NOT NULL,
    asset_kind VARCHAR(32) NOT NULL,
    provider VARCHAR(48) NOT NULL,
    provider_url VARCHAR(1000) NOT NULL,
    local_path VARCHAR(500) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'REMOTE',
    checked_at TIMESTAMP NULL,
    PRIMARY KEY (entity_type, entity_id, asset_kind)
);

CREATE TABLE catalog_import_runs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    status VARCHAR(24) NOT NULL,
    files_seen INT NOT NULL DEFAULT 0,
    entities_written INT NOT NULL DEFAULT 0,
    errors INT NOT NULL DEFAULT 0,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP NULL,
    message TEXT NULL,
    PRIMARY KEY (id)
);
