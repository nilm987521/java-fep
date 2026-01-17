-- ============================================================
-- Setup: Grant privileges to fep_user in FREEPDB1
-- ============================================================

-- Switch to FREEPDB1
ALTER SESSION SET CONTAINER = FREEPDB1;

-- Grant necessary privileges to fep_user
GRANT CREATE TABLE TO fep_user;
GRANT CREATE SEQUENCE TO fep_user;
GRANT CREATE INDEX TO fep_user;
GRANT UNLIMITED TABLESPACE TO fep_user;
GRANT CREATE SESSION TO fep_user;

COMMIT;
EXIT;
