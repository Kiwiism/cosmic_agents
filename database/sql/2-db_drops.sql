#THIS SQL MUST BE USED AFTER 'db_database.sql'
#NEXT SQL 'db_shopupdate.sql' IS OPTIONAL

USE `cosmic`;

  CREATE TABLE IF NOT EXISTS `drop_data` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT,
    `dropperid` int(11) NOT NULL,
    `itemid` int(11) NOT NULL DEFAULT '0',
    `minimum_quantity` int(11) NOT NULL DEFAULT '1',
    `maximum_quantity` int(11) NOT NULL DEFAULT '1',
    `questid` int(11) NOT NULL DEFAULT '0',
    `chance` int(11) NOT NULL DEFAULT '0',
    PRIMARY KEY (`id`),
    UNIQUE KEY (`dropperid`, `itemid`),
    KEY `mobid` (`dropperid`),
    INDEX (dropperid, itemid)
  ) ENGINE=MyISAM  DEFAULT CHARSET=latin1 AUTO_INCREMENT=1 ;

  #pass (sorted) data from one table to another
  INSERT INTO drop_data (dropperid, itemid, minimum_quantity, maximum_quantity, questid, chance)
  (
    SELECT dropperid, itemid, minimum_quantity, maximum_quantity, questid, chance
    FROM temp_data
  );
  DROP TABLE temp_data;

  DELETE FROM drop_data WHERE dropperid >= 9300184 AND dropperid <= 9300215 AND itemid = 0;

  # MapleArrowFetcher! Set proper arrow quantity drop for the mobs.

