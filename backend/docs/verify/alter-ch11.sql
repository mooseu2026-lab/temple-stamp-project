-- 챕터 11 — 리뷰 반영 · 사은품 정책 · 이동시간 활성화.
--   mysql -u<user> -p <스키마> < backend/docs/verify/alter-ch11.sql
--
-- USE 를 두지 않는다. 부르는 쪽이 스키마를 정한다 —
-- 파일 안에 USE 가 있으면 운영 스키마에 올리려고 불러도 조용히 개발 DB 로 간다(최종 점검 F).

-- ⑳ 사용자 단위 보상에 사용자 단위 유니크를 준다 (리뷰 2-2)
--
--    지금까지 유니크는 (정책, 도장)·(정책, 순례) 둘뿐이었다. "이 사람의 누적" 에 주는 보상
--    (3코스 전자일기장·12코스 특별앨범)도 방금 끝낸 코스의 pilgrimage_id 로 저장돼서,
--    코스 하나를 반려했다가 재승인하면 <b>다른 코스 id 로 한 행이 더 생겼다</b> — 실물 기념품이 두 개 나간다.
--    ACTIVE 코스가 1개인 지금은 도달할 수 없지만, 12코스를 열기 전에 반드시 닫아야 한다.
--
--    ★ MySQL 유니크는 NULL 을 서로 다른 값으로 본다. 그래서 pilgrimage_id 를 NULL 로 두는 것만으로는
--      막히지 않고, NULL 을 접는 생성 컬럼이 필요하다(챕터 8 manuscript.site_key 와 같은 수법).

-- 마일스톤 — 전자일기장은 3·6·9·12 에 각각 한 번씩 나온다. 같은 정책으로 여러 번 받는 유일한 보상이라
-- 유니크에 이 칸이 없으면 6코스 보상이 3코스 행과 충돌해 조용히 사라진다.
ALTER TABLE user_reward
    ADD COLUMN milestone SMALLINT NULL AFTER pilgrimage_id;

ALTER TABLE user_reward
    ADD COLUMN user_key BIGINT GENERATED ALWAYS AS (
        CASE WHEN stamp_id IS NULL AND pilgrimage_id IS NULL THEN user_id END
    ) STORED AFTER milestone;

ALTER TABLE user_reward
    ADD COLUMN milestone_key SMALLINT GENERATED ALWAYS AS (IFNULL(milestone, 0)) STORED AFTER user_key;

ALTER TABLE user_reward
    ADD CONSTRAINT uk_user_reward_user UNIQUE (reward_policy_id, user_key, milestone_key);

-- ㉑ 한 사찰이 같은 트랙에 두 번 들어가지 못하게 (김해원 제안)
--
--    후보 배정은 (자리 × 사찰 × 트랙) 인데, 같은 사찰이 같은 트랙으로 두 자리에 들어가면
--    한 사람이 같은 절에서 도장을 두 번 받는 길이 열린다. 지금 데이터에는 없지만 막아 둔다.
--    ★ 넣기 전에 아래를 돌려 0행인지 확인한다. 행이 있으면 ALTER 가 1062 로 실패한다.
--      SELECT site_id, track, COUNT(*) FROM slot_site GROUP BY site_id, track HAVING COUNT(*) > 1;
ALTER TABLE slot_site
    ADD CONSTRAINT uk_slot_site_site UNIQUE (site_id, track);

-- 사은품 정책 확정 (2026-09-07 예성 결정)
--   도장마다·코스마다 주던 보상은 <b>끄고</b>(행은 남긴다 — 과거 적립분의 근거다),
--   3코스마다 전자일기장(디지털) · 12코스 완주 시 맞춤형 특별앨범(실물) 둘만 남긴다.
-- 전자일기장은 실물이 아니다. reward_type CHECK 가 STAMP/COUPON/PHYSICAL 셋뿐이라 먼저 넓힌다 —
-- 넓히지 않고 UPDATE 하면 ERROR 3819 로 막힌다(실제로 한 번 막혔다).
ALTER TABLE reward_policy DROP CHECK chk_reward_policy_type;
ALTER TABLE reward_policy ADD CONSTRAINT chk_reward_policy_type CHECK
    (reward_type IN ('STAMP','COUPON','PHYSICAL','DIGITAL'));

-- trigger_type 도 이름이 바뀐다 — 3코스에서 '한 번' 이 아니라 '3코스마다' 다.
-- ★ 순서가 서로를 막는다. 옛 CHECK 는 새 값을 거부하고 새 CHECK 는 옛 값을 거부한다 —
--   제약을 먼저 떼고, 값을 바꾸고, 그다음에 새 제약을 건다. 반대로 하면 ERROR 3819 다.
ALTER TABLE reward_policy DROP CHECK chk_reward_policy_trigger;
UPDATE reward_policy SET trigger_type = 'EVERY_THREE_COURSES' WHERE code = 'RW-INTERIM';
ALTER TABLE reward_policy ADD CONSTRAINT chk_reward_policy_trigger CHECK (trigger_type IN
    ('STAMP_COMPLETED','COURSE_COMPLETED','EVERY_THREE_COURSES','ALL_COMPLETED'));

UPDATE reward_policy SET is_active = 0 WHERE code IN ('RW-STAMP', 'RW-COURSE');
UPDATE reward_policy
   SET name = '전자일기장', reward_type = 'DIGITAL', is_active = 1
 WHERE code = 'RW-INTERIM';
UPDATE reward_policy
   SET name = '맞춤형 특별앨범', reward_type = 'PHYSICAL', is_active = 1
 WHERE code = 'RW-HOEHYANG';

-- ㉒ 전자일기장은 3·6·9·12 마다 한 권. 마일스톤이 키에 없으면 6코스 책이 3코스 책과 부딪혀 사라진다.
ALTER TABLE ebook
    ADD COLUMN milestone SMALLINT NULL AFTER snapshot_hash;
-- ★ 전자일기장에만 값이 있다. 다른 종류는 NULL 이라 유니크가 아예 걸리지 않는다 —
--   IFNULL 로 0을 채우면 개인 소장본이 사용자당 한 권으로 묶여 다음 책을 만들 수 없다.
ALTER TABLE ebook
    ADD COLUMN milestone_key SMALLINT GENERATED ALWAYS AS (CASE WHEN ebook_type = 'INTERIM' THEN IFNULL(milestone, 0) END) STORED AFTER milestone;
ALTER TABLE ebook
    ADD CONSTRAINT uk_ebook_user_milestone UNIQUE (user_id, ebook_type, milestone_key);

-- 확인
SELECT '⑳ user_reward' AS step;
SHOW COLUMNS FROM user_reward LIKE 'milestone';
SHOW COLUMNS FROM user_reward LIKE 'user\_key';
SHOW COLUMNS FROM user_reward LIKE 'milestone\_key';
SHOW INDEX FROM user_reward WHERE Key_name = 'uk_user_reward_user';
SELECT '㉑ slot_site' AS step;
SHOW INDEX FROM slot_site WHERE Key_name = 'uk_slot_site_site';
SELECT '사은품 정책' AS step;
SELECT code, name, reward_type, trigger_type, is_active FROM reward_policy ORDER BY reward_policy_id;
