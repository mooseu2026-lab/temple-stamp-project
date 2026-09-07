-- backend/docs/verify/w-hoehyang-teardown.sql — 회향 시나리오 원복 (챕터 7 §7-3)
--
-- setup 이 올린 코스를 DRAFT 로 되돌리고 주입한 완주를 지운다.
-- 이것을 빼먹으면 다음 실행의 W15("ACTIVE 1개라 회향 없음")가 깨지고,
-- 시드 상태가 조용히 달라진다 — 2회 연속 실행이 그것을 잡는다.
-- USE 를 두지 않는다(최종 점검 F §3-1). 올릴 때와 <b>같은 조건</b>으로 되돌린다 —
-- 한쪽만 번호 범위를 쓰면 되돌리기가 새어 나간다.

DELETE FROM pilgrimage
 WHERE course_id <> 1
   AND user_id IN (SELECT user_id FROM users WHERE email = 'w-user@test.com');

UPDATE course SET status = 'DRAFT' WHERE course_id <> 1;

SELECT (SELECT COUNT(*) FROM course WHERE status = 'ACTIVE')                    AS active_courses,
       (SELECT COUNT(*) FROM course WHERE course_id <> 1 AND status <> 'DRAFT') AS not_restored;
