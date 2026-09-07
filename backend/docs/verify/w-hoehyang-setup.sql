-- backend/docs/verify/w-hoehyang-setup.sql — 회향 시나리오 준비 (챕터 7 §7-3)
--
-- 회향은 "ACTIVE 코스 전부 완주 + 완주 수 >= 하한 12" 일 때만 성립한다.
-- 시드 코스 12개는 전부 DRAFT 라 평소에는 ACTIVE 코스가 데모 1개뿐이고,
-- 그래서 회향이 성립하지 않는다 — 그것을 확인하는 것이 W15 다.
-- 여기서는 반대쪽, "조건이 맞으면 성립한다" 를 보려고 잠깐 12개를 올린다.
--
-- ★ teardown 에서 반드시 DRAFT 로 되돌린다. 시드 정본은 전부 DRAFT 다(함정 12).
-- USE 를 두지 않는다. 부르는 쪽이 mysql -D 로 스키마를 정한다(최종 점검 F §3-1).
-- ★ 코스를 번호 범위로 고르지 않는다. "37~48" 은 개발 DB 의 auto_increment 가 그렇게 흘렀을 뿐이고,
--   schema.sql 로 새로 만든 DB 에서는 2~13 이다 — 최종 점검 F 의 빈 DB 재구축에서 드러났다.
--   기준은 하나다: <b>데모 코스(1)가 아닌 것이 시드 코스</b>.

UPDATE course SET status = 'ACTIVE' WHERE course_id <> 1;

-- W 사용자에게 코스 37~48 완주를 주입한다. 도장까지 만들지 않는 이유는
-- 회향 판정이 "완주한 코스 수"(pilgrimage.status = COMPLETED)로 세기 때문이다.
-- 코스 1(데모)은 newman 이 도장 5개로 진짜 완주해 둔 상태다 → 합 13.
INSERT IGNORE INTO pilgrimage (user_id, course_id, status, started_at, completed_at)
SELECT u.user_id, c.course_id, 'COMPLETED', NOW(), NOW()
  FROM users u
       CROSS JOIN course c
 WHERE u.email = 'w-user@test.com'
   AND c.course_id <> 1;

SELECT (SELECT COUNT(*) FROM course WHERE status = 'ACTIVE')                          AS active_courses,
       (SELECT COUNT(*) FROM pilgrimage p JOIN users u ON u.user_id = p.user_id
         WHERE u.email = 'w-user@test.com' AND p.status = 'COMPLETED')                AS w_completed;
