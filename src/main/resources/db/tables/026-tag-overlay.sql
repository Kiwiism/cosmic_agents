CREATE TABLE character_tag_sessions
(
    id                        INT          NOT NULL AUTO_INCREMENT,
    accountid                 INT          NOT NULL,
    controlled_characterid    INT          NOT NULL,
    counterpart_characterid   INT          NOT NULL,
    status                    VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    base_snapshot_json        MEDIUMTEXT   NOT NULL,
    overlay_snapshot_json     MEDIUMTEXT   NOT NULL,
    applied_at                TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    restored_at               TIMESTAMP    NULL DEFAULT NULL,
    restore_reason            VARCHAR(32)  NULL DEFAULT NULL,
    failure_reason            VARCHAR(255) NULL DEFAULT NULL,
    PRIMARY KEY (id),
    KEY controlled_status (controlled_characterid, status),
    KEY counterpart_characterid (counterpart_characterid),
    CONSTRAINT fk_tag_session_controlled FOREIGN KEY (controlled_characterid) REFERENCES characters (id) ON DELETE CASCADE,
    CONSTRAINT fk_tag_session_counterpart FOREIGN KEY (counterpart_characterid) REFERENCES characters (id) ON DELETE CASCADE
);
