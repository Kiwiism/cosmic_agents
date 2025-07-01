CREATE TABLE lb_gifts
(
    id      INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `to`    INT          NOT NULL,
    `from`  VARCHAR(13)  NOT NULL,
    message TINYTEXT     NOT NULL,
    sn      INT UNSIGNED NOT NULL,
    ringid  INT          NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE lb_notes
(
    id        INT             NOT NULL AUTO_INCREMENT,
    `to`      VARCHAR(13)     NOT NULL DEFAULT '',
    `from`    VARCHAR(13)     NOT NULL DEFAULT '',
    message   TEXT            NOT NULL,
    timestamp BIGINT UNSIGNED NOT NULL,
    fame      INT             NOT NULL DEFAULT '0',
    deleted   INT             NOT NULL DEFAULT '0',
    PRIMARY KEY (id)
);

CREATE TABLE lb_dueypackages
(
    PackageId  INT UNSIGNED NOT NULL AUTO_INCREMENT,
    ReceiverId INT UNSIGNED NOT NULL,
    SenderName VARCHAR(13)  NOT NULL,
    Mesos      INT UNSIGNED          DEFAULT '0',
    TimeStamp  TIMESTAMP    NOT NULL DEFAULT '2015-01-01 05:00:00',
    Message    VARCHAR(200) NULL,
    Checked    TINYINT UNSIGNED      DEFAULT '1',
    Type       TINYINT UNSIGNED      DEFAULT '0',
    PRIMARY KEY (PackageId)
);

CREATE TABLE lb_dueyitems
(
    id              INT UNSIGNED NOT NULL AUTO_INCREMENT,
    PackageId       INT UNSIGNED NOT NULL DEFAULT '0',
    inventoryitemid INT UNSIGNED NOT NULL DEFAULT '0',
    PRIMARY KEY (id),
    KEY INVENTORYITEMID (inventoryitemid),
    KEY PackageId (PackageId),
    FOREIGN KEY (PackageId) REFERENCES lb_dueypackages (PackageId) ON DELETE CASCADE
);

CREATE TABLE lb_newyear
(
    id              INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    senderid        INT             NOT NULL DEFAULT '-1',
    sendername      VARCHAR(13)              DEFAULT '',
    receiverid      INT             NOT NULL DEFAULT '-1',
    receivername    VARCHAR(13)              DEFAULT '',
    message         VARCHAR(120)             DEFAULT '',
    senderdiscard   TINYINT         NOT NULL DEFAULT '0',
    receiverdiscard TINYINT         NOT NULL DEFAULT '0',
    received        TINYINT         NOT NULL DEFAULT '0',
    timesent        BIGINT UNSIGNED NOT NULL,
    timereceived    BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (id)
);