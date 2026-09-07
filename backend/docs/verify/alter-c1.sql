-- C1 — 사찰 조사자료 반영. 공양 값 체계를 확정한다.
--   mysql -u<user> -p <스키마> < backend/docs/verify/alter-c1.sql
--
-- USE 를 두지 않는다. 부르는 쪽이 스키마를 정한다 — 파일 안에 USE 가 있으면
-- 운영 스키마에 올리려고 불러도 조용히 개발 DB 로 간다(최종 점검 F).
--
-- ㉓ site.meal_available — 자유 문자열이던 칸에 값 4종을 못 박는다.
--
--    schema.sql 은 이 칸을 "공양 가능 등급. 값 체계 미확정" 으로 두었고, 그동안 시더가
--    조사 원문을 그대로 넣어 왔다 — "사찰음식·템플스테이", "사찰음식 특화(2011)",
--    "사찰음식·템플스테이(천진암 정관스님)" 처럼 <b>12종 넘는 서술</b>이 쌓였다.
--    화면이 이 값으로 분기할 수도, 걸러낼 수도 없다. 그래서 네 값으로 접는다.
--
--      NONE         공양 없음
--      TEMPLE_MEAL  절에서 사찰음식을 낸다(템플스테이 포함)
--      PUBLIC_MEAL  대중공양·공양간
--      NEARBY       절 밖 인근 식당
--
--    ★ 서술 원문은 버리지 않는다 — 조사 원본(`backend/docs/content/site-enrich.csv` 의 note 칸,
--      `공양원문=…`)에 그대로 남는다. DB 는 분기할 수 있는 값만 갖는다.
--
-- ★★ 순서가 정해져 있다. CHECK 를 먼저 걸면 기존 72행이 전부 위반이라 ERROR 3819 로 막힌다.
--    UPDATE 로 값을 먼저 접고, 그다음에 제약을 건다(챕터 11 reward_policy 에서 같은 순서로 배웠다).

-- ① 사찰음식·템플스테이가 적힌 것 — 조사에서 가장 많은 갈래다.
UPDATE site
   SET meal_available = 'TEMPLE_MEAL'
 WHERE meal_available IS NOT NULL
   AND (meal_available LIKE '%사찰음식%' OR meal_available LIKE '%템플스테이%');

-- ② 데모 5곳(data.sql)이 쓰던 예시 값. 조사 결과가 아니라 화면을 채우려고 넣은 값이다.
--    AVAILABLE 은 공양을 낸다는 뜻으로, LIMITED 는 대중공양으로 옮긴다.
UPDATE site SET meal_available = 'TEMPLE_MEAL' WHERE meal_available = 'AVAILABLE';
UPDATE site SET meal_available = 'PUBLIC_MEAL' WHERE meal_available = 'LIMITED';

-- ③ 그래도 네 값이 아닌 것이 남으면 <b>비운다</b>.
--    "모른다" 를 NONE("없다")으로 적으면, 확인해 보지도 않고 "공양 없음" 이 화면에 나간다.
UPDATE site
   SET meal_available = NULL
 WHERE meal_available IS NOT NULL
   AND meal_available NOT IN ('NONE', 'TEMPLE_MEAL', 'PUBLIC_MEAL', 'NEARBY');

ALTER TABLE site
    ADD CONSTRAINT chk_site_meal CHECK
    (meal_available IS NULL OR meal_available IN ('NONE','TEMPLE_MEAL','PUBLIC_MEAL','NEARBY'));

-- 확인
SELECT '㉓ site.meal_available' AS step;
SELECT meal_available, COUNT(*) AS 사찰수 FROM site GROUP BY meal_available ORDER BY 2 DESC;
SELECT COUNT(*) AS 값체계_위반 FROM site
 WHERE meal_available IS NOT NULL
   AND meal_available NOT IN ('NONE','TEMPLE_MEAL','PUBLIC_MEAL','NEARBY');
