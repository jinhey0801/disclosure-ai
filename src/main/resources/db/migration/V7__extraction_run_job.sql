-- 추출 실행과 Spring Batch 잡 실행을 연결한다 (진행 건수·실패 조회용).
ALTER TABLE extraction_run
    ADD COLUMN job_execution_id BIGINT NULL COMMENT 'BATCH_JOB_EXECUTION.JOB_EXECUTION_ID' AFTER status;