UPDATE drop_data
SET minimum_quantity = CASE
                       WHEN dropperid = 100100 AND itemid = 2060000 THEN 1
                       WHEN dropperid = 100100 AND itemid = 2061000 THEN 1
                       WHEN dropperid = 100101 AND itemid = 2060000 THEN 2
                       WHEN dropperid = 100101 AND itemid = 2061000 THEN 2
                       WHEN dropperid = 100120 AND itemid = 2060000 THEN 1
                       WHEN dropperid = 100120 AND itemid = 2061000 THEN 1
                       WHEN dropperid = 100121 AND itemid = 2060000 THEN 4
                       WHEN dropperid = 100123 AND itemid = 2061000 THEN 9
                       WHEN dropperid = 100124 AND itemid = 2060000 THEN 11
                       WHEN dropperid = 100124 AND itemid = 2061000 THEN 11
                       WHEN dropperid = 120100 AND itemid = 2060000 THEN 2
                       WHEN dropperid = 120100 AND itemid = 2061000 THEN 2
                       WHEN dropperid = 130100 AND itemid = 2060000 THEN 5
                       WHEN dropperid = 130100 AND itemid = 2061000 THEN 5
                       WHEN dropperid = 130101 AND itemid = 2060000 THEN 5
                       WHEN dropperid = 130101 AND itemid = 2061000 THEN 5
                       WHEN dropperid = 210100 AND itemid = 2060000 THEN 7
                       WHEN dropperid = 210100 AND itemid = 2061000 THEN 7
                       WHEN dropperid = 1110100 AND itemid = 2060000 THEN 19
                       WHEN dropperid = 1110100 AND itemid = 2061000 THEN 19
                       WHEN dropperid = 1110101 AND itemid = 2060000 THEN 13
                       WHEN dropperid = 1110101 AND itemid = 2061000 THEN 13
                       WHEN dropperid = 1110130 AND itemid = 2060000 THEN 19
                       WHEN dropperid = 1110130 AND itemid = 2061000 THEN 19
                       WHEN dropperid = 1120100 AND itemid = 2060000 THEN 15
                       WHEN dropperid = 1120100 AND itemid = 2061000 THEN 15
                       WHEN dropperid = 1130100 AND itemid = 2060000 THEN 22
                       WHEN dropperid = 1130100 AND itemid = 2061000 THEN 22
                       WHEN dropperid = 1140100 AND itemid = 2060000 THEN 24
                       WHEN dropperid = 1140100 AND itemid = 2061000 THEN 24
                       WHEN dropperid = 1140130 AND itemid = 2060000 THEN 24
                       WHEN dropperid = 1140130 AND itemid = 2061000 THEN 24
                       WHEN dropperid = 1210100 AND itemid = 2060000 THEN 9
                       WHEN dropperid = 1210100 AND itemid = 2061000 THEN 9
                       WHEN dropperid = 1210101 AND itemid = 2060000 THEN 13
                       WHEN dropperid = 1210101 AND itemid = 2061000 THEN 13
                       WHEN dropperid = 1210102 AND itemid = 2060000 THEN 10
                       WHEN dropperid = 1210102 AND itemid = 2061000 THEN 10
                       WHEN dropperid = 1210103 AND itemid = 2060000 THEN 19
                       WHEN dropperid = 1210103 AND itemid = 2061000 THEN 19
                       WHEN dropperid = 2100100 AND itemid = 2060000 THEN 26
                       WHEN dropperid = 2100100 AND itemid = 2061000 THEN 26
                       WHEN dropperid = 2100101 AND itemid = 2060000 THEN 27
                       WHEN dropperid = 2100101 AND itemid = 2061000 THEN 27
                       WHEN dropperid = 2100102 AND itemid = 2060000 THEN 28
                       WHEN dropperid = 2100102 AND itemid = 2061000 THEN 28
                       WHEN dropperid = 2100103 AND itemid = 2060000 THEN 32
                       WHEN dropperid = 2100103 AND itemid = 2061000 THEN 32
                       WHEN dropperid = 2100104 AND itemid = 2060000 THEN 36
                       WHEN dropperid = 2100104 AND itemid = 2061000 THEN 36
                       WHEN dropperid = 2100105 AND itemid = 2060000 THEN 30
                       WHEN dropperid = 2100105 AND itemid = 2061000 THEN 30
                       WHEN dropperid = 2100106 AND itemid = 2060000 THEN 31
                       WHEN dropperid = 2100106 AND itemid = 2061000 THEN 31
                       WHEN dropperid = 2100107 AND itemid = 2060000 THEN 35
                       WHEN dropperid = 2100107 AND itemid = 2061000 THEN 35
                       WHEN dropperid = 2100108 AND itemid = 2060000 THEN 37
                       WHEN dropperid = 2100108 AND itemid = 2061000 THEN 37
                       WHEN dropperid = 2110200 AND itemid = 2060000 THEN 28
                       WHEN dropperid = 2110200 AND itemid = 2061000 THEN 28
                       WHEN dropperid = 2110300 AND itemid = 2060000 THEN 31
                       WHEN dropperid = 2110300 AND itemid = 2061000 THEN 31
                       WHEN dropperid = 2110301 AND itemid = 2060000 THEN 37
                       WHEN dropperid = 2110301 AND itemid = 2061000 THEN 37
                       WHEN dropperid = 2130100 AND itemid = 2060000 THEN 28
                       WHEN dropperid = 2130100 AND itemid = 2061000 THEN 28
                       WHEN dropperid = 2220000 AND itemid = 2060000 THEN 36
                       WHEN dropperid = 2220000 AND itemid = 2061000 THEN 36
                       WHEN dropperid = 2220100 AND itemid = 2060000 THEN 26
                       WHEN dropperid = 2220100 AND itemid = 2061000 THEN 26
                       WHEN dropperid = 2230100 AND itemid = 2060000 THEN 35
                       WHEN dropperid = 2230100 AND itemid = 2061000 THEN 35
                       WHEN dropperid = 2230101 AND itemid = 2060000 THEN 31
                       WHEN dropperid = 2230101 AND itemid = 2061000 THEN 31
                       WHEN dropperid = 2230102 AND itemid = 2060000 THEN 32
                       WHEN dropperid = 2230102 AND itemid = 2061000 THEN 32
                       WHEN dropperid = 2230103 AND itemid = 2060000 THEN 30
                       WHEN dropperid = 2230103 AND itemid = 2061000 THEN 30
                       WHEN dropperid = 2230104 AND itemid = 2060000 THEN 36
                       WHEN dropperid = 2230104 AND itemid = 2061000 THEN 36
                       WHEN dropperid = 2230105 AND itemid = 2060000 THEN 30
                       WHEN dropperid = 2230105 AND itemid = 2061000 THEN 30
                       WHEN dropperid = 2230106 AND itemid = 2060000 THEN 32
                       WHEN dropperid = 2230106 AND itemid = 2061000 THEN 32
                       WHEN dropperid = 2230107 AND itemid = 2060000 THEN 31
                       WHEN dropperid = 2230107 AND itemid = 2061000 THEN 31
                       WHEN dropperid = 2230108 AND itemid = 2060000 THEN 28
                       WHEN dropperid = 2230108 AND itemid = 2061000 THEN 28
                       WHEN dropperid = 2230109 AND itemid = 2060000 THEN 36
                       WHEN dropperid = 2230109 AND itemid = 2061000 THEN 36
                       WHEN dropperid = 2230110 AND itemid = 2060000 THEN 30
                       WHEN dropperid = 2230110 AND itemid = 2061000 THEN 30
                       WHEN dropperid = 2230111 AND itemid = 2060000 THEN 31
                       WHEN dropperid = 2230111 AND itemid = 2061000 THEN 31
                       WHEN dropperid = 2230131 AND itemid = 2060000 THEN 31
                       WHEN dropperid = 2230131 AND itemid = 2061000 THEN 31
                       WHEN dropperid = 2230200 AND itemid = 2060000 THEN 37
                       WHEN dropperid = 2230200 AND itemid = 2061000 THEN 37
                       WHEN dropperid = 2300100 AND itemid = 2060000 THEN 26
                       WHEN dropperid = 2300100 AND itemid = 2061000 THEN 26
                       WHEN dropperid = 3000000 AND itemid = 2060000 THEN 39
                       WHEN dropperid = 3000000 AND itemid = 2061000 THEN 39
                       WHEN dropperid = 3000005 AND itemid = 2060000 THEN 39
                       WHEN dropperid = 3000005 AND itemid = 2061000 THEN 39
                       WHEN dropperid = 3000006 AND itemid = 2060000 THEN 39
                       WHEN dropperid = 3000006 AND itemid = 2061000 THEN 39
                       WHEN dropperid = 3100101 AND itemid = 2060000 THEN 41
                       WHEN dropperid = 3100101 AND itemid = 2061000 THEN 41
                       WHEN dropperid = 3100102 AND itemid = 2060000 THEN 39
                       WHEN dropperid = 3100102 AND itemid = 2061000 THEN 39
                       WHEN dropperid = 3110101 AND itemid = 2060000 THEN 41
                       WHEN dropperid = 3110101 AND itemid = 2061000 THEN 41
                       WHEN dropperid = 3110102 AND itemid = 2060000 THEN 41
                       WHEN dropperid = 3110102 AND itemid = 2061000 THEN 41
                       WHEN dropperid = 3110300 AND itemid = 2060000 THEN 41
                       WHEN dropperid = 3110300 AND itemid = 2061000 THEN 41
                       WHEN dropperid = 3110301 AND itemid = 2060000 THEN 41
                       WHEN dropperid = 3110301 AND itemid = 2061000 THEN 41
                       WHEN dropperid = 3110302 AND itemid = 2060000 THEN 45
                       WHEN dropperid = 3110302 AND itemid = 2061000 THEN 45
                       WHEN dropperid = 3110303 AND itemid = 2060000 THEN 49
                       WHEN dropperid = 3110303 AND itemid = 2061000 THEN 49
                       WHEN dropperid = 3210100 AND itemid = 2060005 THEN 6
                       WHEN dropperid = 3210203 AND itemid = 2060000 THEN 47
                       WHEN dropperid = 3210203 AND itemid = 2061000 THEN 47
                       WHEN dropperid = 3210204 AND itemid = 2060000 THEN 44
                       WHEN dropperid = 3210204 AND itemid = 2061000 THEN 44
                       WHEN dropperid = 3210205 AND itemid = 2060000 THEN 44
                       WHEN dropperid = 3210205 AND itemid = 2061000 THEN 44
                       WHEN dropperid = 3210206 AND itemid = 2060000 THEN 47
                       WHEN dropperid = 3210206 AND itemid = 2061000 THEN 47
                       WHEN dropperid = 3210207 AND itemid = 2060000 THEN 44
                       WHEN dropperid = 3210207 AND itemid = 2060005 THEN 7
                       WHEN dropperid = 3210207 AND itemid = 2061000 THEN 44
                       WHEN dropperid = 3210208 AND itemid = 2060000 THEN 47
                       WHEN dropperid = 3210208 AND itemid = 2061000 THEN 47
                       WHEN dropperid = 3210450 AND itemid = 2060000 THEN 47
                       WHEN dropperid = 3210450 AND itemid = 2061000 THEN 47
                       WHEN dropperid = 3210800 AND itemid = 2060005 THEN 8
                       WHEN dropperid = 3220000 AND itemid = 2060000 THEN 63
                       WHEN dropperid = 3220000 AND itemid = 2061000 THEN 63
                       WHEN dropperid = 3230100 AND itemid = 2060000 THEN 45
                       WHEN dropperid = 3230100 AND itemid = 2061000 THEN 45
                       WHEN dropperid = 3230103 AND itemid = 2060000 THEN 49
                       WHEN dropperid = 3230103 AND itemid = 2061000 THEN 49
                       WHEN dropperid = 3230200 AND itemid = 2060000 THEN 45
                       WHEN dropperid = 3230200 AND itemid = 2061000 THEN 45
                       WHEN dropperid = 3230302 AND itemid = 2060000 THEN 45
                       WHEN dropperid = 3230302 AND itemid = 2061000 THEN 45
                       WHEN dropperid = 3230303 AND itemid = 2060000 THEN 48
                       WHEN dropperid = 3230303 AND itemid = 2061000 THEN 48
                       WHEN dropperid = 3230304 AND itemid = 2060000 THEN 49
                       WHEN dropperid = 3230304 AND itemid = 2061000 THEN 49
                       WHEN dropperid = 3230305 AND itemid = 2060000 THEN 51
                       WHEN dropperid = 3230305 AND itemid = 2061000 THEN 51
                       WHEN dropperid = 3230306 AND itemid = 2060000 THEN 48
                       WHEN dropperid = 3230306 AND itemid = 2061000 THEN 48
                       WHEN dropperid = 3230307 AND itemid = 2060000 THEN 40
                       WHEN dropperid = 3230307 AND itemid = 2061000 THEN 40
                       WHEN dropperid = 3230308 AND itemid = 2060000 THEN 51
                       WHEN dropperid = 3230308 AND itemid = 2061000 THEN 51
                       WHEN dropperid = 3230400 AND itemid = 2060000 THEN 39
                       WHEN dropperid = 3230400 AND itemid = 2061000 THEN 39
                       WHEN dropperid = 3230405 AND itemid = 2060000 THEN 49
                       WHEN dropperid = 3230405 AND itemid = 2061000 THEN 49
                       WHEN dropperid = 3300000 AND itemid = 2060000 THEN 39
                       WHEN dropperid = 3300000 AND itemid = 2061000 THEN 39
                       WHEN dropperid = 3300001 AND itemid = 2060000 THEN 39
                       WHEN dropperid = 3300001 AND itemid = 2061000 THEN 39
                       WHEN dropperid = 3300002 AND itemid = 2060000 THEN 40
                       WHEN dropperid = 3300002 AND itemid = 2061000 THEN 40
                       WHEN dropperid = 3300003 AND itemid = 2060000 THEN 41
                       WHEN dropperid = 3300003 AND itemid = 2061000 THEN 41
                       WHEN dropperid = 3300004 AND itemid = 2060000 THEN 43
                       WHEN dropperid = 3300004 AND itemid = 2061000 THEN 43
                       WHEN dropperid = 3300006 AND itemid = 2060000 THEN 45
                       WHEN dropperid = 3300006 AND itemid = 2061000 THEN 45
                       WHEN dropperid = 3300007 AND itemid = 2060000 THEN 45
                       WHEN dropperid = 3300007 AND itemid = 2061000 THEN 45
                       WHEN dropperid = 3300008 AND itemid = 2060000 THEN 69
                       WHEN dropperid = 3300008 AND itemid = 2061000 THEN 69
                       WHEN dropperid = 4110300 AND itemid = 2060000 THEN 54
                       WHEN dropperid = 4110300 AND itemid = 2061000 THEN 54
                       WHEN dropperid = 4110301 AND itemid = 2060000 THEN 58
                       WHEN dropperid = 4110301 AND itemid = 2061000 THEN 58
                       WHEN dropperid = 4130103 AND itemid = 2060000 THEN 85
                       WHEN dropperid = 4130103 AND itemid = 2061000 THEN 85
                       WHEN dropperid = 4230103 AND itemid = 2060000 THEN 54
                       WHEN dropperid = 4230103 AND itemid = 2061000 THEN 54
                       WHEN dropperid = 4230106 AND itemid = 2060000 THEN 58
                       WHEN dropperid = 4230106 AND itemid = 2061000 THEN 58
                       WHEN dropperid = 4230108 AND itemid = 2060000 THEN 56
                       WHEN dropperid = 4230108 AND itemid = 2061000 THEN 56
                       WHEN dropperid = 4230109 AND itemid = 2060000 THEN 54
                       WHEN dropperid = 4230109 AND itemid = 2061000 THEN 54
                       WHEN dropperid = 4230110 AND itemid = 2060000 THEN 58
                       WHEN dropperid = 4230110 AND itemid = 2061000 THEN 58
                       WHEN dropperid = 4230111 AND itemid = 2060000 THEN 53
                       WHEN dropperid = 4230111 AND itemid = 2061000 THEN 53
                       WHEN dropperid = 4230112 AND itemid = 2060000 THEN 57
                       WHEN dropperid = 4230112 AND itemid = 2061000 THEN 57
                       WHEN dropperid = 4230113 AND itemid = 2060000 THEN 52
                       WHEN dropperid = 4230113 AND itemid = 2061000 THEN 52
                       WHEN dropperid = 4230114 AND itemid = 2060000 THEN 53
                       WHEN dropperid = 4230114 AND itemid = 2061000 THEN 53
                       WHEN dropperid = 4230115 AND itemid = 2060000 THEN 60
                       WHEN dropperid = 4230115 AND itemid = 2061000 THEN 60
                       WHEN dropperid = 4230116 AND itemid = 2060000 THEN 52
                       WHEN dropperid = 4230116 AND itemid = 2061000 THEN 52
                       WHEN dropperid = 4230117 AND itemid = 2060000 THEN 54
                       WHEN dropperid = 4230117 AND itemid = 2061000 THEN 54
                       WHEN dropperid = 4230118 AND itemid = 2060000 THEN 58
                       WHEN dropperid = 4230118 AND itemid = 2061000 THEN 58
                       WHEN dropperid = 4230119 AND itemid = 2060000 THEN 53
                       WHEN dropperid = 4230119 AND itemid = 2061000 THEN 53
                       WHEN dropperid = 4230120 AND itemid = 2060000 THEN 57
                       WHEN dropperid = 4230120 AND itemid = 2061000 THEN 57
                       WHEN dropperid = 4230121 AND itemid = 2060000 THEN 60
                       WHEN dropperid = 4230121 AND itemid = 2061000 THEN 60
                       WHEN dropperid = 4230123 AND itemid = 2060000 THEN 56
                       WHEN dropperid = 4230123 AND itemid = 2061000 THEN 56
                       WHEN dropperid = 4230124 AND itemid = 2060000 THEN 54
                       WHEN dropperid = 4230124 AND itemid = 2061000 THEN 54
                       WHEN dropperid = 4230125 AND itemid = 2060000 THEN 57
                       WHEN dropperid = 4230125 AND itemid = 2061000 THEN 57
                       WHEN dropperid = 4230126 AND itemid = 2060000 THEN 61
                       WHEN dropperid = 4230126 AND itemid = 2061000 THEN 61
                       WHEN dropperid = 4230201 AND itemid = 2060000 THEN 52
                       WHEN dropperid = 4230201 AND itemid = 2061000 THEN 52
                       WHEN dropperid = 4230300 AND itemid = 2060000 THEN 58
                       WHEN dropperid = 4230300 AND itemid = 2061000 THEN 58
                       WHEN dropperid = 4230400 AND itemid = 2060000 THEN 58
                       WHEN dropperid = 4230400 AND itemid = 2061000 THEN 58
                       WHEN dropperid = 4230500 AND itemid = 2060000 THEN 52
                       WHEN dropperid = 4230500 AND itemid = 2061000 THEN 52
                       WHEN dropperid = 4230501 AND itemid = 2060000 THEN 53
                       WHEN dropperid = 4230501 AND itemid = 2061000 THEN 53
                       WHEN dropperid = 4230502 AND itemid = 2060000 THEN 56
                       WHEN dropperid = 4230502 AND itemid = 2061000 THEN 56
                       WHEN dropperid = 4230503 AND itemid = 2060000 THEN 58
                       WHEN dropperid = 4230503 AND itemid = 2061000 THEN 58
                       WHEN dropperid = 4230504 AND itemid = 2060000 THEN 58
                       WHEN dropperid = 4230504 AND itemid = 2061000 THEN 58
                       WHEN dropperid = 4230600 AND itemid = 2060000 THEN 52
                       WHEN dropperid = 4230600 AND itemid = 2061000 THEN 52
                       WHEN dropperid = 4240000 AND itemid = 2060000 THEN 64
                       WHEN dropperid = 4240000 AND itemid = 2061000 THEN 64
                       WHEN dropperid = 5120100 AND itemid = 2060000 THEN 98
                       WHEN dropperid = 5120100 AND itemid = 2061000 THEN 98
                       WHEN dropperid = 5130104 AND itemid = 2060000 THEN 71
                       WHEN dropperid = 5130104 AND itemid = 2061000 THEN 71
                       WHEN dropperid = 5140000 AND itemid = 2060000 THEN 75
                       WHEN dropperid = 5140000 AND itemid = 2061000 THEN 75
                       WHEN dropperid = 5150000 AND itemid = 2060000 THEN 77
                       WHEN dropperid = 5150000 AND itemid = 2061000 THEN 77
                       WHEN dropperid = 5200000 AND itemid = 2060001 THEN 15
                       WHEN dropperid = 5200000 AND itemid = 2061001 THEN 15
                       WHEN dropperid = 5200001 AND itemid = 2060001 THEN 19
                       WHEN dropperid = 5200001 AND itemid = 2061001 THEN 19
                       WHEN dropperid = 5200002 AND itemid = 2060001 THEN 19
                       WHEN dropperid = 5200002 AND itemid = 2061001 THEN 19
                       WHEN dropperid = 5220000 AND itemid = 2060000 THEN 100
                       WHEN dropperid = 5220000 AND itemid = 2061000 THEN 100
                       WHEN dropperid = 5220002 AND itemid = 2060000 THEN 91
                       WHEN dropperid = 5220002 AND itemid = 2061000 THEN 91
                       WHEN dropperid = 5220003 AND itemid = 2060000 THEN 107
                       WHEN dropperid = 5220003 AND itemid = 2061000 THEN 107
                       WHEN dropperid = 5400000 AND itemid = 2060001 THEN 22
                       WHEN dropperid = 5400000 AND itemid = 2061001 THEN 22
                       WHEN dropperid = 6220000 AND itemid = 2060000 THEN 118
                       WHEN dropperid = 6220000 AND itemid = 2061000 THEN 118
                       WHEN dropperid = 7120103 AND itemid = 2060003 THEN 22
                       WHEN dropperid = 7220000 AND itemid = 2060001 THEN 64
                       WHEN dropperid = 7220000 AND itemid = 2061001 THEN 64
                       WHEN dropperid = 7220001 AND itemid = 2060001 THEN 63
                       WHEN dropperid = 7220001 AND itemid = 2061001 THEN 63
                       WHEN dropperid = 7220002 AND itemid = 2060001 THEN 70
                       WHEN dropperid = 7220002 AND itemid = 2061001 THEN 70
                       WHEN dropperid = 8220000 AND itemid = 2060001 THEN 75
                       WHEN dropperid = 8220000 AND itemid = 2061001 THEN 75
                       WHEN dropperid = 8220001 AND itemid = 2060001 THEN 82
                       WHEN dropperid = 8220001 AND itemid = 2061001 THEN 82
                       WHEN dropperid = 9300011 AND itemid = 2060000 THEN 51
                       WHEN dropperid = 9300011 AND itemid = 2061000 THEN 51
                       WHEN dropperid = 9300060 AND itemid = 2060000 THEN 54
                       WHEN dropperid = 9300060 AND itemid = 2061000 THEN 54
                       WHEN dropperid = 9300131 AND itemid = 2060000 THEN 51
                       WHEN dropperid = 9300131 AND itemid = 2061000 THEN 51
                       WHEN dropperid = 9300132 AND itemid = 2060000 THEN 39
                       WHEN dropperid = 9300132 AND itemid = 2061000 THEN 39
                       WHEN dropperid = 9300133 AND itemid = 2061000 THEN 39
                       WHEN dropperid = 9300160 AND itemid = 2060000 THEN 82
                       WHEN dropperid = 9300160 AND itemid = 2061000 THEN 82
                       WHEN dropperid = 9300161 AND itemid = 2060000 THEN 82
                       WHEN dropperid = 9300161 AND itemid = 2061000 THEN 82
                       WHEN dropperid = 9300274 AND itemid = 2060000 THEN 10
                       WHEN dropperid = 9300274 AND itemid = 2061000 THEN 10
                       WHEN dropperid = 9300332 AND itemid = 2060000 THEN 52
                       WHEN dropperid = 9300334 AND itemid = 2060000 THEN 64
                       WHEN dropperid = 9300341 AND itemid = 2060000 THEN 7
                       WHEN dropperid = 9300341 AND itemid = 2061000 THEN 7
                       WHEN dropperid = 9300342 AND itemid = 2060000 THEN 10
                       WHEN dropperid = 9300342 AND itemid = 2061000 THEN 10
                       WHEN dropperid = 9300343 AND itemid = 2060000 THEN 9
                       WHEN dropperid = 9300343 AND itemid = 2061000 THEN 9
                       WHEN dropperid = 9303005 AND itemid = 2060001 THEN 17
                       WHEN dropperid = 9303005 AND itemid = 2061001 THEN 17
                       WHEN dropperid = 9303008 AND itemid = 2060001 THEN 17
                       WHEN dropperid = 9303008 AND itemid = 2061001 THEN 17
                       WHEN dropperid = 9303009 AND itemid = 2060001 THEN 33
                       WHEN dropperid = 9303009 AND itemid = 2061001 THEN 33
                       WHEN dropperid = 9400000 AND itemid = 2060001 THEN 16
                       WHEN dropperid = 9400009 AND itemid = 2060001 THEN 130
                       WHEN dropperid = 9400011 AND itemid = 2060002 THEN 17
                       WHEN dropperid = 9400100 AND itemid = 2060003 THEN 14
                       WHEN dropperid = 9400101 AND itemid = 2061003 THEN 15
                       WHEN dropperid = 9400204 AND itemid = 2060003 THEN 17
                       WHEN dropperid = 9400239 AND itemid = 2060000 THEN 31
                       WHEN dropperid = 9400239 AND itemid = 2061000 THEN 31
                       WHEN dropperid = 9400244 AND itemid = 2060000 THEN 77
                       WHEN dropperid = 9400244 AND itemid = 2061000 THEN 77
                       WHEN dropperid = 9400248 AND itemid = 2060000 THEN 31
                       WHEN dropperid = 9400248 AND itemid = 2061000 THEN 31
                       WHEN dropperid = 9400540 AND itemid = 2060004 THEN 6
                       WHEN dropperid = 9400540 AND itemid = 2061004 THEN 6
                       WHEN dropperid = 9400541 AND itemid = 2060004 THEN 6
                       WHEN dropperid = 9400541 AND itemid = 2061004 THEN 6
                       WHEN dropperid = 9400542 AND itemid = 2060004 THEN 9
                       WHEN dropperid = 9400542 AND itemid = 2061004 THEN 9
                       WHEN dropperid = 9400543 AND itemid = 2060004 THEN 10
                       WHEN dropperid = 9400543 AND itemid = 2061004 THEN 10
                       WHEN dropperid = 9400547 AND itemid = 2060000 THEN 35
                       WHEN dropperid = 9400547 AND itemid = 2061000 THEN 35
                       WHEN dropperid = 9400548 AND itemid = 2060000 THEN 39
                       WHEN dropperid = 9400548 AND itemid = 2061000 THEN 39
                       WHEN dropperid = 9400550 AND itemid = 2060000 THEN 35
                       WHEN dropperid = 9400550 AND itemid = 2061000 THEN 35
                       WHEN dropperid = 9400558 AND itemid = 2060000 THEN 39
                       WHEN dropperid = 9400558 AND itemid = 2061000 THEN 39
                       WHEN dropperid = 9400563 AND itemid = 2060000 THEN 52
                       WHEN dropperid = 9400563 AND itemid = 2061000 THEN 52
                       WHEN dropperid = 9400638 AND itemid = 2060000 THEN 26
                       WHEN dropperid = 9400638 AND itemid = 2061000 THEN 26
                       WHEN dropperid = 9420500 AND itemid = 2060000 THEN 36
                       WHEN dropperid = 9420500 AND itemid = 2061000 THEN 36
                       WHEN dropperid = 9420502 AND itemid = 2060000 THEN 23
                       WHEN dropperid = 9420502 AND itemid = 2061000 THEN 23
                       WHEN dropperid = 9420506 AND itemid = 2060000 THEN 30
                       WHEN dropperid = 9420506 AND itemid = 2061000 THEN 30
                       WHEN dropperid = 9420508 AND itemid = 2060000 THEN 56
                       WHEN dropperid = 9420508 AND itemid = 2061000 THEN 56
                       WHEN dropperid = 9420527 AND itemid = 2060001 THEN 29
                       WHEN dropperid = 9420527 AND itemid = 2061001 THEN 29
                       WHEN dropperid = 9420531 AND itemid = 2060001 THEN 38
                       WHEN dropperid = 9420531 AND itemid = 2061001 THEN 38
                       WHEN dropperid = 9500112 AND itemid = 2060001 THEN 22
                       WHEN dropperid = 9500112 AND itemid = 2061001 THEN 22
                       WHEN dropperid = 9500119 AND itemid = 2060000 THEN 51
                       WHEN dropperid = 9500119 AND itemid = 2061000 THEN 51
                       WHEN dropperid = 9500120 AND itemid = 2060000 THEN 58
                       WHEN dropperid = 9500120 AND itemid = 2061000 THEN 58
                       WHEN dropperid = 9500122 AND itemid = 2060000 THEN 64
                       WHEN dropperid = 9500123 AND itemid = 2060000 THEN 77
                       WHEN dropperid = 9500123 AND itemid = 2061000 THEN 77
                       WHEN dropperid = 9500308 AND itemid = 2060000 THEN 91
                       WHEN dropperid = 9500308 AND itemid = 2061000 THEN 91
                       WHEN dropperid = 9500310 AND itemid = 2060000 THEN 107
                       WHEN dropperid = 9500310 AND itemid = 2061000 THEN 107
                       WHEN dropperid = 9500312 AND itemid = 2060001 THEN 63
                       WHEN dropperid = 9500312 AND itemid = 2061001 THEN 63
                       WHEN dropperid = 9500313 AND itemid = 2060001 THEN 64
                       WHEN dropperid = 9500313 AND itemid = 2061001 THEN 64
                       WHEN dropperid = 9500314 AND itemid = 2060001 THEN 70
                       WHEN dropperid = 9500314 AND itemid = 2061001 THEN 70
                       WHEN dropperid = 9500321 AND itemid = 2060001 THEN 6
                       WHEN dropperid = 9500321 AND itemid = 2061001 THEN 6
                       WHEN dropperid = 9500366 AND itemid = 2060000 THEN 19
                       WHEN dropperid = 9500369 AND itemid = 2060000 THEN 19
  ELSE minimum_quantity END,
    maximum_quantity = CASE
                       WHEN dropperid = 100100 AND itemid = 2060000 THEN 2
                       WHEN dropperid = 100100 AND itemid = 2061000 THEN 2
                       WHEN dropperid = 100101 AND itemid = 2060000 THEN 3
                       WHEN dropperid = 100101 AND itemid = 2061000 THEN 3
                       WHEN dropperid = 100120 AND itemid = 2060000 THEN 2
                       WHEN dropperid = 100120 AND itemid = 2061000 THEN 2
                       WHEN dropperid = 100121 AND itemid = 2060000 THEN 5
                       WHEN dropperid = 100123 AND itemid = 2061000 THEN 12
                       WHEN dropperid = 100124 AND itemid = 2060000 THEN 14
                       WHEN dropperid = 100124 AND itemid = 2061000 THEN 14
                       WHEN dropperid = 120100 AND itemid = 2060000 THEN 3
                       WHEN dropperid = 120100 AND itemid = 2061000 THEN 3
                       WHEN dropperid = 130100 AND itemid = 2060000 THEN 7
                       WHEN dropperid = 130100 AND itemid = 2061000 THEN 7
                       WHEN dropperid = 130101 AND itemid = 2060000 THEN 7
                       WHEN dropperid = 130101 AND itemid = 2061000 THEN 7
                       WHEN dropperid = 210100 AND itemid = 2060000 THEN 9
                       WHEN dropperid = 210100 AND itemid = 2061000 THEN 9
                       WHEN dropperid = 1110100 AND itemid = 2060000 THEN 24
                       WHEN dropperid = 1110100 AND itemid = 2061000 THEN 24
                       WHEN dropperid = 1110101 AND itemid = 2060000 THEN 17
                       WHEN dropperid = 1110101 AND itemid = 2061000 THEN 17
                       WHEN dropperid = 1110130 AND itemid = 2060000 THEN 24
                       WHEN dropperid = 1110130 AND itemid = 2061000 THEN 24
                       WHEN dropperid = 1120100 AND itemid = 2060000 THEN 19
                       WHEN dropperid = 1120100 AND itemid = 2061000 THEN 19
                       WHEN dropperid = 1130100 AND itemid = 2060000 THEN 28
                       WHEN dropperid = 1130100 AND itemid = 2061000 THEN 28
                       WHEN dropperid = 1140100 AND itemid = 2060000 THEN 30
                       WHEN dropperid = 1140100 AND itemid = 2061000 THEN 30
                       WHEN dropperid = 1140130 AND itemid = 2060000 THEN 30
                       WHEN dropperid = 1140130 AND itemid = 2061000 THEN 30
                       WHEN dropperid = 1210100 AND itemid = 2060000 THEN 12
                       WHEN dropperid = 1210100 AND itemid = 2061000 THEN 12
                       WHEN dropperid = 1210101 AND itemid = 2060000 THEN 17
                       WHEN dropperid = 1210101 AND itemid = 2061000 THEN 17
                       WHEN dropperid = 1210102 AND itemid = 2060000 THEN 13
                       WHEN dropperid = 1210102 AND itemid = 2061000 THEN 13
                       WHEN dropperid = 1210103 AND itemid = 2060000 THEN 24
                       WHEN dropperid = 1210103 AND itemid = 2061000 THEN 24
                       WHEN dropperid = 2100100 AND itemid = 2060000 THEN 33
                       WHEN dropperid = 2100100 AND itemid = 2061000 THEN 33
                       WHEN dropperid = 2100101 AND itemid = 2060000 THEN 34
                       WHEN dropperid = 2100101 AND itemid = 2061000 THEN 34
                       WHEN dropperid = 2100102 AND itemid = 2060000 THEN 35
                       WHEN dropperid = 2100102 AND itemid = 2061000 THEN 35
                       WHEN dropperid = 2100103 AND itemid = 2060000 THEN 40
                       WHEN dropperid = 2100103 AND itemid = 2061000 THEN 40
                       WHEN dropperid = 2100104 AND itemid = 2060000 THEN 45
                       WHEN dropperid = 2100104 AND itemid = 2061000 THEN 45
                       WHEN dropperid = 2100105 AND itemid = 2060000 THEN 38
                       WHEN dropperid = 2100105 AND itemid = 2061000 THEN 38
                       WHEN dropperid = 2100106 AND itemid = 2060000 THEN 39
                       WHEN dropperid = 2100106 AND itemid = 2061000 THEN 39
                       WHEN dropperid = 2100107 AND itemid = 2060000 THEN 44
                       WHEN dropperid = 2100107 AND itemid = 2061000 THEN 44
                       WHEN dropperid = 2100108 AND itemid = 2060000 THEN 47
                       WHEN dropperid = 2100108 AND itemid = 2061000 THEN 47
                       WHEN dropperid = 2110200 AND itemid = 2060000 THEN 35
                       WHEN dropperid = 2110200 AND itemid = 2061000 THEN 35
                       WHEN dropperid = 2110300 AND itemid = 2060000 THEN 39
                       WHEN dropperid = 2110300 AND itemid = 2061000 THEN 39
                       WHEN dropperid = 2110301 AND itemid = 2060000 THEN 47
                       WHEN dropperid = 2110301 AND itemid = 2061000 THEN 47
                       WHEN dropperid = 2130100 AND itemid = 2060000 THEN 35
                       WHEN dropperid = 2130100 AND itemid = 2061000 THEN 35
                       WHEN dropperid = 2220000 AND itemid = 2060000 THEN 45
                       WHEN dropperid = 2220000 AND itemid = 2061000 THEN 45
                       WHEN dropperid = 2220100 AND itemid = 2060000 THEN 33
                       WHEN dropperid = 2220100 AND itemid = 2061000 THEN 33
                       WHEN dropperid = 2230100 AND itemid = 2060000 THEN 44
                       WHEN dropperid = 2230100 AND itemid = 2061000 THEN 44
                       WHEN dropperid = 2230101 AND itemid = 2060000 THEN 39
                       WHEN dropperid = 2230101 AND itemid = 2061000 THEN 39
                       WHEN dropperid = 2230102 AND itemid = 2060000 THEN 40
                       WHEN dropperid = 2230102 AND itemid = 2061000 THEN 40
                       WHEN dropperid = 2230103 AND itemid = 2060000 THEN 38
                       WHEN dropperid = 2230103 AND itemid = 2061000 THEN 38
                       WHEN dropperid = 2230104 AND itemid = 2060000 THEN 45
                       WHEN dropperid = 2230104 AND itemid = 2061000 THEN 45
                       WHEN dropperid = 2230105 AND itemid = 2060000 THEN 38
                       WHEN dropperid = 2230105 AND itemid = 2061000 THEN 38
                       WHEN dropperid = 2230106 AND itemid = 2060000 THEN 40
                       WHEN dropperid = 2230106 AND itemid = 2061000 THEN 40
                       WHEN dropperid = 2230107 AND itemid = 2060000 THEN 39
                       WHEN dropperid = 2230107 AND itemid = 2061000 THEN 39
                       WHEN dropperid = 2230108 AND itemid = 2060000 THEN 35
                       WHEN dropperid = 2230108 AND itemid = 2061000 THEN 35
                       WHEN dropperid = 2230109 AND itemid = 2060000 THEN 45
                       WHEN dropperid = 2230109 AND itemid = 2061000 THEN 45
                       WHEN dropperid = 2230110 AND itemid = 2060000 THEN 38
                       WHEN dropperid = 2230110 AND itemid = 2061000 THEN 38
                       WHEN dropperid = 2230111 AND itemid = 2060000 THEN 39
                       WHEN dropperid = 2230111 AND itemid = 2061000 THEN 39
                       WHEN dropperid = 2230131 AND itemid = 2060000 THEN 39
                       WHEN dropperid = 2230131 AND itemid = 2061000 THEN 39
                       WHEN dropperid = 2230200 AND itemid = 2060000 THEN 47
                       WHEN dropperid = 2230200 AND itemid = 2061000 THEN 47
                       WHEN dropperid = 2300100 AND itemid = 2060000 THEN 33
                       WHEN dropperid = 2300100 AND itemid = 2061000 THEN 33
                       WHEN dropperid = 3000000 AND itemid = 2060000 THEN 49
                       WHEN dropperid = 3000000 AND itemid = 2061000 THEN 49
                       WHEN dropperid = 3000005 AND itemid = 2060000 THEN 49
                       WHEN dropperid = 3000005 AND itemid = 2061000 THEN 49
                       WHEN dropperid = 3000006 AND itemid = 2060000 THEN 49
                       WHEN dropperid = 3000006 AND itemid = 2061000 THEN 49
                       WHEN dropperid = 3100101 AND itemid = 2060000 THEN 52
                       WHEN dropperid = 3100101 AND itemid = 2061000 THEN 52
                       WHEN dropperid = 3100102 AND itemid = 2060000 THEN 49
                       WHEN dropperid = 3100102 AND itemid = 2061000 THEN 49
                       WHEN dropperid = 3110101 AND itemid = 2060000 THEN 52
                       WHEN dropperid = 3110101 AND itemid = 2061000 THEN 52
                       WHEN dropperid = 3110102 AND itemid = 2060000 THEN 52
                       WHEN dropperid = 3110102 AND itemid = 2061000 THEN 52
                       WHEN dropperid = 3110300 AND itemid = 2060000 THEN 52
                       WHEN dropperid = 3110300 AND itemid = 2061000 THEN 52
                       WHEN dropperid = 3110301 AND itemid = 2060000 THEN 52
                       WHEN dropperid = 3110301 AND itemid = 2061000 THEN 52
                       WHEN dropperid = 3110302 AND itemid = 2060000 THEN 57
                       WHEN dropperid = 3110302 AND itemid = 2061000 THEN 57
                       WHEN dropperid = 3110303 AND itemid = 2060000 THEN 62
                       WHEN dropperid = 3110303 AND itemid = 2061000 THEN 62
                       WHEN dropperid = 3210100 AND itemid = 2060005 THEN 8
                       WHEN dropperid = 3210203 AND itemid = 2060000 THEN 59
                       WHEN dropperid = 3210203 AND itemid = 2061000 THEN 59
                       WHEN dropperid = 3210204 AND itemid = 2060000 THEN 55
                       WHEN dropperid = 3210204 AND itemid = 2061000 THEN 55
                       WHEN dropperid = 3210205 AND itemid = 2060000 THEN 55
                       WHEN dropperid = 3210205 AND itemid = 2061000 THEN 55
                       WHEN dropperid = 3210206 AND itemid = 2060000 THEN 59
                       WHEN dropperid = 3210206 AND itemid = 2061000 THEN 59
                       WHEN dropperid = 3210207 AND itemid = 2060000 THEN 55
                       WHEN dropperid = 3210207 AND itemid = 2060005 THEN 9
                       WHEN dropperid = 3210207 AND itemid = 2061000 THEN 55
                       WHEN dropperid = 3210208 AND itemid = 2060000 THEN 59
                       WHEN dropperid = 3210208 AND itemid = 2061000 THEN 59
                       WHEN dropperid = 3210450 AND itemid = 2060000 THEN 59
                       WHEN dropperid = 3210450 AND itemid = 2061000 THEN 59
                       WHEN dropperid = 3210800 AND itemid = 2060005 THEN 10
                       WHEN dropperid = 3220000 AND itemid = 2060000 THEN 79
                       WHEN dropperid = 3220000 AND itemid = 2061000 THEN 79
                       WHEN dropperid = 3230100 AND itemid = 2060000 THEN 57
                       WHEN dropperid = 3230100 AND itemid = 2061000 THEN 57
                       WHEN dropperid = 3230103 AND itemid = 2060000 THEN 62
                       WHEN dropperid = 3230103 AND itemid = 2061000 THEN 62
                       WHEN dropperid = 3230200 AND itemid = 2060000 THEN 57
                       WHEN dropperid = 3230200 AND itemid = 2061000 THEN 57
                       WHEN dropperid = 3230302 AND itemid = 2060000 THEN 57
                       WHEN dropperid = 3230302 AND itemid = 2061000 THEN 57
                       WHEN dropperid = 3230303 AND itemid = 2060000 THEN 60
                       WHEN dropperid = 3230303 AND itemid = 2061000 THEN 60
                       WHEN dropperid = 3230304 AND itemid = 2060000 THEN 62
                       WHEN dropperid = 3230304 AND itemid = 2061000 THEN 62
                       WHEN dropperid = 3230305 AND itemid = 2060000 THEN 64
                       WHEN dropperid = 3230305 AND itemid = 2061000 THEN 64
                       WHEN dropperid = 3230306 AND itemid = 2060000 THEN 60
                       WHEN dropperid = 3230306 AND itemid = 2061000 THEN 60
                       WHEN dropperid = 3230307 AND itemid = 2060000 THEN 50
                       WHEN dropperid = 3230307 AND itemid = 2061000 THEN 50
                       WHEN dropperid = 3230308 AND itemid = 2060000 THEN 64
                       WHEN dropperid = 3230308 AND itemid = 2061000 THEN 64
                       WHEN dropperid = 3230400 AND itemid = 2060000 THEN 49
                       WHEN dropperid = 3230400 AND itemid = 2061000 THEN 49
                       WHEN dropperid = 3230405 AND itemid = 2060000 THEN 62
                       WHEN dropperid = 3230405 AND itemid = 2061000 THEN 62
                       WHEN dropperid = 3300000 AND itemid = 2060000 THEN 49
                       WHEN dropperid = 3300000 AND itemid = 2061000 THEN 49
                       WHEN dropperid = 3300001 AND itemid = 2060000 THEN 49
                       WHEN dropperid = 3300001 AND itemid = 2061000 THEN 49
                       WHEN dropperid = 3300002 AND itemid = 2060000 THEN 50
                       WHEN dropperid = 3300002 AND itemid = 2061000 THEN 50
                       WHEN dropperid = 3300003 AND itemid = 2060000 THEN 52
                       WHEN dropperid = 3300003 AND itemid = 2061000 THEN 52
                       WHEN dropperid = 3300004 AND itemid = 2060000 THEN 54
                       WHEN dropperid = 3300004 AND itemid = 2061000 THEN 54
                       WHEN dropperid = 3300006 AND itemid = 2060000 THEN 57
                       WHEN dropperid = 3300006 AND itemid = 2061000 THEN 57
                       WHEN dropperid = 3300007 AND itemid = 2060000 THEN 57
                       WHEN dropperid = 3300007 AND itemid = 2061000 THEN 57
                       WHEN dropperid = 3300008 AND itemid = 2060000 THEN 87
                       WHEN dropperid = 3300008 AND itemid = 2061000 THEN 87
                       WHEN dropperid = 4110300 AND itemid = 2060000 THEN 68
                       WHEN dropperid = 4110300 AND itemid = 2061000 THEN 68
                       WHEN dropperid = 4110301 AND itemid = 2060000 THEN 73
                       WHEN dropperid = 4110301 AND itemid = 2061000 THEN 73
                       WHEN dropperid = 4130103 AND itemid = 2060000 THEN 107
                       WHEN dropperid = 4130103 AND itemid = 2061000 THEN 107
                       WHEN dropperid = 4230103 AND itemid = 2060000 THEN 68
                       WHEN dropperid = 4230103 AND itemid = 2061000 THEN 68
                       WHEN dropperid = 4230106 AND itemid = 2060000 THEN 73
                       WHEN dropperid = 4230106 AND itemid = 2061000 THEN 73
                       WHEN dropperid = 4230108 AND itemid = 2060000 THEN 70
                       WHEN dropperid = 4230108 AND itemid = 2061000 THEN 70
                       WHEN dropperid = 4230109 AND itemid = 2060000 THEN 68
                       WHEN dropperid = 4230109 AND itemid = 2061000 THEN 68
                       WHEN dropperid = 4230110 AND itemid = 2060000 THEN 73
                       WHEN dropperid = 4230110 AND itemid = 2061000 THEN 73
                       WHEN dropperid = 4230111 AND itemid = 2060000 THEN 67
                       WHEN dropperid = 4230111 AND itemid = 2061000 THEN 67
                       WHEN dropperid = 4230112 AND itemid = 2060000 THEN 72
                       WHEN dropperid = 4230112 AND itemid = 2061000 THEN 72
                       WHEN dropperid = 4230113 AND itemid = 2060000 THEN 65
                       WHEN dropperid = 4230113 AND itemid = 2061000 THEN 65
                       WHEN dropperid = 4230114 AND itemid = 2060000 THEN 67
                       WHEN dropperid = 4230114 AND itemid = 2061000 THEN 67
                       WHEN dropperid = 4230115 AND itemid = 2060000 THEN 75
                       WHEN dropperid = 4230115 AND itemid = 2061000 THEN 75
                       WHEN dropperid = 4230116 AND itemid = 2060000 THEN 65
                       WHEN dropperid = 4230116 AND itemid = 2061000 THEN 65
                       WHEN dropperid = 4230117 AND itemid = 2060000 THEN 68
                       WHEN dropperid = 4230117 AND itemid = 2061000 THEN 68
                       WHEN dropperid = 4230118 AND itemid = 2060000 THEN 73
                       WHEN dropperid = 4230118 AND itemid = 2061000 THEN 73
                       WHEN dropperid = 4230119 AND itemid = 2060000 THEN 67
                       WHEN dropperid = 4230119 AND itemid = 2061000 THEN 67
                       WHEN dropperid = 4230120 AND itemid = 2060000 THEN 72
                       WHEN dropperid = 4230120 AND itemid = 2061000 THEN 72
                       WHEN dropperid = 4230121 AND itemid = 2060000 THEN 75
                       WHEN dropperid = 4230121 AND itemid = 2061000 THEN 75
                       WHEN dropperid = 4230123 AND itemid = 2060000 THEN 70
                       WHEN dropperid = 4230123 AND itemid = 2061000 THEN 70
                       WHEN dropperid = 4230124 AND itemid = 2060000 THEN 68
                       WHEN dropperid = 4230124 AND itemid = 2061000 THEN 68
                       WHEN dropperid = 4230125 AND itemid = 2060000 THEN 72
                       WHEN dropperid = 4230125 AND itemid = 2061000 THEN 72
                       WHEN dropperid = 4230126 AND itemid = 2060000 THEN 77
                       WHEN dropperid = 4230126 AND itemid = 2061000 THEN 77
                       WHEN dropperid = 4230201 AND itemid = 2060000 THEN 65
                       WHEN dropperid = 4230201 AND itemid = 2061000 THEN 65
                       WHEN dropperid = 4230300 AND itemid = 2060000 THEN 73
                       WHEN dropperid = 4230300 AND itemid = 2061000 THEN 73
                       WHEN dropperid = 4230400 AND itemid = 2060000 THEN 73
                       WHEN dropperid = 4230400 AND itemid = 2061000 THEN 73
                       WHEN dropperid = 4230500 AND itemid = 2060000 THEN 65
                       WHEN dropperid = 4230500 AND itemid = 2061000 THEN 65
                       WHEN dropperid = 4230501 AND itemid = 2060000 THEN 67
                       WHEN dropperid = 4230501 AND itemid = 2061000 THEN 67
                       WHEN dropperid = 4230502 AND itemid = 2060000 THEN 70
                       WHEN dropperid = 4230502 AND itemid = 2061000 THEN 70
                       WHEN dropperid = 4230503 AND itemid = 2060000 THEN 73
                       WHEN dropperid = 4230503 AND itemid = 2061000 THEN 73
                       WHEN dropperid = 4230504 AND itemid = 2060000 THEN 73
                       WHEN dropperid = 4230504 AND itemid = 2061000 THEN 73
                       WHEN dropperid = 4230600 AND itemid = 2060000 THEN 65
                       WHEN dropperid = 4230600 AND itemid = 2061000 THEN 65
                       WHEN dropperid = 4240000 AND itemid = 2060000 THEN 80
                       WHEN dropperid = 4240000 AND itemid = 2061000 THEN 80
                       WHEN dropperid = 5120100 AND itemid = 2060000 THEN 123
                       WHEN dropperid = 5120100 AND itemid = 2061000 THEN 123
                       WHEN dropperid = 5130104 AND itemid = 2060000 THEN 89
                       WHEN dropperid = 5130104 AND itemid = 2061000 THEN 89
                       WHEN dropperid = 5140000 AND itemid = 2060000 THEN 94
                       WHEN dropperid = 5140000 AND itemid = 2061000 THEN 94
                       WHEN dropperid = 5150000 AND itemid = 2060000 THEN 97
                       WHEN dropperid = 5150000 AND itemid = 2061000 THEN 97
                       WHEN dropperid = 5200000 AND itemid = 2060001 THEN 19
                       WHEN dropperid = 5200000 AND itemid = 2061001 THEN 19
                       WHEN dropperid = 5200001 AND itemid = 2060001 THEN 24
                       WHEN dropperid = 5200001 AND itemid = 2061001 THEN 24
                       WHEN dropperid = 5200002 AND itemid = 2060001 THEN 24
                       WHEN dropperid = 5200002 AND itemid = 2061001 THEN 24
                       WHEN dropperid = 5220000 AND itemid = 2060000 THEN 125
                       WHEN dropperid = 5220000 AND itemid = 2061000 THEN 125
                       WHEN dropperid = 5220002 AND itemid = 2060000 THEN 114
                       WHEN dropperid = 5220002 AND itemid = 2061000 THEN 114
                       WHEN dropperid = 5220003 AND itemid = 2060000 THEN 134
                       WHEN dropperid = 5220003 AND itemid = 2061000 THEN 134
                       WHEN dropperid = 5400000 AND itemid = 2060001 THEN 28
                       WHEN dropperid = 5400000 AND itemid = 2061001 THEN 28
                       WHEN dropperid = 6220000 AND itemid = 2060000 THEN 148
                       WHEN dropperid = 6220000 AND itemid = 2061000 THEN 148
                       WHEN dropperid = 7120103 AND itemid = 2060003 THEN 28
                       WHEN dropperid = 7220000 AND itemid = 2060001 THEN 81
                       WHEN dropperid = 7220000 AND itemid = 2061001 THEN 81
                       WHEN dropperid = 7220001 AND itemid = 2060001 THEN 79
                       WHEN dropperid = 7220001 AND itemid = 2061001 THEN 79
                       WHEN dropperid = 7220002 AND itemid = 2060001 THEN 87
                       WHEN dropperid = 7220002 AND itemid = 2061001 THEN 87
                       WHEN dropperid = 8220000 AND itemid = 2060001 THEN 94
                       WHEN dropperid = 8220000 AND itemid = 2061001 THEN 94
                       WHEN dropperid = 8220001 AND itemid = 2060001 THEN 102
                       WHEN dropperid = 8220001 AND itemid = 2061001 THEN 102
                       WHEN dropperid = 9300011 AND itemid = 2060000 THEN 64
                       WHEN dropperid = 9300011 AND itemid = 2061000 THEN 64
                       WHEN dropperid = 9300060 AND itemid = 2060000 THEN 68
                       WHEN dropperid = 9300060 AND itemid = 2061000 THEN 68
                       WHEN dropperid = 9300131 AND itemid = 2060000 THEN 64
                       WHEN dropperid = 9300131 AND itemid = 2061000 THEN 64
                       WHEN dropperid = 9300132 AND itemid = 2060000 THEN 49
                       WHEN dropperid = 9300132 AND itemid = 2061000 THEN 49
                       WHEN dropperid = 9300133 AND itemid = 2061000 THEN 49
                       WHEN dropperid = 9300160 AND itemid = 2060000 THEN 103
                       WHEN dropperid = 9300160 AND itemid = 2061000 THEN 103
                       WHEN dropperid = 9300161 AND itemid = 2060000 THEN 103
                       WHEN dropperid = 9300161 AND itemid = 2061000 THEN 103
                       WHEN dropperid = 9300274 AND itemid = 2060000 THEN 13
                       WHEN dropperid = 9300274 AND itemid = 2061000 THEN 13
                       WHEN dropperid = 9300332 AND itemid = 2060000 THEN 65
                       WHEN dropperid = 9300334 AND itemid = 2060000 THEN 80
                       WHEN dropperid = 9300341 AND itemid = 2060000 THEN 9
                       WHEN dropperid = 9300341 AND itemid = 2061000 THEN 9
                       WHEN dropperid = 9300342 AND itemid = 2060000 THEN 13
                       WHEN dropperid = 9300342 AND itemid = 2061000 THEN 13
                       WHEN dropperid = 9300343 AND itemid = 2060000 THEN 12
                       WHEN dropperid = 9300343 AND itemid = 2061000 THEN 12
                       WHEN dropperid = 9303005 AND itemid = 2060001 THEN 21
                       WHEN dropperid = 9303005 AND itemid = 2061001 THEN 21
                       WHEN dropperid = 9303008 AND itemid = 2060001 THEN 21
                       WHEN dropperid = 9303008 AND itemid = 2061001 THEN 21
                       WHEN dropperid = 9303009 AND itemid = 2060001 THEN 41
                       WHEN dropperid = 9303009 AND itemid = 2061001 THEN 41
                       WHEN dropperid = 9400000 AND itemid = 2060001 THEN 20
                       WHEN dropperid = 9400009 AND itemid = 2060001 THEN 163
                       WHEN dropperid = 9400011 AND itemid = 2060002 THEN 21
                       WHEN dropperid = 9400100 AND itemid = 2060003 THEN 18
                       WHEN dropperid = 9400101 AND itemid = 2061003 THEN 19
                       WHEN dropperid = 9400204 AND itemid = 2060003 THEN 22
                       WHEN dropperid = 9400239 AND itemid = 2060000 THEN 39
                       WHEN dropperid = 9400239 AND itemid = 2061000 THEN 39
                       WHEN dropperid = 9400244 AND itemid = 2060000 THEN 97
                       WHEN dropperid = 9400244 AND itemid = 2061000 THEN 97
                       WHEN dropperid = 9400248 AND itemid = 2060000 THEN 39
                       WHEN dropperid = 9400248 AND itemid = 2061000 THEN 39
                       WHEN dropperid = 9400540 AND itemid = 2060004 THEN 8
                       WHEN dropperid = 9400540 AND itemid = 2061004 THEN 8
                       WHEN dropperid = 9400541 AND itemid = 2060004 THEN 8
                       WHEN dropperid = 9400541 AND itemid = 2061004 THEN 8
                       WHEN dropperid = 9400542 AND itemid = 2060004 THEN 11
                       WHEN dropperid = 9400542 AND itemid = 2061004 THEN 11
                       WHEN dropperid = 9400543 AND itemid = 2060004 THEN 13
                       WHEN dropperid = 9400543 AND itemid = 2061004 THEN 13
                       WHEN dropperid = 9400547 AND itemid = 2060000 THEN 44
                       WHEN dropperid = 9400547 AND itemid = 2061000 THEN 44
                       WHEN dropperid = 9400548 AND itemid = 2060000 THEN 49
                       WHEN dropperid = 9400548 AND itemid = 2061000 THEN 49
                       WHEN dropperid = 9400550 AND itemid = 2060000 THEN 44
                       WHEN dropperid = 9400550 AND itemid = 2061000 THEN 44
                       WHEN dropperid = 9400558 AND itemid = 2060000 THEN 49
                       WHEN dropperid = 9400558 AND itemid = 2061000 THEN 49
                       WHEN dropperid = 9400563 AND itemid = 2060000 THEN 65
                       WHEN dropperid = 9400563 AND itemid = 2061000 THEN 65
                       WHEN dropperid = 9400638 AND itemid = 2060000 THEN 33
                       WHEN dropperid = 9400638 AND itemid = 2061000 THEN 33
                       WHEN dropperid = 9420500 AND itemid = 2060000 THEN 45
                       WHEN dropperid = 9420500 AND itemid = 2061000 THEN 45
                       WHEN dropperid = 9420502 AND itemid = 2060000 THEN 29
                       WHEN dropperid = 9420502 AND itemid = 2061000 THEN 29
                       WHEN dropperid = 9420506 AND itemid = 2060000 THEN 38
                       WHEN dropperid = 9420506 AND itemid = 2061000 THEN 38
                       WHEN dropperid = 9420508 AND itemid = 2060000 THEN 70
                       WHEN dropperid = 9420508 AND itemid = 2061000 THEN 70
                       WHEN dropperid = 9420527 AND itemid = 2060001 THEN 36
                       WHEN dropperid = 9420527 AND itemid = 2061001 THEN 36
                       WHEN dropperid = 9420531 AND itemid = 2060001 THEN 48
                       WHEN dropperid = 9420531 AND itemid = 2061001 THEN 48
                       WHEN dropperid = 9500112 AND itemid = 2060001 THEN 28
                       WHEN dropperid = 9500112 AND itemid = 2061001 THEN 28
                       WHEN dropperid = 9500119 AND itemid = 2060000 THEN 64
                       WHEN dropperid = 9500119 AND itemid = 2061000 THEN 64
                       WHEN dropperid = 9500120 AND itemid = 2060000 THEN 73
                       WHEN dropperid = 9500120 AND itemid = 2061000 THEN 73
                       WHEN dropperid = 9500122 AND itemid = 2060000 THEN 80
                       WHEN dropperid = 9500123 AND itemid = 2060000 THEN 97
                       WHEN dropperid = 9500123 AND itemid = 2061000 THEN 97
                       WHEN dropperid = 9500308 AND itemid = 2060000 THEN 114
                       WHEN dropperid = 9500308 AND itemid = 2061000 THEN 114
                       WHEN dropperid = 9500310 AND itemid = 2060000 THEN 134
                       WHEN dropperid = 9500310 AND itemid = 2061000 THEN 134
                       WHEN dropperid = 9500312 AND itemid = 2060001 THEN 79
                       WHEN dropperid = 9500312 AND itemid = 2061001 THEN 79
                       WHEN dropperid = 9500313 AND itemid = 2060001 THEN 81
                       WHEN dropperid = 9500313 AND itemid = 2061001 THEN 81
                       WHEN dropperid = 9500314 AND itemid = 2060001 THEN 87
                       WHEN dropperid = 9500314 AND itemid = 2061001 THEN 87
                       WHEN dropperid = 9500321 AND itemid = 2060001 THEN 8
                       WHEN dropperid = 9500321 AND itemid = 2061001 THEN 8
                       WHEN dropperid = 9500366 AND itemid = 2060000 THEN 24
                       WHEN dropperid = 9500369 AND itemid = 2060000 THEN 24
  ELSE maximum_quantity END
