-- 챕터 10 명세 정합 — 운영 DB 에 손으로 실행한다.
--   mysql -u<user> -p <스키마> < backend/docs/verify/alter-ch10.sql
--
-- USE 를 두지 않는다. 부르는 쪽이 스키마를 정한다 —
-- 파일 안에 USE 가 있으면 운영 스키마에 올리려고 불러도 조용히 개발 DB 로 간다(최종 점검 F).

-- ⑲ 행동과제에 출처 축을 준다 (기획 명세 §5.4 의 50/30/20 가중, 감사 H H-2)
--
--    지금까지 과제는 (구절 × 계층) 하나로만 갈렸다. 그래서 어느 사찰에서 열어도 같은 과제가 나왔고,
--    "사찰마다 다른 과제" 라는 명세의 핵심이 빠져 있었다. 축을 하나 더해 세 풀로 나눈다.
--      SITE   이 사찰의 과제      가중 50
--      VERSE  이 구절의 과제      가중 30
--      COMMON 어디서나 쓰는 과제  가중 20
--
--    ★ 순서가 중요하다. verse_no 를 NULL 허용으로 바꾸기 전에 그 컬럼을 받치는 색인을 먼저 깐다.
--      uk_mission 이 (verse_no, …) 로 시작해 외래키를 받치고 있어서, 그것을 먼저 지우면 ERROR 1553 이 난다.
--      챕터 7 ⑥ 에서 인증서 유니크를 바꿀 때 똑같은 자리를 밟았다.

ALTER TABLE mission ADD INDEX idx_mission_verse (verse_no);

-- SITE·COMMON 과제는 구절이 없다. NULL 을 허용해야 "구절 무관" 을 거짓말 없이 적을 수 있다.
ALTER TABLE mission MODIFY COLUMN verse_no TINYINT NULL COMMENT '구절 번호. SITE·COMMON 과제는 NULL';

ALTER TABLE mission
    ADD COLUMN scope VARCHAR(10) NOT NULL DEFAULT 'VERSE' AFTER tier,
    ADD COLUMN site_id BIGINT NULL AFTER scope;

ALTER TABLE mission
    ADD CONSTRAINT chk_mission_scope CHECK (scope IN ('SITE','VERSE','COMMON'));

-- 축과 값이 어긋난 행을 아예 못 넣게 막는다. 이것이 없으면 scope='COMMON' 인데 site_id 가 박힌
-- 행이 들어와 어느 풀에도 안 잡히거나 두 풀에 잡힌다.
ALTER TABLE mission
    ADD CONSTRAINT chk_mission_scope_ref CHECK (
        (scope = 'SITE'   AND site_id IS NOT NULL AND verse_no IS NULL)
     OR (scope = 'VERSE'  AND site_id IS NULL     AND verse_no IS NOT NULL)
     OR (scope = 'COMMON' AND site_id IS NULL     AND verse_no IS NULL));

ALTER TABLE mission
    ADD CONSTRAINT fk_mission_site_id FOREIGN KEY (site_id)
        REFERENCES site (site_id) ON DELETE RESTRICT;

-- NULL 을 0 으로 접는 생성 컬럼. MySQL 유니크는 NULL 을 서로 다른 값으로 보기 때문에,
-- 접지 않으면 COMMON 과제가 같은 (계층·변형)으로 몇 편이든 들어간다(챕터 8 함정 1 과 같은 자리).
ALTER TABLE mission
    ADD COLUMN site_key BIGINT GENERATED ALWAYS AS (IFNULL(site_id, 0)) STORED AFTER site_id;
ALTER TABLE mission
    ADD COLUMN verse_key TINYINT GENERATED ALWAYS AS (IFNULL(verse_no, 0)) STORED AFTER site_key;

-- 옛 유니크는 (verse_no, tier, variant_no) 라 verse_no 가 NULL 인 행을 못 막는다. 축 셋을 다 넣는다.
ALTER TABLE mission DROP INDEX uk_mission;
ALTER TABLE mission
    ADD CONSTRAINT uk_mission UNIQUE (scope, site_key, verse_key, tier, variant_no);

-- 세 풀을 고를 때 쓰는 색인. 없으면 과제 하나 고를 때마다 표를 훑는다.
ALTER TABLE mission ADD INDEX idx_mission_pool (tier, scope, site_id, verse_no);

-- 확인
SELECT '⑲ mission scope' AS step;
SHOW COLUMNS FROM mission LIKE 'scope';
SHOW COLUMNS FROM mission LIKE 'site\_key';
SHOW COLUMNS FROM mission LIKE 'verse\_key';
-- Key_name uk_mission 의 첫 칸이 scope 여야 한다.
SHOW INDEX FROM mission WHERE Key_name = 'uk_mission';
SELECT scope, COUNT(*) AS cnt FROM mission GROUP BY scope;
