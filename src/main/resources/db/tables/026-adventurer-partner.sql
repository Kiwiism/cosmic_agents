CREATE TABLE adventurer_partner_links
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    account_id         INT          NOT NULL,
    world_id           INT          NOT NULL,
    first_character_id INT          NOT NULL,
    second_character_id INT         NOT NULL,
    preferred_mode     VARCHAR(24)  NOT NULL DEFAULT 'DOUBLE_PARTNER',
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_adventurer_partner_pair (first_character_id, second_character_id),
    KEY idx_adventurer_partner_first (first_character_id, enabled),
    KEY idx_adventurer_partner_second (second_character_id, enabled),
    KEY idx_adventurer_partner_account_world (account_id, world_id, enabled),
    CONSTRAINT chk_adventurer_partner_order CHECK (first_character_id < second_character_id),
    CONSTRAINT chk_adventurer_partner_mode CHECK (preferred_mode IN ('SOLO_TAG', 'DOUBLE_PARTNER')),
    CONSTRAINT fk_adventurer_partner_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_adventurer_partner_first FOREIGN KEY (first_character_id) REFERENCES characters (id) ON DELETE CASCADE,
    CONSTRAINT fk_adventurer_partner_second FOREIGN KEY (second_character_id) REFERENCES characters (id) ON DELETE CASCADE
);

CREATE TABLE adventurer_partner_sessions
(
    id                         BIGINT       NOT NULL AUTO_INCREMENT,
    link_id                    BIGINT       NOT NULL,
    player_actor_character_id  INT          NOT NULL,
    partner_character_id       INT          NOT NULL,
    mode                       VARCHAR(24)  NOT NULL,
    current_profile_orientation VARCHAR(24) NOT NULL DEFAULT 'CANONICAL',
    generation                 BIGINT       NOT NULL DEFAULT 0,
    lifecycle_status           VARCHAR(24)  NOT NULL DEFAULT 'ACTIVATING',
    activated_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_transition_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at                  TIMESTAMP    NULL,
    failure_reason             VARCHAR(512) NULL,
    PRIMARY KEY (id),
    KEY idx_adventurer_partner_session_link (link_id, lifecycle_status),
    KEY idx_adventurer_partner_session_player (player_actor_character_id, lifecycle_status),
    KEY idx_adventurer_partner_session_partner (partner_character_id, lifecycle_status),
    KEY idx_adventurer_partner_session_open (closed_at, lifecycle_status),
    CONSTRAINT chk_adventurer_partner_session_mode CHECK (mode IN ('SOLO_TAG', 'DOUBLE_PARTNER')),
    CONSTRAINT chk_adventurer_partner_orientation CHECK (current_profile_orientation IN ('CANONICAL', 'SWAPPED')),
    CONSTRAINT chk_adventurer_partner_lifecycle CHECK (
        lifecycle_status IN ('ACTIVATING', 'ACTIVE', 'SWAPPING', 'RELEASING', 'CLOSED', 'FAILED', 'RECOVERED')
    ),
    CONSTRAINT fk_adventurer_partner_session_link FOREIGN KEY (link_id)
        REFERENCES adventurer_partner_links (id) ON DELETE CASCADE,
    CONSTRAINT fk_adventurer_partner_session_player FOREIGN KEY (player_actor_character_id)
        REFERENCES characters (id) ON DELETE CASCADE,
    CONSTRAINT fk_adventurer_partner_session_partner FOREIGN KEY (partner_character_id)
        REFERENCES characters (id) ON DELETE CASCADE
);

-- Quickslots were historically account-scoped. Partner profiles require the
-- same ownership boundary as skills, keymap, and macros.
CREATE TABLE character_quickslots
(
    character_id INT    NOT NULL,
    keymap       BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (character_id),
    CONSTRAINT fk_character_quickslots_character FOREIGN KEY (character_id)
        REFERENCES characters (id) ON DELETE CASCADE
);

INSERT INTO character_quickslots (character_id, keymap)
SELECT c.id, q.keymap
FROM characters c
JOIN quickslotkeymapped q ON q.accountid = c.accountid;