;

  UPDATE drop_data SET `chance`=1287 WHERE `chance`=1500;

  # MapleSkillbookChanceFetcher! Tuning up some skillbook drop chances in order to fit their dropper's availability (whether's a boss or not) and level.
  # thanks unnqca for reporting some skillbooks having unusually high drop chances.
  REPLACE INTO drop_data (`dropperid`, `itemid`, `minimum_quantity`, `maximum_quantity`, `questid`, `chance`) VALUES
(851000, 2290132, 1, 1, 0, 3861),
(7090000, 2290087, 1, 1, 0, 10000),
(8090000, 2290045, 1, 1, 0, 10000),
(8140103, 2290044, 1, 1, 0, 500),
(8140511, 2290009, 1, 1, 0, 500),
(8140511, 2290050, 1, 1, 0, 500),
(8140511, 2290083, 1, 1, 0, 500),
(8140511, 2290134, 1, 1, 0, 1500),
(8140512, 2290013, 1, 1, 0, 500),
(8140512, 2290067, 1, 1, 0, 500),
(8140512, 2290082, 1, 1, 0, 500),
(8140512, 2290097, 1, 1, 0, 500),
(8140512, 2290116, 1, 1, 0, 500),
(8140512, 2290131, 1, 1, 0, 1500),
(8140600, 2290132, 1, 1, 0, 1500),
(8140700, 2290106, 1, 1, 0, 500),
(8140700, 2290126, 1, 1, 0, 1500),
(8140701, 2290122, 1, 1, 0, 500),
(8140702, 2290112, 1, 1, 0, 500),
(8140703, 2290088, 1, 1, 0, 500),
(8140703, 2290099, 1, 1, 0, 500),
(8141000, 2290082, 1, 1, 0, 500),
(8141000, 2290097, 1, 1, 0, 500),
(8141100, 2280005, 1, 1, 0, 500),
(8141300, 2290098, 1, 1, 0, 500),
(8142100, 2290032, 1, 1, 0, 500),
(8142100, 2290082, 1, 1, 0, 500),
(8142100, 2290114, 1, 1, 0, 500),
(8143000, 2280004, 1, 1, 0, 500),
(8150000, 2280013, 1, 1, 0, 30000),
(8150000, 2290070, 1, 1, 0, 10000),
(8150000, 2290091, 1, 1, 0, 10000),
(8150100, 2290042, 1, 1, 0, 500),
(8150100, 2290053, 1, 1, 0, 500),
(8150100, 2290073, 1, 1, 0, 500),
(8150100, 2290102, 1, 1, 0, 500),
(8150100, 2290118, 1, 1, 0, 500),
(8150101, 2290017, 1, 1, 0, 500),
(8150101, 2290021, 1, 1, 0, 500),
(8150101, 2290035, 1, 1, 0, 500),
(8150101, 2290042, 1, 1, 0, 500),
(8150101, 2290052, 1, 1, 0, 500),
(8150101, 2290102, 1, 1, 0, 500),
(8150200, 2290024, 1, 1, 0, 500),
(8150200, 2290100, 1, 1, 0, 500),
(8150200, 2290135, 1, 1, 0, 1500),
(8150201, 2290004, 1, 1, 0, 500),
(8150201, 2290006, 1, 1, 0, 500),
(8150201, 2290024, 1, 1, 0, 500),
(8150201, 2290036, 1, 1, 0, 500),
(8150201, 2290056, 1, 1, 0, 500),
(8150201, 2290072, 1, 1, 0, 500),
(8150201, 2290078, 1, 1, 0, 500),
(8150201, 2290117, 1, 1, 0, 500),
(8150300, 2290003, 1, 1, 0, 500),
(8150300, 2290033, 1, 1, 0, 500),
(8150300, 2290111, 1, 1, 0, 500),
(8150300, 2290120, 1, 1, 0, 500),
(8150300, 2290127, 1, 1, 0, 1500),
(8150301, 2290023, 1, 1, 0, 500),
(8150301, 2290029, 1, 1, 0, 500),
(8150301, 2290101, 1, 1, 0, 500),
(8150301, 2290107, 1, 1, 0, 500),
(8150302, 2290010, 1, 1, 0, 500),
(8150302, 2290019, 1, 1, 0, 500),
(8150302, 2290026, 1, 1, 0, 500),
(8150302, 2290076, 1, 1, 0, 500),
(8150302, 2290085, 1, 1, 0, 500),
(8150302, 2290096, 1, 1, 0, 500),
(8150302, 2290113, 1, 1, 0, 500),
(8150302, 2290119, 1, 1, 0, 500),
(8150302, 2290128, 1, 1, 0, 1500),
(8160000, 2290017, 1, 1, 0, 500),
(8160000, 2290045, 1, 1, 0, 500),
(8160000, 2290065, 1, 1, 0, 500),
(8160000, 2290067, 1, 1, 0, 500),
(8160000, 2290081, 1, 1, 0, 500),
(8170000, 2290012, 1, 1, 0, 500),
(8170000, 2290086, 1, 1, 0, 500),
(8170000, 2290087, 1, 1, 0, 500),
(8170000, 2290134, 1, 1, 0, 1500),
(8180000, 2290002, 1, 1, 0, 10000),
(8180000, 2290003, 1, 1, 0, 10000),
(8180000, 2290014, 1, 1, 0, 10000),
(8180000, 2290015, 1, 1, 0, 10000),
(8180000, 2290030, 1, 1, 0, 10000),
(8180000, 2290035, 1, 1, 0, 10000),
(8180000, 2290036, 1, 1, 0, 10000),
(8180000, 2290063, 1, 1, 0, 10000),
(8180000, 2290080, 1, 1, 0, 10000),
(8180000, 2290098, 1, 1, 0, 10000),
(8180000, 2290101, 1, 1, 0, 10000),
(8180000, 2290117, 1, 1, 0, 10000),
(8180000, 2290130, 1, 1, 0, 30000),
(8180001, 2290018, 1, 1, 0, 10000),
(8180001, 2290019, 1, 1, 0, 10000),
(8180001, 2290032, 1, 1, 0, 10000),
(8180001, 2290042, 1, 1, 0, 10000),
(8180001, 2290058, 1, 1, 0, 10000),
(8180001, 2290059, 1, 1, 0, 10000),
(8180001, 2290068, 1, 1, 0, 10000),
(8180001, 2290069, 1, 1, 0, 10000),
(8180001, 2290072, 1, 1, 0, 10000),
(8180001, 2290092, 1, 1, 0, 10000),
(8180001, 2290099, 1, 1, 0, 10000),
(8180001, 2290100, 1, 1, 0, 10000),
(8180001, 2290102, 1, 1, 0, 10000),
(8180001, 2290119, 1, 1, 0, 10000),
(8180001, 2290128, 1, 1, 0, 30000),
(8190000, 2280016, 1, 1, 0, 1500),
(8190000, 2290030, 1, 1, 0, 500),
(8190000, 2290044, 1, 1, 0, 500),
(8190000, 2290054, 1, 1, 0, 500),
(8190000, 2290066, 1, 1, 0, 500),
(8190000, 2290075, 1, 1, 0, 500),
(8190000, 2290092, 1, 1, 0, 500),
(8190000, 2290103, 1, 1, 0, 500),
(8190002, 2290000, 1, 1, 0, 500),
(8190002, 2290008, 1, 1, 0, 500),
(8190002, 2290018, 1, 1, 0, 500),
(8190002, 2290038, 1, 1, 0, 500),
(8190002, 2290060, 1, 1, 0, 500),
(8190002, 2290080, 1, 1, 0, 500),
(8190002, 2290124, 1, 1, 0, 500),
(8190003, 2280013, 1, 1, 0, 1500),
(8190003, 2290007, 1, 1, 0, 500),
(8190003, 2290012, 1, 1, 0, 500),
(8190003, 2290014, 1, 1, 0, 500),
(8190003, 2290033, 1, 1, 0, 500),
(8190003, 2290045, 1, 1, 0, 500),
(8190003, 2290050, 1, 1, 0, 500),
(8190003, 2290055, 1, 1, 0, 500),
(8190003, 2290062, 1, 1, 0, 500),
(8190003, 2290063, 1, 1, 0, 500),
(8190003, 2290070, 1, 1, 0, 500),
(8190003, 2290086, 1, 1, 0, 500),
(8190003, 2290108, 1, 1, 0, 500),
(8190003, 2290133, 1, 1, 0, 1500),
(8190004, 2290002, 1, 1, 0, 500),
(8190004, 2290009, 1, 1, 0, 500),
(8190004, 2290021, 1, 1, 0, 500),
(8190004, 2290034, 1, 1, 0, 500),
(8190004, 2290041, 1, 1, 0, 500),
(8190004, 2290052, 1, 1, 0, 500),
(8190004, 2290053, 1, 1, 0, 500),
(8190004, 2290058, 1, 1, 0, 500),
(8190004, 2290068, 1, 1, 0, 500),
(8190004, 2290071, 1, 1, 0, 500),
(8190004, 2290073, 1, 1, 0, 500),
(8190004, 2290090, 1, 1, 0, 500),
(8190004, 2290112, 1, 1, 0, 500),
(8190004, 2290121, 1, 1, 0, 500),
(8190004, 2290130, 1, 1, 0, 1500),
(8190005, 2290000, 1, 1, 0, 500),
(8190005, 2290008, 1, 1, 0, 500),
(8190005, 2290018, 1, 1, 0, 500),
(8190005, 2290038, 1, 1, 0, 500),
(8190005, 2290060, 1, 1, 0, 500),
(8190005, 2290080, 1, 1, 0, 500),
(8190005, 2290124, 1, 1, 0, 500),
(8200000, 2290005, 1, 1, 0, 500),
(8200000, 2290011, 1, 1, 0, 500),
(8200000, 2290114, 1, 1, 0, 500),
(8200001, 2280015, 1, 1, 0, 1500),
(8200001, 2290050, 1, 1, 0, 500),
(8200001, 2290059, 1, 1, 0, 500),
(8200001, 2290065, 1, 1, 0, 500),
(8200001, 2290129, 1, 1, 0, 1500),
(8200002, 2290062, 1, 1, 0, 500),
(8200002, 2290066, 1, 1, 0, 500),
(8200002, 2290070, 1, 1, 0, 500),
(8200002, 2290131, 1, 1, 0, 1500),
(8200002, 2290139, 1, 1, 0, 1500),
(8200003, 2290012, 1, 1, 0, 500),
(8200003, 2290056, 1, 1, 0, 500),
(8200003, 2290071, 1, 1, 0, 500),
(8200003, 2290101, 1, 1, 0, 500),
(8200003, 2290136, 1, 1, 0, 1500),
(8200004, 2280016, 1, 1, 0, 1500),
(8200004, 2290069, 1, 1, 0, 500),
(8200004, 2290072, 1, 1, 0, 500),
(8200004, 2290073, 1, 1, 0, 500),
(8200004, 2290127, 1, 1, 0, 1500),
(8200004, 2290134, 1, 1, 0, 1500),
(8200005, 2280014, 1, 1, 0, 1500),
(8200005, 2290078, 1, 1, 0, 500),
(8200005, 2290079, 1, 1, 0, 500),
(8200005, 2290095, 1, 1, 0, 500),
(8200006, 2290003, 1, 1, 0, 500),
(8200006, 2290064, 1, 1, 0, 500),
(8200006, 2290076, 1, 1, 0, 500),
(8200006, 2290077, 1, 1, 0, 500),
(8200006, 2290129, 1, 1, 0, 1500),
(8200006, 2290138, 1, 1, 0, 1500),
(8200007, 2290006, 1, 1, 0, 500),
(8200007, 2290007, 1, 1, 0, 500),
(8200007, 2290011, 1, 1, 0, 500),
(8200007, 2290016, 1, 1, 0, 500),
(8200007, 2290125, 1, 1, 0, 500),
(8200007, 2290136, 1, 1, 0, 1500),
(8200008, 2290006, 1, 1, 0, 500),
(8200008, 2290051, 1, 1, 0, 500),
(8200008, 2290121, 1, 1, 0, 500),
(8200008, 2290122, 1, 1, 0, 500),
(8200008, 2290133, 1, 1, 0, 1500),
(8200009, 2290013, 1, 1, 0, 500),
(8200009, 2290016, 1, 1, 0, 500),
(8200009, 2290031, 1, 1, 0, 500),
(8200009, 2290039, 1, 1, 0, 500),
(8200010, 2290026, 1, 1, 0, 500),
(8200010, 2290059, 1, 1, 0, 500),
(8200010, 2290088, 1, 1, 0, 500),
(8200010, 2290089, 1, 1, 0, 500),
(8200010, 2290127, 1, 1, 0, 1500),
(8200011, 2290001, 1, 1, 0, 750),
(8200011, 2290040, 1, 1, 0, 750),
(8200011, 2290046, 1, 1, 0, 750),
(8200011, 2290048, 1, 1, 0, 750),
(8200011, 2290049, 1, 1, 0, 750),
(8200011, 2290114, 1, 1, 0, 750),
(8200011, 2290137, 1, 1, 0, 2250),
(8200012, 2290041, 1, 1, 0, 750),
(8200012, 2290092, 1, 1, 0, 750),
(8200012, 2290093, 1, 1, 0, 750),
(8200012, 2290115, 1, 1, 0, 750),
(8200012, 2290137, 1, 1, 0, 2250),
(8200012, 2290139, 1, 1, 0, 2250),
(8220002, 2290020, 1, 1, 0, 10000),
(8220002, 2290081, 1, 1, 0, 10000),
(8220002, 2290085, 1, 1, 0, 10000),
(8220002, 2290133, 1, 1, 0, 30000),
(8220003, 2290006, 1, 1, 0, 10000),
(8220003, 2290030, 1, 1, 0, 10000),
(8220003, 2290031, 1, 1, 0, 10000),
(8220003, 2290032, 1, 1, 0, 10000),
(8220003, 2290033, 1, 1, 0, 10000),
(8220003, 2290060, 1, 1, 0, 10000),
(8220003, 2290061, 1, 1, 0, 10000),
(8220003, 2290076, 1, 1, 0, 10000),
(8220003, 2290077, 1, 1, 0, 10000),
(8220003, 2290104, 1, 1, 0, 10000),
(8220003, 2290105, 1, 1, 0, 10000),
(8220003, 2290117, 1, 1, 0, 10000),
(8220003, 2290118, 1, 1, 0, 10000),
(8220004, 2290018, 1, 1, 0, 10000),
(8220004, 2290019, 1, 1, 0, 10000),
(8220004, 2290024, 1, 1, 0, 10000),
(8220004, 2290025, 1, 1, 0, 10000),
(8220004, 2290058, 1, 1, 0, 10000),
(8220004, 2290059, 1, 1, 0, 10000),
(8220004, 2290076, 1, 1, 0, 10000),
(8220004, 2290077, 1, 1, 0, 10000),
(8220004, 2290106, 1, 1, 0, 10000),
(8220004, 2290127, 1, 1, 0, 30000),
(8220004, 2290134, 1, 1, 0, 30000),
(8220005, 2290002, 1, 1, 0, 15000),
(8220005, 2290003, 1, 1, 0, 15000),
(8220005, 2290036, 1, 1, 0, 15000),
(8220005, 2290037, 1, 1, 0, 15000),
(8220005, 2290055, 1, 1, 0, 15000),
(8220005, 2290080, 1, 1, 0, 15000),
(8220005, 2290099, 1, 1, 0, 15000),
(8220005, 2290131, 1, 1, 0, 45000),
(8220005, 2290136, 1, 1, 0, 45000),
(8220006, 2290012, 1, 1, 0, 20000),
(8220006, 2290013, 1, 1, 0, 20000),
(8220006, 2290042, 1, 1, 0, 20000),
(8220006, 2290043, 1, 1, 0, 20000),
(8220006, 2290060, 1, 1, 0, 20000),
(8220006, 2290061, 1, 1, 0, 20000),
(8220006, 2290090, 1, 1, 0, 20000),
(8220006, 2290119, 1, 1, 0, 20000),
(8220006, 2290120, 1, 1, 0, 20000),
(8220006, 2290135, 1, 1, 0, 60000),
(8220006, 2290138, 1, 1, 0, 60000),
(8220007, 2290035, 1, 1, 0, 10000),
(8220007, 2290091, 1, 1, 0, 10000),
(8220007, 2290108, 1, 1, 0, 10000),
(8220009, 2290031, 1, 1, 0, 10000),
(8220009, 2290129, 1, 1, 0, 30000),
(8220015, 2280004, 1, 1, 0, 10000),
(8220015, 2280005, 1, 1, 0, 10000),
(8220015, 2280006, 1, 1, 0, 10000),
(8500002, 2280007, 1, 1, 0, 15000),
(8500002, 2280008, 1, 1, 0, 15000),
(8500002, 2280009, 1, 1, 0, 15000),
(8500002, 2280010, 1, 1, 0, 15000),
(8500002, 2290006, 1, 1, 0, 15000),
(8500002, 2290010, 1, 1, 0, 15000),
(8500002, 2290011, 1, 1, 0, 15000),
(8500002, 2290013, 1, 1, 0, 15000),
(8500002, 2290028, 1, 1, 0, 15000),
(8500002, 2290037, 1, 1, 0, 15000),
(8500002, 2290043, 1, 1, 0, 15000),
(8500002, 2290051, 1, 1, 0, 15000),
(8500002, 2290056, 1, 1, 0, 15000),
(8500002, 2290061, 1, 1, 0, 15000),
(8500002, 2290066, 1, 1, 0, 15000),
(8500002, 2290071, 1, 1, 0, 15000),
(8500002, 2290078, 1, 1, 0, 15000),
(8500002, 2290089, 1, 1, 0, 15000),
(8500002, 2290091, 1, 1, 0, 15000),
(8500002, 2290104, 1, 1, 0, 15000),
(8500002, 2290107, 1, 1, 0, 15000),
(8500002, 2290121, 1, 1, 0, 15000),
(8500002, 2290123, 1, 1, 0, 15000),
(8500002, 2290126, 1, 1, 0, 45000),
(8500002, 2290129, 1, 1, 0, 45000),
(8510000, 2280007, 1, 1, 0, 10000),
(8510000, 2280008, 1, 1, 0, 10000),
(8510000, 2280009, 1, 1, 0, 10000),
(8510000, 2280010, 1, 1, 0, 10000),
(8510000, 2290000, 1, 1, 0, 10000),
(8510000, 2290001, 1, 1, 0, 10000),
(8510000, 2290004, 1, 1, 0, 10000),
(8510000, 2290005, 1, 1, 0, 10000),
(8510000, 2290024, 1, 1, 0, 10000),
(8510000, 2290025, 1, 1, 0, 10000),
(8510000, 2290026, 1, 1, 0, 10000),
(8510000, 2290027, 1, 1, 0, 10000),
(8510000, 2290052, 1, 1, 0, 10000),
(8510000, 2290053, 1, 1, 0, 10000),
(8510000, 2290054, 1, 1, 0, 10000),
(8510000, 2290055, 1, 1, 0, 10000),
(8510000, 2290076, 1, 1, 0, 10000),
(8510000, 2290077, 1, 1, 0, 10000),
(8510000, 2290082, 1, 1, 0, 10000),
(8510000, 2290083, 1, 1, 0, 10000),
(8510000, 2290097, 1, 1, 0, 10000),
(8510000, 2290099, 1, 1, 0, 10000),
(8510000, 2290106, 1, 1, 0, 10000),
(8510000, 2290108, 1, 1, 0, 10000),
(8510000, 2290112, 1, 1, 0, 10000),
(8510000, 2290114, 1, 1, 0, 10000),
(8510000, 2290122, 1, 1, 0, 10000),
(8510000, 2290124, 1, 1, 0, 10000),
(8510000, 2290132, 1, 1, 0, 30000),
(8520000, 2280007, 1, 1, 0, 10000),
(8520000, 2280008, 1, 1, 0, 10000),
(8520000, 2280009, 1, 1, 0, 10000),
(8520000, 2280010, 1, 1, 0, 10000),
(8520000, 2290000, 1, 1, 0, 10000),
(8520000, 2290001, 1, 1, 0, 10000),
(8520000, 2290004, 1, 1, 0, 10000),
(8520000, 2290005, 1, 1, 0, 10000),
(8520000, 2290024, 1, 1, 0, 10000),
(8520000, 2290025, 1, 1, 0, 10000),
(8520000, 2290026, 1, 1, 0, 10000),
(8520000, 2290027, 1, 1, 0, 10000),
(8520000, 2290052, 1, 1, 0, 10000),
(8520000, 2290053, 1, 1, 0, 10000),
(8520000, 2290054, 1, 1, 0, 10000),
(8520000, 2290055, 1, 1, 0, 10000),
(8520000, 2290076, 1, 1, 0, 10000),
(8520000, 2290077, 1, 1, 0, 10000),
(8520000, 2290082, 1, 1, 0, 10000),
(8520000, 2290083, 1, 1, 0, 10000),
(8520000, 2290097, 1, 1, 0, 10000),
(8520000, 2290099, 1, 1, 0, 10000),
(8520000, 2290106, 1, 1, 0, 10000),
(8520000, 2290108, 1, 1, 0, 10000),
(8520000, 2290112, 1, 1, 0, 10000),
(8520000, 2290114, 1, 1, 0, 10000),
(8520000, 2290122, 1, 1, 0, 10000),
(8520000, 2290124, 1, 1, 0, 10000),
(8520000, 2290132, 1, 1, 0, 30000),
(8800002, 2280007, 1, 1, 0, 20000),
(8800002, 2280008, 1, 1, 0, 20000),
(8800002, 2280009, 1, 1, 0, 20000),
(8800002, 2280010, 1, 1, 0, 20000),
(8800002, 2280013, 1, 1, 0, 60000),
(8800002, 2280014, 1, 1, 0, 60000),
(8800002, 2280015, 1, 1, 0, 60000),
(8800002, 2280016, 1, 1, 0, 60000),
(8800002, 2290006, 1, 1, 0, 20000),
(8800002, 2290007, 1, 1, 0, 20000),
(8800002, 2290016, 1, 1, 0, 20000),
(8800002, 2290020, 1, 1, 0, 20000),
(8800002, 2290022, 1, 1, 0, 20000),
(8800002, 2290024, 1, 1, 0, 20000),
(8800002, 2290028, 1, 1, 0, 20000),
(8800002, 2290029, 1, 1, 0, 20000),
(8800002, 2290040, 1, 1, 0, 20000),
(8800002, 2290046, 1, 1, 0, 20000),
(8800002, 2290048, 1, 1, 0, 20000),
(8800002, 2290056, 1, 1, 0, 20000),
(8800002, 2290057, 1, 1, 0, 20000),
(8800002, 2290058, 1, 1, 0, 20000),
(8800002, 2290064, 1, 1, 0, 20000),
(8800002, 2290067, 1, 1, 0, 20000),
(8800002, 2290074, 1, 1, 0, 20000),
(8800002, 2290079, 1, 1, 0, 20000),
(8800002, 2290084, 1, 1, 0, 20000),
(8800002, 2290094, 1, 1, 0, 20000),
(8800002, 2290110, 1, 1, 0, 20000),
(8800002, 2290115, 1, 1, 0, 20000),
(8810018, 2290017, 1, 1, 0, 25000),
(8810018, 2290021, 1, 1, 0, 25000),
(8810018, 2290023, 1, 1, 0, 25000),
(8810018, 2290041, 1, 1, 0, 25000),
(8810018, 2290047, 1, 1, 0, 25000),
(8810018, 2290049, 1, 1, 0, 25000),
(8810018, 2290065, 1, 1, 0, 25000),
(8810018, 2290075, 1, 1, 0, 25000),
(8810018, 2290085, 1, 1, 0, 25000),
(8810018, 2290095, 1, 1, 0, 25000),
(8810018, 2290096, 1, 1, 0, 25000),
(8810018, 2290111, 1, 1, 0, 25000),
(8810018, 2290116, 1, 1, 0, 25000),
(8810018, 2290125, 1, 1, 0, 25000),
(8810018, 2290133, 1, 1, 0, 75000),
(8810018, 2290137, 1, 1, 0, 75000),
(8810018, 2290139, 1, 1, 0, 75000),
(8820000, 2290010, 1, 1, 0, 30000),
(8820000, 2290022, 1, 1, 0, 30000),
(8820000, 2290040, 1, 1, 0, 30000),
(8820000, 2290046, 1, 1, 0, 30000),
(8820000, 2290048, 1, 1, 0, 30000),
(8820000, 2290052, 1, 1, 0, 30000),
(8820000, 2290084, 1, 1, 0, 30000),
(8820000, 2290090, 1, 1, 0, 30000),
(8820000, 2290106, 1, 1, 0, 30000),
(8820000, 2290119, 1, 1, 0, 30000),
(8820001, 2290010, 1, 1, 0, 30000),
(8820001, 2290022, 1, 1, 0, 30000),
(8820001, 2290040, 1, 1, 0, 30000),
(8820001, 2290046, 1, 1, 0, 30000),
(8820001, 2290048, 1, 1, 0, 30000),
(8820001, 2290052, 1, 1, 0, 30000),
(8820001, 2290084, 1, 1, 0, 30000),
(8820001, 2290090, 1, 1, 0, 30000),
(8820001, 2290106, 1, 1, 0, 30000),
(8820001, 2290119, 1, 1, 0, 30000),
(9300028, 2280015, 1, 1, 0, 30000),
(9300028, 2290026, 1, 1, 0, 10000),
(9300028, 2290064, 1, 1, 0, 10000),
(9300028, 2290075, 1, 1, 0, 10000),
(9300028, 2290093, 1, 1, 0, 10000),
(9300028, 2290111, 1, 1, 0, 10000),
(9300094, 2280004, 1, 1, 0, 10000),
(9300094, 2280005, 1, 1, 0, 10000),
(9300094, 2280006, 1, 1, 0, 10000),
(9300095, 2280004, 1, 1, 0, 500),
(9300095, 2280005, 1, 1, 0, 500),
(9300095, 2280006, 1, 1, 0, 500),
(9303016, 2290006, 1, 1, 0, 500),
(9303016, 2290030, 1, 1, 0, 500),
(9303016, 2290032, 1, 1, 0, 500),
(9303016, 2290060, 1, 1, 0, 500),
(9303016, 2290076, 1, 1, 0, 500),
(9303016, 2290104, 1, 1, 0, 500),
(9303016, 2290117, 1, 1, 0, 500),
(9400014, 2290053, 1, 1, 0, 10000),
(9400014, 2290087, 1, 1, 0, 10000),
(9400014, 2290112, 1, 1, 0, 10000),
(9400014, 2290122, 1, 1, 0, 10000),
(9400120, 2290045, 1, 1, 0, 10000),
(9400121, 2280014, 1, 1, 0, 45000),
(9400121, 2290081, 1, 1, 0, 15000),
(9400121, 2290087, 1, 1, 0, 15000),
(9400121, 2290101, 1, 1, 0, 15000),
(9400121, 2290103, 1, 1, 0, 15000),
(9400122, 2290007, 1, 1, 0, 10000),
(9400122, 2290062, 1, 1, 0, 10000),
(9400122, 2290116, 1, 1, 0, 10000),
(9400300, 2290045, 1, 1, 0, 30000),
(9400300, 2290055, 1, 1, 0, 30000),
(9400300, 2290063, 1, 1, 0, 30000),
(9400300, 2290079, 1, 1, 0, 30000),
(9400300, 2290081, 1, 1, 0, 30000),
(9400300, 2290096, 1, 1, 0, 30000),
(9400514, 2290023, 1, 1, 0, 10000),
(9400514, 2290057, 1, 1, 0, 10000),
(9400514, 2290088, 1, 1, 0, 10000),
(9400514, 2290095, 1, 1, 0, 10000),
(9400514, 2290115, 1, 1, 0, 10000),
(9400514, 2290139, 1, 1, 0, 30000),
(9400549, 2290001, 1, 1, 0, 10000),
(9400549, 2290020, 1, 1, 0, 10000),
(9400549, 2290045, 1, 1, 0, 10000),
(9400549, 2290057, 1, 1, 0, 10000),
(9400549, 2290086, 1, 1, 0, 10000),
(9400575, 2290009, 1, 1, 0, 10000),
(9400575, 2290051, 1, 1, 0, 10000),
(9400575, 2290081, 1, 1, 0, 10000),
(9400575, 2290087, 1, 1, 0, 10000),
(9400575, 2290107, 1, 1, 0, 10000),
(9400575, 2290123, 1, 1, 0, 10000),
(9400580, 2290004, 1, 1, 0, 500),
(9400580, 2290024, 1, 1, 0, 500),
(9400580, 2290083, 1, 1, 0, 500),
(9400580, 2290087, 1, 1, 0, 500),
(9400580, 2290103, 1, 1, 0, 500),
(9400580, 2290121, 1, 1, 0, 500),
(9400582, 2290005, 1, 1, 0, 500),
(9400582, 2290010, 1, 1, 0, 500),
(9400582, 2290029, 1, 1, 0, 500),
(9400582, 2290047, 1, 1, 0, 500),
(9400582, 2290049, 1, 1, 0, 500),
(9400582, 2290074, 1, 1, 0, 500),
(9400582, 2290079, 1, 1, 0, 500),
(9400582, 2290081, 1, 1, 0, 500),
(9400582, 2290135, 1, 1, 0, 1500),
(9400590, 2290088, 1, 1, 0, 15000),
(9400590, 2290125, 1, 1, 0, 15000),
(9400590, 2290135, 1, 1, 0, 45000),
(9400591, 2290039, 1, 1, 0, 15000),
(9400591, 2290074, 1, 1, 0, 15000),
(9400591, 2290113, 1, 1, 0, 15000),
(9400592, 2290047, 1, 1, 0, 15000),
(9400592, 2290123, 1, 1, 0, 15000),
(9400592, 2290131, 1, 1, 0, 45000),
(9400593, 2290069, 1, 1, 0, 15000),
(9400593, 2290093, 1, 1, 0, 15000),
(9400593, 2290138, 1, 1, 0, 45000),
(9420513, 2290039, 1, 1, 0, 10000),
(9420513, 2290100, 1, 1, 0, 10000),
(9420513, 2290108, 1, 1, 0, 10000),
(9420513, 2290118, 1, 1, 0, 10000),
(9420513, 2290138, 1, 1, 0, 30000),
(9420514, 2290099, 1, 1, 0, 1287),
(9420517, 2290000, 1, 1, 0, 1287),
(9420517, 2290008, 1, 1, 0, 1287),
(9420517, 2290018, 1, 1, 0, 1287),
(9420517, 2290038, 1, 1, 0, 1287),
(9420517, 2290060, 1, 1, 0, 1287),
(9420517, 2290080, 1, 1, 0, 1287),
(9420517, 2290103, 1, 1, 0, 1287),
(9420518, 2290123, 1, 1, 0, 1287),
(9420519, 2290113, 1, 1, 0, 1287),
(9420522, 2290000, 1, 1, 0, 1287),
(9420522, 2290001, 1, 1, 0, 1287),
(9420522, 2290011, 1, 1, 0, 1287),
(9420522, 2290025, 1, 1, 0, 1287),
(9420522, 2290028, 1, 1, 0, 1287),
(9420522, 2290037, 1, 1, 0, 1287),
(9420522, 2290043, 1, 1, 0, 1287),
(9420522, 2290066, 1, 1, 0, 1287),
(9420522, 2290082, 1, 1, 0, 1287),
(9420522, 2290083, 1, 1, 0, 1287),
(9420522, 2290089, 1, 1, 0, 1287),
(9420522, 2290091, 1, 1, 0, 1287),
(9420522, 2290107, 1, 1, 0, 1287),
(9420540, 2280006, 1, 1, 0, 500),
(9420540, 2290119, 1, 1, 0, 500),
(9420540, 2290120, 1, 1, 0, 500),
(9420544, 2280007, 1, 1, 0, 20000),
(9420544, 2280008, 1, 1, 0, 20000),
(9420544, 2280009, 1, 1, 0, 20000),
(9420544, 2280010, 1, 1, 0, 20000),
(9420544, 2290002, 1, 1, 0, 20000),
(9420544, 2290015, 1, 1, 0, 20000),
(9420544, 2290022, 1, 1, 0, 20000),
(9420544, 2290027, 1, 1, 0, 20000),
(9420544, 2290034, 1, 1, 0, 20000),
(9420544, 2290052, 1, 1, 0, 20000),
(9420544, 2290054, 1, 1, 0, 20000),
(9420544, 2290089, 1, 1, 0, 20000),
(9420544, 2290094, 1, 1, 0, 20000),
(9420544, 2290098, 1, 1, 0, 20000),
(9420544, 2290105, 1, 1, 0, 20000),
(9420544, 2290110, 1, 1, 0, 20000),
(9420544, 2290119, 1, 1, 0, 20000),
(9420549, 2280007, 1, 1, 0, 20000),
(9420549, 2280008, 1, 1, 0, 20000),
(9420549, 2280009, 1, 1, 0, 20000),
(9420549, 2280010, 1, 1, 0, 20000),
(9420549, 2290002, 1, 1, 0, 20000),
(9420549, 2290015, 1, 1, 0, 20000),
(9420549, 2290022, 1, 1, 0, 20000),
(9420549, 2290027, 1, 1, 0, 20000),
(9420549, 2290034, 1, 1, 0, 20000),
(9420549, 2290052, 1, 1, 0, 20000),
(9420549, 2290054, 1, 1, 0, 20000),
(9420549, 2290089, 1, 1, 0, 20000),
(9420549, 2290094, 1, 1, 0, 20000),
(9420549, 2290098, 1, 1, 0, 20000),
(9420549, 2290105, 1, 1, 0, 20000),
(9420549, 2290110, 1, 1, 0, 20000),
(9420549, 2290119, 1, 1, 0, 20000),
(9500166, 2290044, 1, 1, 0, 500),
(9500173, 2290018, 1, 1, 0, 10000),
(9500173, 2290019, 1, 1, 0, 10000),
(9500173, 2290032, 1, 1, 0, 10000),
(9500173, 2290042, 1, 1, 0, 10000),
(9500173, 2290058, 1, 1, 0, 10000),
(9500173, 2290068, 1, 1, 0, 10000),
(9500173, 2290072, 1, 1, 0, 10000),
(9500173, 2290092, 1, 1, 0, 10000),
(9500173, 2290099, 1, 1, 0, 10000),
(9500173, 2290102, 1, 1, 0, 10000),
(9500173, 2290119, 1, 1, 0, 10000),
(9500173, 2290128, 1, 1, 0, 30000),
(9500174, 2290002, 1, 1, 0, 10000),
(9500174, 2290014, 1, 1, 0, 10000),
(9500174, 2290030, 1, 1, 0, 10000),
(9500174, 2290080, 1, 1, 0, 10000),
(9500174, 2290130, 1, 1, 0, 30000),
(9500180, 2290010, 1, 1, 0, 10000),
(9500180, 2290028, 1, 1, 0, 10000),
(9500180, 2290126, 1, 1, 0, 30000),
(9500181, 2290010, 1, 1, 0, 10000),
(9500181, 2290028, 1, 1, 0, 10000),
(9500181, 2290126, 1, 1, 0, 30000),
(9500331, 2290010, 1, 1, 0, 10000),
(9500331, 2290028, 1, 1, 0, 10000),
(9500331, 2290126, 1, 1, 0, 30000),
(9500332, 2290132, 1, 1, 0, 30000),
(9500333, 2290006, 1, 1, 0, 10000),
(9500333, 2290030, 1, 1, 0, 10000),
(9500333, 2290032, 1, 1, 0, 10000),
(9500333, 2290060, 1, 1, 0, 10000),
(9500333, 2290076, 1, 1, 0, 10000),
(9500333, 2290104, 1, 1, 0, 10000),
(9500333, 2290117, 1, 1, 0, 10000);