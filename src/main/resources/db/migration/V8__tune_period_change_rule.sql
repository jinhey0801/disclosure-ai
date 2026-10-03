-- PERIOD_CHANGE 재조정 (정답 데이터로 확인한 분포 기준)
--   기존: 7개 항목, 50% → 정답에서도 87건 중 78건이 걸림 (소형사는 이익·현금이 해마다 크게 흔들림)
--   실제 전년 동기 대비 변동 최댓값: 자산 112%, 자본 219%, 매출 272%, 부채 527%
--   이익은 직전 값이 0 근처이거나 흑자/적자가 바뀌어 수십~수백 배도 흔함 → 제외
-- 목적을 "추출 오류 탐지"로 좁힌다. 단위 오독(천 배·천분의 일)은 잡고, 실제 변동은 걸리지 않게 배수 10 기준.
-- 변동률(-100% 하한)로는 1/1000 축소를 못 잡으므로 배수(|당기|/|전기|)로 양방향을 같은 기준으로 본다.
UPDATE validation_rule
SET name        = '전년 동기 대비 이상 변동 (10배 이상)',
    params      = JSON_OBJECT('metrics', JSON_ARRAY('TOTAL_ASSETS', 'TOTAL_LIABILITIES', 'TOTAL_EQUITY', 'REVENUE'),
                              'thresholdFactor', 10,
                              'compareTo', 'SAME_PERIOD_PRIOR_YEAR'),
    description = '자산·부채·자본·매출이 전년 동기 대비 10배 이상(또는 1/10 이하) 변하면 경고. 단위 오독 등 추출 오류 탐지용'
WHERE code = 'PERIOD_CHANGE';
