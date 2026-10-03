-- 같은 공시를 단위만 바꿔 넣는 "입력 변형" 실험용. 실제 피투자사 보고자료(엑셀·PDF)는 천원·백만원이 섞인다.
ALTER TABLE extraction_run
    ADD COLUMN input_variant VARCHAR(20) NOT NULL DEFAULT 'ORIGINAL'
        COMMENT 'ORIGINAL / THOUSAND_WON / MILLION_WON / MIXED / UNIT_PHRASE' AFTER prompt_version;
