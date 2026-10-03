-- 기업 + 사업연도 + 보고서 종류당 보고서는 하나만 둔다.
-- 정정공시가 나오면 새 행을 만들지 않고 기존 행의 접수번호를 최신으로 갱신한다.
ALTER TABLE disclosure_report
    ADD UNIQUE KEY uk_report_period (company_id, fiscal_year, report_type);

ALTER TABLE disclosure_report
    DROP INDEX idx_report_company_period;
