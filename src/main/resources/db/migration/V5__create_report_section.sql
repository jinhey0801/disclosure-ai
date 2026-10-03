-- 공시 원문에서 잘라낸 재무제표 본문 (LLM 입력).
-- XBRL 태그(계정코드 등 사실상의 정답)는 제거하고, 사람이 보는 표 텍스트만 저장한다.
CREATE TABLE report_section (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id       BIGINT       NOT NULL,
    fs_div          VARCHAR(4)   NOT NULL COMMENT 'CFS(연결) / OFS(별도)',
    statement_type  VARCHAR(10)  NOT NULL COMMENT 'BS(재무상태표) / IS(손익계산서·포괄손익계산서)',
    seq             INT          NOT NULL COMMENT '같은 종류가 여러 개일 때 순서 (손익계산서 + 포괄손익계산서 분리 회사)',
    title           VARCHAR(200) NOT NULL COMMENT '원문 목차 제목 (예: 2-2. 연결 포괄손익계산서)',
    unit_label      VARCHAR(20)  NULL COMMENT '원문 단위 표기 (예: 원, 천원)',
    content         MEDIUMTEXT   NOT NULL COMMENT 'LLM 에 넣을 텍스트',
    char_count      INT          NOT NULL,
    source_class    VARCHAR(30)  NOT NULL COMMENT '원문 TABLE-GROUP ACLASS (예: {XBRL}IS_C1)',
    fetched_at      DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_report_section (report_id, fs_div, statement_type, seq),
    CONSTRAINT fk_section_report FOREIGN KEY (report_id) REFERENCES disclosure_report (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
