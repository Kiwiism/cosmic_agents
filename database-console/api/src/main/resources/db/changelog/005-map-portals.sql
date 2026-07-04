--liquibase formatted sql
--changeset cosmic-database-console:005

CREATE TABLE catalog_map_portals (
    map_id INT NOT NULL,
    portal_index INT NOT NULL,
    portal_name VARCHAR(120) NOT NULL,
    portal_type INT NOT NULL,
    target_map_id INT NOT NULL,
    target_portal_name VARCHAR(120) NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    source_path VARCHAR(500) NOT NULL,
    PRIMARY KEY (map_id, portal_index),
    KEY ix_portal_target (target_map_id)
);
