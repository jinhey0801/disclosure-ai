-- =========================================================
-- 항목 정의
--   SHARE_CAPITAL(자본금): 자본잠식 판정용 보조 항목 (평가 대상 아님)
--   EMPLOYEE_COUNT(임직원 수): 분기보고서에서 생략되는 경우가 있어 우선 비활성
-- =========================================================
INSERT INTO metric_definition
    (code, standard_name, statement_type, period_basis, canonical_unit, dart_account_id, enabled, evaluated, description)
VALUES
    ('TOTAL_ASSETS',         '자산총계',         'BS',      'INSTANT',  'KRW',    'ifrs-full_Assets',                  TRUE,  TRUE,  NULL),
    ('TOTAL_LIABILITIES',    '부채총계',         'BS',      'INSTANT',  'KRW',    'ifrs-full_Liabilities',             TRUE,  TRUE,  NULL),
    ('TOTAL_EQUITY',         '자본총계',         'BS',      'INSTANT',  'KRW',    'ifrs-full_Equity',                  TRUE,  TRUE,  NULL),
    ('CASH_AND_EQUIVALENTS', '현금및현금성자산', 'BS',      'INSTANT',  'KRW',    'ifrs-full_CashAndCashEquivalents',  TRUE,  TRUE,  NULL),
    ('REVENUE',              '매출액',           'IS',      'DURATION', 'KRW',    'ifrs-full_Revenue',                 TRUE,  TRUE,  NULL),
    ('OPERATING_INCOME',     '영업이익',         'IS',      'DURATION', 'KRW',    'dart_OperatingIncomeLoss',          TRUE,  TRUE,  '손실이면 음수'),
    ('NET_INCOME',           '당기순이익',       'IS',      'DURATION', 'KRW',    'ifrs-full_ProfitLoss',              TRUE,  TRUE,  '손실이면 음수. 분기/반기보고서에서는 분기순이익/반기순이익으로 표기'),
    ('SHARE_CAPITAL',        '자본금',           'BS',      'INSTANT',  'KRW',    'ifrs-full_IssuedCapital',           TRUE,  FALSE, '자본잠식 판정용 보조 항목'),
    ('EMPLOYEE_COUNT',       '임직원 수',        'GENERAL', 'INSTANT',  'PERSON', NULL,                                FALSE, TRUE,  '정답 출처: DART 직원 현황 API');

INSERT INTO metric_synonym (metric_code, synonym) VALUES
    ('TOTAL_ASSETS', '자산총계'),
    ('TOTAL_ASSETS', '자산 총계'),
    ('TOTAL_ASSETS', '총자산'),
    ('TOTAL_LIABILITIES', '부채총계'),
    ('TOTAL_LIABILITIES', '부채 총계'),
    ('TOTAL_LIABILITIES', '총부채'),
    ('TOTAL_EQUITY', '자본총계'),
    ('TOTAL_EQUITY', '자본 총계'),
    ('TOTAL_EQUITY', '총자본'),
    ('CASH_AND_EQUIVALENTS', '현금및현금성자산'),
    ('CASH_AND_EQUIVALENTS', '현금 및 현금성자산'),
    ('CASH_AND_EQUIVALENTS', '현금및현금등가물'),
    ('REVENUE', '매출액'),
    ('REVENUE', '매출'),
    ('REVENUE', '영업수익'),
    ('REVENUE', '수익(매출액)'),
    ('OPERATING_INCOME', '영업이익'),
    ('OPERATING_INCOME', '영업이익(손실)'),
    ('OPERATING_INCOME', '영업손실'),
    ('NET_INCOME', '당기순이익'),
    ('NET_INCOME', '당기순이익(손실)'),
    ('NET_INCOME', '당기순손실'),
    ('NET_INCOME', '분기순이익'),
    ('NET_INCOME', '분기순이익(손실)'),
    ('NET_INCOME', '반기순이익'),
    ('NET_INCOME', '반기순이익(손실)'),
    ('SHARE_CAPITAL', '자본금'),
    ('EMPLOYEE_COUNT', '임직원 수'),
    ('EMPLOYEE_COUNT', '직원 수'),
    ('EMPLOYEE_COUNT', '종업원 수');

-- =========================================================
-- 검증 규칙
-- =========================================================
INSERT INTO validation_rule (code, name, rule_type, params, severity, enabled, description) VALUES
    ('BALANCE_IDENTITY', '자산총계 = 부채총계 + 자본총계', 'BALANCE_IDENTITY',
     JSON_OBJECT('total', 'TOTAL_ASSETS',
                 'parts', JSON_ARRAY('TOTAL_LIABILITIES', 'TOTAL_EQUITY'),
                 'toleranceRatio', 0.001),
     'ERROR', TRUE, '단위 반올림 오차를 고려해 0.1% 이내 차이는 통과'),
    ('CAPITAL_IMPAIRMENT', '자본잠식 여부', 'CAPITAL_IMPAIRMENT',
     JSON_OBJECT('equity', 'TOTAL_EQUITY', 'capital', 'SHARE_CAPITAL'),
     'WARNING', TRUE, '자본총계 < 자본금: 부분잠식, 자본총계 < 0: 완전잠식'),
    ('PERIOD_CHANGE', '전년 동기 대비 급변', 'PERIOD_CHANGE',
     JSON_OBJECT('metrics', JSON_ARRAY('TOTAL_ASSETS', 'TOTAL_LIABILITIES', 'TOTAL_EQUITY',
                                       'CASH_AND_EQUIVALENTS', 'REVENUE', 'OPERATING_INCOME', 'NET_INCOME'),
                 'thresholdRatio', 0.5,
                 'compareTo', 'SAME_PERIOD_PRIOR_YEAR'),
     'WARNING', TRUE, '전년 동기 대비 50% 이상 변동 시 경고 (추출 오류 또는 실제 이벤트)');
