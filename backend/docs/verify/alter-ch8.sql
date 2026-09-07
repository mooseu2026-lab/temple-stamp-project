-- backend/docs/verify/alter-ch8.sql — 챕터 8(원고 등록·심사) 스키마 변경
--
--   mysql -uroot -p temple_stamp_project < backend/docs/verify/alter-ch8.sql
--
-- schema.sql 은 CREATE TABLE IF NOT EXISTS 라 이미 있는 표에 컬럼을 더하지 않는다.
-- 이미 만들어진 DB(로컬·운영)에는 이 파일을 손으로 한 번 돌려야 한다. 운영 목록은 정리.md §5-1.
-- USE 를 두지 않는다. 부르는 쪽이 스키마를 정한다:
--   mysql -u<user> -p <스키마> < 이 파일
-- 파일 안에 USE 가 있으면 운영 스키마에 올리려고 불러도 조용히 개발 DB 로 간다(최종 점검 F).

-- ⑫ 원고 표. 기존 mission·expansion_phrase 는 그대로 둔다(레거시 계층 원고) —
--    그쪽은 (구, 계층) 축이고 이 표는 (사찰, 구) 축이라 규칙이 서로 다르다.
--    자세한 판정은 docs/audit/ch8-manuscript.md §1.
--
--    ★ site_key 가 이 표의 핵심이다. MySQL 은 NULL 을 유니크에서 서로 다른 값으로 보기 때문에
--      site_id 에 그냥 유니크를 걸면 기본 원고(site_id NULL)가 몇 편이든 들어간다.
--      생성 컬럼으로 NULL 을 0 으로 접어 두면 그 구멍이 막힌다(챕터 8 함정 1).
CREATE TABLE IF NOT EXISTS manuscript (
    manuscript_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    site_id BIGINT NULL,                            -- NULL 이면 기본 원고(구별 공용)
    site_key BIGINT GENERATED ALWAYS AS (IFNULL(site_id, 0)) STORED,   -- 유니크용. NULL 을 0 으로 접는다
    verse_no TINYINT NOT NULL,                      -- 오관게 1~5
    kind VARCHAR(10) NOT NULL,                      -- MISSION(미션 안내) / EXT(확장문구)
    variant_no INT NOT NULL,                        -- 같은 키 안의 변형. 서버가 매기고 재사용하지 않는다
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',    -- DRAFT→SUBMITTED→APPROVED/REJECTED · APPROVED→RETIRED
    title VARCHAR(50) NOT NULL,
    body TEXT NOT NULL,                             -- MISSION 1~2000 · EXT 1~200 (길이는 서비스가 본다)
    author_id BIGINT NOT NULL,                      -- 쓴 사람. 탈퇴해도 유지(원고는 개인정보가 아니다)
    reviewer_id BIGINT NULL,                        -- 심사한 관리자. 작성자와 같을 수 없다(4-eyes)
    reviewed_at DATETIME NULL,
    reject_reason VARCHAR(200) NULL,
    retired_at DATETIME NULL,                       -- 퇴역 시각. 삭제하지 않는 이유는 옛 도장이 참조하기 때문
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_manuscript_variant UNIQUE (site_key, verse_no, kind, variant_no),
    INDEX idx_manuscript_pick (site_key, verse_no, kind, status),
    INDEX idx_manuscript_review (status, created_at),
    INDEX idx_manuscript_author (author_id, status),
    CONSTRAINT fk_manuscript_site_id FOREIGN KEY (site_id)
        REFERENCES site (site_id) ON DELETE RESTRICT,
    CONSTRAINT fk_manuscript_author_id FOREIGN KEY (author_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_manuscript_reviewer_id FOREIGN KEY (reviewer_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT chk_manuscript_verse CHECK (verse_no BETWEEN 1 AND 5),
    CONSTRAINT chk_manuscript_kind CHECK (kind IN ('MISSION', 'EXT')),
    CONSTRAINT chk_manuscript_status CHECK (status IN
        ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'RETIRED')),
    CONSTRAINT chk_manuscript_variant CHECK (variant_no >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ⑬ 도장이 그때 본 원고를 붙든다. 원고가 나중에 퇴역·수정돼도 그 도장의 글은 바뀌지 않는다
--    (챕터 9 전자책이 그 시점의 문구를 그대로 실어야 한다 — 챕터 8 함정 3·4).
ALTER TABLE stamp
    ADD COLUMN manuscript_id BIGINT NULL AFTER mission_id,
    ADD COLUMN ext_manuscript_id BIGINT NULL AFTER expansion_phrase_id;
ALTER TABLE stamp
    ADD CONSTRAINT fk_stamp_manuscript_id FOREIGN KEY (manuscript_id)
        REFERENCES manuscript (manuscript_id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_stamp_ext_manuscript_id FOREIGN KEY (ext_manuscript_id)
        REFERENCES manuscript (manuscript_id) ON DELETE RESTRICT;

-- ⑭ 편집자 역할. 원고를 쓰고 제출하되 심사는 못 한다.
--    역할 부여 API 는 만들지 않는다 — 운영 SQL 이다(정리.md §7).
ALTER TABLE users
    DROP CHECK chk_users_role;
ALTER TABLE users
    ADD CONSTRAINT chk_users_role CHECK (role IN ('USER', 'EDITOR', 'ADMIN'));

-- 확인
SELECT '⑫ manuscript' AS step;
SHOW TABLES LIKE 'manuscript';
SHOW COLUMNS FROM manuscript LIKE 'site_key';
SELECT '⑬ stamp 의 원고 고정 컬럼' AS step;
SHOW COLUMNS FROM stamp LIKE '%manuscript%';
SELECT '⑭ EDITOR 역할' AS step;
SELECT CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS
 WHERE CONSTRAINT_SCHEMA = 'temple_stamp_project' AND CONSTRAINT_NAME = 'chk_users_role';
