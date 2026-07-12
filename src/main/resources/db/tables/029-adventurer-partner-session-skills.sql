CREATE TABLE adventurer_partner_session_skills
(
    session_id             BIGINT NOT NULL,
    character_id           INT    NOT NULL,
    skill_id               INT    NOT NULL,
    original_skill_level   INT    NULL,
    original_master_level  INT    NULL,
    original_expiration    BIGINT NULL,
    granted_skill_level    INT    NOT NULL,
    granted_master_level   INT    NOT NULL,
    granted_expiration     BIGINT NOT NULL,
    PRIMARY KEY (session_id, character_id, skill_id),
    KEY idx_partner_session_skill_character (character_id),
    CONSTRAINT fk_partner_session_skill_session FOREIGN KEY (session_id)
        REFERENCES adventurer_partner_sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_partner_session_skill_character FOREIGN KEY (character_id)
        REFERENCES characters (id) ON DELETE CASCADE
);
