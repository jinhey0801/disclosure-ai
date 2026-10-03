-- =========================================================
-- 기업 / 공시 보고서
-- =========================================================
CREATE TABLE company (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    corp_code   VARCHAR(8)   NOT NULL COMMENT 'DART 고유번호(8자리)',
    stock_code  VARCHAR(6)   NULL COMMENT '종목코드(6자리)',
    corp_name   VARCHAR(100) NOT NULL,
    market      VARCHAR(20)  NOT NULL COMMENT 'KOSDAQ 등',
    created_at  DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_company_corp_code (corp_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE disclosure_report (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    rcept_no     VARCHAR(14)  NOT NULL COMMENT 'DART 접수번호. 정정공시는 별도 접수번호',
    report_type  VARCHAR(10)  NOT NULL COMMENT 'ANNUAL(11011) / HALF(11012) / Q1(11013) / Q3(11014)',
    fiscal_year  INT          NOT NULL COMMENT '사업연도',
    period_end   DATE         NOT NULL COMMENT '보고 기간 종료일 (재무상태표 기준일)',
    title        VARCHAR(200) NOT NULL,
    filed_on     DATE         NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_report_rcept_no (rcept_no),
    KEY idx_report_company_period (company_id, fiscal_year, report_type),
    CONSTRAINT fk_report_company FOREIGN KEY (company_id) REFERENCES company (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =========================================================
-- 항목 메타데이터
-- =========================================================
CREATE TABLE metric_definition (
    code             VARCHAR(40)  PRIMARY KEY COMMENT '예: TOTAL_ASSETS',
    standard_name    VARCHAR(50)  NOT NULL COMMENT '표준명: 자산총계',
    statement_type   VARCHAR(10)  NOT NULL COMMENT 'BS(재무상태표) / IS(손익계산서) / GENERAL',
    period_basis     VARCHAR(10)  NOT NULL COMMENT 'INSTANT(시점) / DURATION(기간). DURATION 항목만 3개월/누적 구분이 생김',
    canonical_unit   VARCHAR(10)  NOT NULL COMMENT '정규화 단위: KRW(원) / PERSON(명)',
    dart_account_id  VARCHAR(100) NULL COMMENT 'DART 재무제표 API account_id (정답 매칭용)',
    enabled          BOOLEAN      NOT NULL COMMENT '현재 추출 대상 여부',
    evaluated        BOOLEAN      NOT NULL COMMENT '정확도 평가 대상 여부 (보조 항목은 false)',
    description      VARCHAR(500) NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE metric_synonym (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    metric_code  VARCHAR(40)  NOT NULL,
    synonym      VARCHAR(100) NOT NULL COMMENT '공시 원문에 나타나는 계정명',
    UNIQUE KEY uk_synonym (metric_code, synonym),
    CONSTRAINT fk_synonym_metric FOREIGN KEY (metric_code) REFERENCES metric_definition (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =========================================================
-- 추출 (LLM)
-- 비교 키: (report_id, metric_code, fs_div, period_scope)
--   report_id 가 기업 + 보고서 종류 + 기간을 함께 결정한다.
-- =========================================================
CREATE TABLE extraction_run (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    model           VARCHAR(100) NOT NULL,
    prompt_version  VARCHAR(50)  NOT NULL,
    status          VARCHAR(20)  NOT NULL COMMENT 'RUNNING / COMPLETED / FAILED',
    started_at      DATETIME(6)  NOT NULL,
    finished_at     DATETIME(6)  NULL,
    note            VARCHAR(500) NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE extraction_result (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id            BIGINT        NOT NULL,
    report_id         BIGINT        NOT NULL,
    metric_code       VARCHAR(40)   NOT NULL,
    fs_div            VARCHAR(4)    NOT NULL COMMENT 'CFS(연결) / OFS(별도) / NONE(임직원 수 등)',
    period_scope      VARCHAR(12)   NOT NULL COMMENT 'INSTANT / QUARTER(3개월) / CUMULATIVE(누적)',
    raw_account_name  VARCHAR(200)  NULL COMMENT '원문 계정명 (예: 영업수익)',
    raw_value         VARCHAR(100)  NULL COMMENT '원문 숫자 문자열 (예: (1,234))',
    raw_unit          VARCHAR(20)   NULL COMMENT '원문 단위: WON / THOUSAND_WON / MILLION_WON / PERSON',
    normalized_value  DECIMAL(20, 0) NULL COMMENT '정규화 값 (원/명). NULL = 찾지 못함',
    evidence_text     TEXT          NULL COMMENT '원문 근거 텍스트',
    created_at        DATETIME(6)   NOT NULL,
    UNIQUE KEY uk_extraction (run_id, report_id, metric_code, fs_div, period_scope),
    KEY idx_extraction_report (report_id),
    CONSTRAINT fk_extraction_run    FOREIGN KEY (run_id)      REFERENCES extraction_run (id),
    CONSTRAINT fk_extraction_report FOREIGN KEY (report_id)   REFERENCES disclosure_report (id),
    CONSTRAINT fk_extraction_metric FOREIGN KEY (metric_code) REFERENCES metric_definition (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =========================================================
-- 정답 (DART Open API) - 추출 결과와 분리, 같은 비교 키
-- =========================================================
CREATE TABLE ground_truth (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id            BIGINT         NOT NULL,
    metric_code          VARCHAR(40)    NOT NULL,
    fs_div               VARCHAR(4)     NOT NULL,
    period_scope         VARCHAR(12)    NOT NULL,
    value                DECIMAL(20, 0) NOT NULL COMMENT '원/명 단위',
    source               VARCHAR(20)    NOT NULL COMMENT 'DART_FS_API / DART_EMP_API',
    source_account_id    VARCHAR(100)   NULL,
    source_account_name  VARCHAR(200)   NULL,
    fetched_at           DATETIME(6)    NOT NULL,
    UNIQUE KEY uk_ground_truth (report_id, metric_code, fs_div, period_scope),
    CONSTRAINT fk_truth_report FOREIGN KEY (report_id)   REFERENCES disclosure_report (id),
    CONSTRAINT fk_truth_metric FOREIGN KEY (metric_code) REFERENCES metric_definition (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =========================================================
-- 검증 규칙 / 결과
-- =========================================================
CREATE TABLE validation_rule (
    code         VARCHAR(40)  PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    rule_type    VARCHAR(30)  NOT NULL COMMENT 'BALANCE_IDENTITY / CAPITAL_IMPAIRMENT / PERIOD_CHANGE',
    params       JSON         NOT NULL COMMENT '규칙별 파라미터 (대상 항목, 허용 오차 등)',
    severity     VARCHAR(10)  NOT NULL COMMENT 'ERROR / WARNING',
    enabled      BOOLEAN      NOT NULL,
    description  VARCHAR(500) NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE validation_result (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id      BIGINT       NOT NULL,
    report_id   BIGINT       NOT NULL,
    rule_code   VARCHAR(40)  NOT NULL,
    fs_div      VARCHAR(4)   NOT NULL,
    outcome     VARCHAR(10)  NOT NULL COMMENT 'PASS / FAIL / SKIPPED(입력 항목 누락)',
    message     VARCHAR(500) NULL,
    details     JSON         NULL COMMENT '계산에 쓴 값, 차이 등',
    created_at  DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_validation (run_id, report_id, rule_code, fs_div),
    CONSTRAINT fk_validation_run    FOREIGN KEY (run_id)    REFERENCES extraction_run (id),
    CONSTRAINT fk_validation_report FOREIGN KEY (report_id) REFERENCES disclosure_report (id),
    CONSTRAINT fk_validation_rule   FOREIGN KEY (rule_code) REFERENCES validation_rule (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
