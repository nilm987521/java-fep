-- ============================================================
-- FEP_TRANSACTION - Add VERSION column and TARGET_ACCOUNT
-- ============================================================
-- 新增 VERSION 欄位用於 JPA 樂觀鎖
-- 新增 TARGET_ACCOUNT 欄位 (如果不存在)
-- ============================================================

-- Connect to FREEPDB1 as fep_user
CONNECT fep_user/fep_password@//localhost:1521/FREEPDB1

-- 新增 VERSION 欄位 (JPA @Version 樂觀鎖)
ALTER TABLE FEP_TRANSACTION ADD VERSION NUMBER(19) DEFAULT 0;
COMMENT ON COLUMN FEP_TRANSACTION.VERSION IS 'Optimistic locking version for JPA @Version';

-- 新增 TARGET_ACCOUNT 欄位 (如果不存在)
-- 注意：資料表原本有 DESTINATION_ACCOUNT，但 Entity 使用 TARGET_ACCOUNT
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'FEP_TRANSACTION' AND COLUMN_NAME = 'TARGET_ACCOUNT';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE FEP_TRANSACTION ADD TARGET_ACCOUNT VARCHAR2(32)';
    END IF;
END;
/

COMMENT ON COLUMN FEP_TRANSACTION.TARGET_ACCOUNT IS 'Target account number for transfer transactions';

-- 建立 Sequence (如果不存在)
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_FEP_TRANSACTION';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_FEP_TRANSACTION START WITH 1 INCREMENT BY 50 NOCACHE NOCYCLE';
    END IF;
END;
/

COMMIT;
