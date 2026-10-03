-- 단위를 통째로 잘못 읽으면(천원·백만원 표기를 원으로) 모든 값이 같은 배율로 틀려서
-- 자산=부채+자본도, 전년 동기 대비 변동도 정상으로 보인다. 그 경우를 잡는 절대 규모 점검.
-- 평가셋 18곳 중 가장 작은 회사도 자산총계가 200억 원 이상이고, 상장사가 10억 원 미만인 경우는 드물다.
INSERT INTO validation_rule (code, name, rule_type, params, severity, enabled, description) VALUES
    ('MIN_SCALE', '자산총계 규모 점검 (10억 원 이상)', 'MIN_SCALE',
     JSON_OBJECT('metric', 'TOTAL_ASSETS', 'min', 1000000000),
     'ERROR', TRUE, '자산총계가 10억 원 미만이면 단위를 잘못 읽었을 가능성이 높다');
