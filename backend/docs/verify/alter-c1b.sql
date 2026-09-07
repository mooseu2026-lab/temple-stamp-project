-- C1 보강 — 공양 값 체계를 바꾼다(2026-09-08 예성 확인).
--   mysql -u<user> -p <스키마> < backend/docs/verify/alter-c1b.sql
--
-- USE 를 두지 않는다. 부르는 쪽이 스키마를 정한다.
--
-- ㉔ site.meal_available — PUBLIC_MEAL 을 빼고 RESERVATION 을 넣는다.
--
--    C1(㉓)은 값을 넷으로 접었다: NONE / TEMPLE_MEAL / PUBLIC_MEAL / NEARBY.
--    그 뒤 확인된 사실이 둘이다.
--
--      ① 대부분의 절에 공양이 있다. "미확인" 은 없다는 뜻이 아니라 조사가 못 미친 것이었다.
--      ② 다만 <b>시간이 정해져 있고 사전에 연락을 해야 한다.</b>
--
--    ②가 값 체계를 바꾼다. 전에는 "있다/없다/어디서" 만 말했는데, 실제로 사용자가 알아야 하는 것은
--    "그냥 가도 되는가, 미리 연락해야 하는가" 다. 그것을 말하지 않으면 시간을 모르고 찾아간 사람이
--    헛걸음을 한다. 그래서 <b>RESERVATION</b> 을 기본값으로 두고, 대중공양을 뜻하던 PUBLIC_MEAL 은 뺀다 —
--    조사에서 그 값을 쓴 절이 한 곳도 없었고(데모 한 건뿐), 지금 필요한 구분이 아니다.
--
--      TEMPLE_MEAL  사찰음식 특화·체험(명장 등). 이름을 아는 6곳뿐
--      RESERVATION  공양 있음 · 사전 예약/시간 확인 필요   ← 기본값
--      NONE         공양 없음이 <b>확인된</b> 곳          ← 확인 전에는 쓰지 않는다
--      NEARBY       경내 공양 없고 인근 식당              ← 확인된 곳만
--
-- ★★ 순서 — CHECK 를 먼저 바꾸면 기존 값이 위반이라 ERROR 3819 다.
--    DROP CHECK → UPDATE → ADD CONSTRAINT 순으로만 지나간다(챕터 11 reward_policy 에서 배운 것).

ALTER TABLE site DROP CHECK chk_site_meal;

-- ① 앞 회차가 조사 서술로 유추해 넣은 TEMPLE_MEAL 을 되돌린다.
--    조사 문구에 "사찰음식" 이 있다는 것과 그 절이 <b>사찰음식으로 이름난 곳</b>이라는 것은 다른 말이다.
--    원문은 site-enrich.csv 의 note "공양원문=" 에 그대로 남아 있다.
UPDATE site SET meal_available = 'RESERVATION'
 WHERE meal_available IN ('TEMPLE_MEAL', 'PUBLIC_MEAL');

-- ①-b 데모 5곳(data.sql)의 예시 값도 같은 기준으로. 조사 결과가 아니라 화면을 채우려고 넣은 값이다.
--     여섯 곳에 드는 진관사만 TEMPLE_MEAL 이고 나머지는 위 ①이 이미 RESERVATION 으로 바꿨다.

-- ② 사찰음식으로 이름난 여섯 곳만 TEMPLE_MEAL.
--    이름으로 고르는 이유는 site_id 가 환경마다 다르기 때문이다(빈 DB 는 1~115, 개발 DB 는 990xxx).
UPDATE site SET meal_available = 'TEMPLE_MEAL'
 WHERE name IN ('진관사', '봉녕사', '수도사', '백양사', '금수암', '망경산사');

-- ③ 아직 비어 있는 곳도 RESERVATION 이다 — "모른다" 로 둘 이유가 없다(위 ①).
UPDATE site SET meal_available = 'RESERVATION'
 WHERE meal_available IS NULL OR meal_available = '';

ALTER TABLE site
    ADD CONSTRAINT chk_site_meal CHECK
    (meal_available IS NULL OR meal_available IN ('NONE','TEMPLE_MEAL','RESERVATION','NEARBY'));

-- 확인
SELECT '㉔ site.meal_available 값 4종' AS step;
SELECT meal_available, COUNT(*) AS 사찰수 FROM site GROUP BY meal_available ORDER BY 2 DESC;
SELECT COUNT(*) AS 비어있음 FROM site WHERE meal_available IS NULL OR meal_available = '';
SELECT COUNT(*) AS 값체계_위반 FROM site
 WHERE meal_available IS NOT NULL
   AND meal_available NOT IN ('NONE','TEMPLE_MEAL','RESERVATION','NEARBY');
