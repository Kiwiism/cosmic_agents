--liquibase formatted sql
--changeset cosmic-database-console:004

CREATE TABLE catalog_map_life (
    map_id INT NOT NULL,
    region_code VARCHAR(64) NOT NULL,
    region_name VARCHAR(120) NOT NULL,
    life_type CHAR(1) NOT NULL,
    entity_id INT NOT NULL,
    spawn_count INT NOT NULL,
    source_path VARCHAR(500) NOT NULL,
    PRIMARY KEY (map_id, life_type, entity_id),
    KEY ix_map_life_entity (life_type, entity_id),
    KEY ix_map_life_region (region_code, life_type, entity_id)
);

CREATE TABLE catalog_jobs (
    job_id INT NOT NULL,
    job_name VARCHAR(120) NOT NULL,
    source_path VARCHAR(500) NOT NULL,
    PRIMARY KEY (job_id)
);
