-- backend/docs/verify/ch2-checkpoints.sql
-- Postman 을 한 번 돌린 직후 Workbench 에서 위에서 아래로 실행. 기대값은 각 줄 오른쪽 주석
USE temple_stamp_project;

-- ① 비로그인 조회는 이력을 남기지 않는다                                   기대: 0
SELECT COUNT(*) AS anon_seen FROM phrase_seen WHERE user_id NOT IN (SELECT user_id FROM users);

-- ② 결정안 §0-4 적용 후: 미리보기는 기록하지 않는다                          기대: 0
--    (적용 전이라면 2 이상. 값이 2면 아직 옛 동작 — STEP 1 미반영)
SELECT COUNT(*) AS preview_seen
  FROM phrase_seen ps JOIN users u ON u.user_id = ps.user_id
 WHERE u.email = 'checkpoint@test.com';

-- ②' 확인포인트 계정의 계층·언어 (page 가 무엇을 기준으로 골랐는지 역추적)     기대: tier NULL(→AGE30), locale ko
SELECT user_id, tier, locale FROM users WHERE email = 'checkpoint@test.com';

-- ③ 검증 실패는 DB 에 닿지 않는다 — 확인할 것이 없음. 대신 region 이 정말 9건인지
SELECT COUNT(*) AS regions FROM region;                                       -- 9

-- ④ en 번역이 있는 사찰                                                     기대: site 1 이 en 행을 가짐
SELECT s.site_id, s.name AS ko, i.name AS en
  FROM site s LEFT JOIN site_i18n i ON i.site_id = s.site_id AND i.locale = 'en'
 WHERE s.site_id IN (SELECT site_id FROM course_site WHERE course_id = 1)
 ORDER BY (SELECT position FROM course_site cs WHERE cs.site_id = s.site_id);

-- ⑤ 가는 법 R1 — ON 조건이 맞으면 어떤 site_id 를 넣어도 정확히 7행           기대: 7 / 7
SELECT COUNT(*) AS steps_site1
  FROM shrine_element e
  LEFT JOIN site_element se ON se.element_id = e.element_id AND se.site_id = 1;
SELECT COUNT(*) AS steps_site999
  FROM shrine_element e
  LEFT JOIN site_element se ON se.element_id = e.element_id AND se.site_id = 999;  -- 없는 절이어도 7 (사전이 7행이므로)

-- ⑥ 정의역 밖 구절은 애초에 없다                                            기대: verse_no 1~5 만
SELECT verse_no FROM gwan_verse ORDER BY verse_no;

-- ⑦ 좌표 컬럼이 사용자 테이블 어디에도 없다 (원칙①)                          기대: 0행
SELECT TABLE_NAME, COLUMN_NAME
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'temple_stamp_project'
   AND COLUMN_NAME IN ('latitude','longitude','lat','lng')
   AND TABLE_NAME NOT IN ('site');                                          -- site 는 시설 고정 좌표라 예외

-- ⑧ 요청ID·timestamp 는 DB 에 없다 — 확인 대상 아님

-- ⑨ health 가 UP 이면 커넥션이 살아 있다 — 같은 커넥션에서 한 줄
SELECT NOW() AS db_alive, @@time_zone AS tz;                                   -- tz 가 SYSTEM 이면 서버 TZ(Asia/Seoul) 확인
