CREATE TABLE bot_owners
(
    bot_char_id   INT       NOT NULL,
    owner_char_id INT       NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (bot_char_id),
    KEY owner_char_id (owner_char_id),
    CONSTRAINT fk_bot_owners_bot FOREIGN KEY (bot_char_id) REFERENCES characters (id) ON DELETE CASCADE,
    CONSTRAINT fk_bot_owners_owner FOREIGN KEY (owner_char_id) REFERENCES characters (id) ON DELETE CASCADE
);
