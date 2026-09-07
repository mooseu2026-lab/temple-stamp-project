-- backend/docs/verify/seed-v4-check.sql
-- v4 시드 검증. 읽기 전용이라 몇 번을 돌려도 안전하다.
--   mysql -uroot -p1234 --default-character-set=utf8mb4 < backend/docs/verify/seed-v4-check.sql
USE temple_stamp_project;

SELECT '① 행 수' AS section;
SELECT
  (SELECT COUNT(*) FROM site)                                          AS site_all,
  (SELECT COUNT(*) FROM site WHERE seed_key IS NOT NULL)               AS site_seeded,
  (SELECT COUNT(*) FROM course WHERE name LIKE '%공양의 길')            AS course_seeded,
  (SELECT COUNT(*) FROM course_site cs JOIN course c ON c.course_id = cs.course_id
    WHERE c.name LIKE '%공양의 길')                                     AS course_site_seeded,
  (SELECT COUNT(*) FROM slot_site)                                     AS slot_site_all,
  (SELECT COUNT(*) FROM site_element)                                  AS site_element_all,
  (SELECT COUNT(*) FROM site_badge WHERE badge_type = 'FLOWER')        AS flower_badges;

SELECT '② 권역별 사찰 수 · 좌표 확보' AS section;
SELECT r.code, r.name,
       COUNT(*)                                         AS sites,
       SUM(s.latitude <> 0)                             AS with_coord,
       SUM(s.latitude = 0)                              AS zero_coord
  FROM site s
  JOIN region r ON r.code = SUBSTRING_INDEX(s.seed_key, ':', 1)
 WHERE s.seed_key IS NOT NULL
 GROUP BY r.code, r.name
 ORDER BY r.sort_no;
-- ↑ seed_key 앞머리는 CSV region_code 다. 별칭 3개(BUSAN_GYEONGNAM·DAEGU_GYEONGBUK·JEONNAM_GWANGJU)는
--   DB code 와 달라 이 조인에서 빠진다. 아래 ②' 가 그 셋을 따로 센다.

SELECT '②′ CSV 코드 기준(별칭 포함)' AS section;
SELECT SUBSTRING_INDEX(seed_key, ':', 1) AS csv_region,
       COUNT(*) AS sites, SUM(latitude <> 0) AS with_coord, SUM(latitude = 0) AS zero_coord
  FROM site WHERE seed_key IS NOT NULL
 GROUP BY csv_region ORDER BY csv_region;

SELECT '③ 좌표 0 인 사찰' AS section;
SELECT site_id, name, seed_key FROM site
 WHERE seed_key IS NOT NULL AND latitude = 0 ORDER BY seed_key;

SELECT '④ 라인별 5구가 모두 후보를 갖는가 (12 × 5 = 60)' AS section;
SELECT c.course_id, c.name, COUNT(DISTINCT cs.position) AS slots,
       COUNT(DISTINCT CASE WHEN ss.slot_site_id IS NOT NULL THEN cs.position END) AS slots_with_candidate,
       COUNT(ss.slot_site_id) AS candidates
  FROM course c
  JOIN course_site cs ON cs.course_id = c.course_id
  LEFT JOIN slot_site ss ON ss.course_site_id = cs.course_site_id
 WHERE c.name LIKE '%공양의 길'
 GROUP BY c.course_id, c.name ORDER BY c.course_id;

SELECT '⑤ 동명이찰 — 같은 이름이 서로 다른 seed_key 를 갖는가' AS section;
SELECT s.name, COUNT(*) AS n, COUNT(DISTINCT s.seed_key) AS distinct_keys,
       GROUP_CONCAT(s.seed_key ORDER BY s.seed_key SEPARATOR ' | ') AS keys_
  FROM site s WHERE s.seed_key IS NOT NULL
 GROUP BY s.name HAVING COUNT(*) > 1 ORDER BY s.name;

