DELETE FROM site_distance WHERE site_a_id IN (SELECT site_id FROM site WHERE name LIKE '검증사찰%');
DELETE FROM course_site WHERE course_id IN (SELECT course_id FROM course WHERE name='검증코스');
DELETE FROM course WHERE name='검증코스';
DELETE FROM site WHERE name LIKE '검증사찰%' OR name='힌트없음';   -- i18n·viewpoint·badge 는 CASCADE
DELETE FROM users WHERE email LIKE 'reg-%@test.com' OR email LIKE 'lock-%@test.com';   -- refresh_token CASCADE, user_agreement 는 RESTRICT → 먼저 삭제
