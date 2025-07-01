CREATE TABLE lb_keymap
(
    id          INT NOT NULL AUTO_INCREMENT,
    characterid INT NOT NULL DEFAULT '0',
    `key`       INT NOT NULL DEFAULT '0',
    type        INT NOT NULL DEFAULT '0',
    action      INT NOT NULL DEFAULT '0',
    PRIMARY KEY (id)
);

CREATE TABLE lb_quickslotkeymapped
(
    accountid INT    NOT NULL,
    keymap    BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (accountid),
    FOREIGN KEY (accountid) REFERENCES lb_accounts (id) ON DELETE CASCADE
);