SELECT '⑥ 후보 무결성 — 후보 사찰의 권역이 코스 권역과 같은가' AS section;
SELECT c.course_id, c.name AS course_name, s.name AS site_name,
       SUBSTRING_INDEX(s.seed_key, ':', 1) AS candidate_region, r.code AS course_region
  FROM slot_site ss
  JOIN course_site cs ON cs.course_site_id = ss.course_site_id
  JOIN course c ON c.course_id = cs.course_id
  JOIN region r ON r.region_id = c.region_id
  JOIN site s ON s.site_id = ss.site_id
 WHERE c.name LIKE '%공양의 길'
   AND r.code <> CASE SUBSTRING_INDEX(s.seed_key, ':', 1)
                   WHEN 'BUSAN_GYEONGNAM' THEN 'GYEONGNAM'
                   WHEN 'DAEGU_GYEONGBUK' THEN 'GYEONGBUK'
                   WHEN 'JEONNAM_GWANGJU' THEN 'JEONNAM'
                   ELSE SUBSTRING_INDEX(s.seed_key, ':', 1) END;
-- 0행이어야 한다.

SELECT '⑦ 후보 무결성 — MAIN sort 1 = 대표(course_site.site_id)' AS section;
SELECT c.course_id, c.name, cs.position, cs.site_id AS representative, ss.site_id AS main_sort1
  FROM course_site cs
  JOIN course c ON c.course_id = cs.course_id
  LEFT JOIN slot_site ss ON ss.course_site_id = cs.course_site_id AND ss.track = 'MAIN' AND ss.sort_no = 1
 WHERE c.name LIKE '%공양의 길' AND (ss.site_id IS NULL OR ss.site_id <> cs.site_id);
-- 0행이어야 한다.

SELECT '⑧ 과포화(is_congested=1) 후보' AS section;
SELECT s.name, s.seed_key, COUNT(*) AS congested_rows
  FROM slot_site ss JOIN site s ON s.site_id = ss.site_id
 WHERE ss.is_congested = 1 GROUP BY s.name, s.seed_key ORDER BY s.name;

SELECT '⑨ 과포화가 유일한 후보인 자리 — 목록에 남기고 congested 로 표시한다(숨기지 않음)' AS section;
SELECT c.name AS course_name, cs.position, s.name AS representative,
       COUNT(*) AS candidates
  FROM course_site cs
  JOIN course c ON c.course_id = cs.course_id
  JOIN site s ON s.site_id = cs.site_id
  JOIN slot_site ss ON ss.course_site_id = cs.course_site_id
 WHERE c.name LIKE '%공양의 길'
 GROUP BY c.course_id, c.name, cs.position, s.name
HAVING COUNT(*) = SUM(ss.is_congested)
 ORDER BY c.course_id, cs.position;
-- candidates 가 1 이상이면 된다. 0 이면 findByCourse 가 다시 걸러내고 있다는 뜻이다.

SELECT '⑨′ 후보 행이 아예 없는 자리 (0행이어야 한다)' AS section;
SELECT c.name AS course_name, cs.position, s.name AS representative
  FROM course_site cs
  JOIN course c ON c.course_id = cs.course_id
  JOIN site s ON s.site_id = cs.site_id
 WHERE c.name LIKE '%공양의 길'
   AND NOT EXISTS (SELECT 1 FROM slot_site ss WHERE ss.course_site_id = cs.course_site_id)
 ORDER BY c.course_id, cs.position;

SELECT '⑩ CSV Y 개수 ↔ site_element 행 수는 SeedConsistencyTest 가 검사한다' AS section;
SELECT e.sort_no, e.code, COUNT(se.site_element_id) AS rows_
  FROM shrine_element e
  LEFT JOIN site_element se ON se.element_id = e.element_id
  GROUP BY e.sort_no, e.code ORDER BY e.sort_no;

-- ── D05 (중간 점검 B) : 회향 하한이 지켜지는가 ────────────────────────────────
-- ACTIVE 코스가 하한(12)에 못 미치는 동안에는 코스를 다 끝내도 회향이 나가면 안 된다.
SELECT 'D05 회향 하한 — 아래 셋이 전부 0 이어야 한다' AS section;
SELECT
  (SELECT COUNT(*) FROM course WHERE status = 'ACTIVE')                    AS active_courses,
  (SELECT COUNT(*) FROM certificate WHERE cert_type = 'HOEHYANG')          AS hoehyang_cert,
  (SELECT COUNT(*) FROM ebook WHERE ebook_type = 'HOEHYANG')               AS hoehyang_ebook,
  (SELECT COUNT(*) FROM user_reward ur JOIN reward_policy rp ON rp.reward_policy_id = ur.reward_policy_id
    WHERE rp.trigger_type = 'ON_ALL_COMPLETED')                            AS hoehyang_reward;
