CREATE TABLE lb_wishlists
(
    id     INT NOT NULL AUTO_INCREMENT,
    charid INT NOT NULL,
    sn     INT NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE lb_specialcashitems
(
    id       INT NOT NULL,
    sn       INT NOT NULL,
    modifier INT NOT NULL COMMENT '1024 is add/remove',
    info     INT NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE lb_nxcode
(
    id         INT             NOT NULL AUTO_INCREMENT,
    code       VARCHAR(17)     NOT NULL UNIQUE,
    retriever  VARCHAR(13)              DEFAULT NULL,
    expiration BIGINT UNSIGNED NOT NULL DEFAULT '0',
    PRIMARY KEY (id)
);

CREATE TABLE lb_nxcode_items
(
    id       INT NOT NULL AUTO_INCREMENT,
    codeid   INT NOT NULL,
    type     INT NOT NULL DEFAULT '5',
    item     INT NOT NULL DEFAULT '4000000',
    quantity INT NOT NULL DEFAULT '1',
    PRIMARY KEY (id)
);

CREATE TABLE lb_nxcoupons
(
    id        INT NOT NULL AUTO_INCREMENT,
    couponid  INT NOT NULL DEFAULT '0',
    rate      INT NOT NULL DEFAULT '0',
    activeday INT NOT NULL DEFAULT '0',
    starthour INT NOT NULL DEFAULT '0',
    endhour   INT NOT NULL DEFAULT '0',
    PRIMARY KEY (id)
)