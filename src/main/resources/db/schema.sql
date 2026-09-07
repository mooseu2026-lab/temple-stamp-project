-- =====================================================================
--  temple_stamp_project schema
--  MySQL 8.0 / utf8mb4_unicode_ci / InnoDB / 31 tables
--
--  spring.sql.init 이 매 기동마다 통째로 실행하므로 전부 IF NOT EXISTS 로 둔다.
--  생성 순서는 외래키 의존을 따른다: 계정 → 콘텐츠 → 순례·인증 → 기록 → 산출물.
-- =====================================================================

-- =====================================================================
--  ① 계정 (3)
-- =====================================================================

-- 1. users — 회원
CREATE TABLE IF NOT EXISTS users (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY,      -- 회원 고유 번호
    email VARCHAR(100) NOT NULL,                    -- 로그인 아이디. 저장 전 소문자로 정규화
    password VARCHAR(255) NULL,                     -- BCrypt 해시. 원문은 어디에도 저장 안 함. 탈퇴하면 비운다
    nickname VARCHAR(20) NOT NULL,                  -- 화면 표시명. 인증서에도 이 이름이 실림
    role VARCHAR(20) NOT NULL DEFAULT 'USER',       -- USER(일반) / ADMIN(관리자)
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE / DELETED. 탈퇴해도 행은 남는다(챕터 1 보강)
    tier VARCHAR(10) NULL,                          -- 대상 구분 7종. NULL이면 AGE30 취급. JWT에 안 넣음
    locale VARCHAR(10) NOT NULL DEFAULT 'ko',       -- 화면 언어 ko/en/ja/zh
    notification_enabled TINYINT(1) NOT NULL DEFAULT 1,  -- 알림 수신 여부
    login_fail_count INT NOT NULL DEFAULT 0,        -- 연속 실패 횟수. 성공하면 0. col=col+1로 증가
    locked_until DATETIME NULL,                     -- 이 시각까지 로그인 금지 (5회 실패 시 15분)
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,                       -- 탈퇴 시각. 행을 지우지 않는 이유는 아래 주석
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('USER','EDITOR','ADMIN')),   -- EDITOR 는 원고를 쓰되 심사는 못 한다(챕터 8)
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE','DELETED')),
    CONSTRAINT chk_users_tier CHECK (tier IS NULL OR tier IN
        ('AGE20','AGE30','AGE40','AGE50','AGE60','RIDER','FOREIGN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 1-b. storage_orphan — 지워야 할 저장소 키 큐 (챕터 1 보강 탈퇴가 적재, 챕터 9 청소기가 소비)
--     파일 삭제를 탈퇴 트랜잭션 안에서 하지 않는 이유: 저장소 호출이 실패하면 탈퇴 전체가 롤백된다.
--     지울 키를 먼저 적어 두고 실제 삭제는 나중에 따로 한다.
CREATE TABLE IF NOT EXISTS storage_orphan (
    storage_orphan_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_key VARCHAR(500) NOT NULL,                 -- 저장소 키
    reason VARCHAR(30) NOT NULL,                    -- USER_WITHDRAWN / PHOTO_REPLACED / EBOOK_TRIMMED
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING→DELETED / FAILED(챕터 9 §5)
    retry_count INT NOT NULL DEFAULT 0,             -- 5회를 넘기면 FAILED. 못 지우는 키가 영원히 돌지 않게
    needs_review TINYINT(1) NOT NULL DEFAULT 0,     -- FAILED 로 접힌 키. 사람이 본다
    last_error VARCHAR(200) NULL,
    enqueued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,                       -- 청소기가 실제로 지운 시각. NULL 이면 아직 남아 있다
    INDEX idx_storage_orphan_pending (status, enqueued_at),
    CONSTRAINT chk_storage_orphan_status CHECK (status IN ('PENDING','DELETED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. refresh_token — 재발급 토큰
CREATE TABLE IF NOT EXISTS refresh_token (
    refresh_token_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,                        -- 소유 회원
    token_hash CHAR(64) NOT NULL,                   -- SHA-256 해시. 원문 저장 안 함
    expires_at DATETIME NOT NULL,                   -- 만료 시각 (14일)
    revoked_at DATETIME NULL,                       -- 폐기 시각. 값 있으면 사용 불가 (회전·로그아웃)
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    INDEX idx_refresh_token_user (user_id),
    -- 청소기가 5분마다 "만료+유예를 넘긴 토큰" 을 지운다. 이 색인이 없으면 그때마다 표 전체를
    -- 훑고 정렬한다(EXPLAIN type=ALL + Using filesort). 로그인 한 번에 한 행이 쌓이고
    -- 수명이 7일이라 운영에서는 수십만 행이 된다 — 감사 G STEP 6 이 실측으로 찾았다.
    INDEX idx_refresh_token_expires (expires_at),
    CONSTRAINT fk_refresh_token_user_id FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. user_agreement — 약관 동의 이력
CREATE TABLE IF NOT EXISTS user_agreement (
    user_agreement_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,                        -- 동의한 회원
    agreement_type VARCHAR(30) NOT NULL,            -- LOCATION_SERVICE / EBOOK_PUBLIC
    agreement_version VARCHAR(20) NOT NULL,         -- 약관 버전 (예: v1)
    agreed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    withdrawn_at DATETIME NULL,                     -- 철회 시각. 철회해도 행을 삭제하지 않는다
    CONSTRAINT uk_user_agreement UNIQUE (user_id, agreement_type, agreement_version),
    CONSTRAINT fk_user_agreement_user_id FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT chk_user_agreement_type CHECK
        (agreement_type IN ('LOCATION_SERVICE','EBOOK_PUBLIC'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================
--  ② 콘텐츠 (14)
-- =====================================================================

-- 4. region — 9권역
CREATE TABLE IF NOT EXISTS region (
    region_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(30) NOT NULL,                      -- 영문 코드 (예: JEJU). 프로그램 참조용
    name VARCHAR(50) NOT NULL,                      -- 권역 이름 (예: 제주)
    sort_no INT NOT NULL DEFAULT 0,                 -- 화면 나열 순서
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_region_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. course — 순례 코스 (사찰 5곳)
CREATE TABLE IF NOT EXISTS course (
    course_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    region_id BIGINT NOT NULL,                      -- 소속 권역
    name VARCHAR(100) NOT NULL,                     -- 코스 이름
    description TEXT NULL,                          -- 코스 소개
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',    -- DRAFT/ACTIVE/INACTIVE. ACTIVE만 사용자 노출
    sort_no INT NOT NULL DEFAULT 0,                 -- 권역 내 나열 순서
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_course_region_status (region_id, status),
    CONSTRAINT fk_course_region_id FOREIGN KEY (region_id)
        REFERENCES region (region_id) ON DELETE RESTRICT,
    CONSTRAINT chk_course_status CHECK (status IN ('DRAFT','ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. gwan_verse — 오관게 5구
CREATE TABLE IF NOT EXISTS gwan_verse (
    verse_no TINYINT NOT NULL PRIMARY KEY,          -- 구절 번호 1~5
    hanja VARCHAR(50) NOT NULL,                     -- 한자 원문
    text_ko VARCHAR(300) NOT NULL,                  -- 우리말 풀이
    theme VARCHAR(100) NOT NULL,                    -- 주제 (감사/자기 사랑/내려놓음/돌봄/과거·현재·미래)
    CONSTRAINT chk_gwan_verse_no CHECK (verse_no BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. site — 사찰
CREATE TABLE IF NOT EXISTS site (
    site_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,                     -- 사찰 이름
    latitude DECIMAL(10,7) NOT NULL,                -- 위도. 시설 고정 위치이며 개인 위치정보가 아님
    longitude DECIMAL(10,7) NOT NULL,               -- 경도. 위와 같음
    verify_radius INT NOT NULL,                     -- 인증 인정 반경(m). 사찰마다 실사로 결정
    qr_version INT NOT NULL DEFAULT 1,              -- QR 버전. 유출 시 +1 하면 기존 QR 일괄 무효
    qr_location_hint VARCHAR(255) NULL,             -- QR 부착 위치 안내 (예: 대웅전 앞 안내판 우측)
    parking_info VARCHAR(500) NULL,                 -- 주차 안내 (라이더 정보 포함)
    access_info VARCHAR(500) NULL,                  -- 진입로 안내 (포장 여부·경사)
    meal_available VARCHAR(20) NULL,                -- 공양 가능 등급. 값 체계 미확정
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',    -- 협의 완료(ACTIVE) 사찰만 노출
    seed_key VARCHAR(80) NULL,                      -- v4 시드 멱등 키: REGION:이름:구분. 재실행 시 중복 생성 방지
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_site_status (status),
    CONSTRAINT uk_site_seed_key UNIQUE (seed_key),
    CONSTRAINT chk_site_status CHECK (status IN ('DRAFT','ACTIVE','INACTIVE')),
    CONSTRAINT chk_site_radius CHECK (verify_radius > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. site_i18n — 사찰 다국어
CREATE TABLE IF NOT EXISTS site_i18n (
    site_i18n_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    site_id BIGINT NOT NULL,                        -- 대상 사찰
    locale VARCHAR(10) NOT NULL,                    -- 번역 언어 (en/ja/zh)
    name VARCHAR(100) NOT NULL,                     -- 사찰 이름 번역
    description TEXT NULL,                          -- 소개 번역
    parking_info VARCHAR(500) NULL,                 -- 주차 안내 번역
    access_info VARCHAR(500) NULL,                  -- 진입로 안내 번역
    CONSTRAINT uk_site_i18n UNIQUE (site_id, locale),
    CONSTRAINT fk_site_i18n_site_id FOREIGN KEY (site_id)
        REFERENCES site (site_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. site_viewpoint — 뷰포인트 (사찰당 1~3)
CREATE TABLE IF NOT EXISTS site_viewpoint (
    site_viewpoint_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    site_id BIGINT NOT NULL,                        -- 대상 사찰
    sort_no TINYINT NOT NULL,                       -- 순번 1~3
    location_desc VARCHAR(255) NOT NULL,            -- 위치 설명 (예: 대웅전 뒤 언덕)
    best_time VARCHAR(100) NULL,                    -- 가장 좋은 시간대 (예: 해질녘)
    what_to_see VARCHAR(255) NULL,                  -- 무엇을 보는가
    CONSTRAINT uk_site_viewpoint UNIQUE (site_id, sort_no),
    CONSTRAINT fk_site_viewpoint_site_id FOREIGN KEY (site_id)
        REFERENCES site (site_id) ON DELETE CASCADE,
    CONSTRAINT chk_site_viewpoint_sort CHECK (sort_no BETWEEN 1 AND 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. site_badge — 꽃절 / 십일수호 뱃지
CREATE TABLE IF NOT EXISTS site_badge (
    site_badge_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    site_id BIGINT NOT NULL,                        -- 대상 사찰
    badge_type VARCHAR(20) NOT NULL,                -- FLOWER(꽃절) / GUARDIAN(십일수호)
    description VARCHAR(500) NULL,                  -- 뱃지 설명 (꽃 종류·시기 등)
    CONSTRAINT uk_site_badge UNIQUE (site_id, badge_type),
    CONSTRAINT fk_site_badge_site_id FOREIGN KEY (site_id)
        REFERENCES site (site_id) ON DELETE CASCADE,
    CONSTRAINT chk_site_badge_type CHECK (badge_type IN ('FLOWER','GUARDIAN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 11. course_site — 코스 안의 자리 (1~5)
--     uk_course_site_site 덕분에 사찰 ID 하나로 코스·자리·구절이 유일하게 결정된다.
--     싱글페이지가 siteId 만 받는 근거다.
CREATE TABLE IF NOT EXISTS course_site (
    course_site_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,                      -- 소속 코스
    site_id BIGINT NOT NULL,                        -- 배정된 사찰
    position TINYINT NOT NULL,                      -- 코스 안 자리 1~5
    verse_no TINYINT NOT NULL,                      -- 이 자리의 오관게 구절 (= position)
    CONSTRAINT uk_course_site_position UNIQUE (course_id, position),
    CONSTRAINT uk_course_site_site UNIQUE (site_id),  -- 사찰은 정확히 한 코스에만
    CONSTRAINT fk_course_site_course_id FOREIGN KEY (course_id)
        REFERENCES course (course_id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_site_site_id FOREIGN KEY (site_id)
        REFERENCES site (site_id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_site_verse_no FOREIGN KEY (verse_no)
        REFERENCES gwan_verse (verse_no) ON DELETE RESTRICT,
    CONSTRAINT chk_course_site_position CHECK (position BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 12. site_distance — 사찰 간 최소 이동시간 (방향 있는 쌍. 코스당 20행, 전체 240행)
CREATE TABLE IF NOT EXISTS site_distance (
    site_a_id BIGINT NOT NULL,                      -- 직전 사찰
    site_b_id BIGINT NOT NULL,                      -- 다음 사찰
    min_minutes INT NOT NULL,                       -- 최소 이동시간(분). 이보다 빠르면 보류 처리
    PRIMARY KEY (site_a_id, site_b_id),
    CONSTRAINT fk_site_distance_site_a_id FOREIGN KEY (site_a_id)
        REFERENCES site (site_id) ON DELETE RESTRICT,
    CONSTRAINT fk_site_distance_site_b_id FOREIGN KEY (site_b_id)
        REFERENCES site (site_id) ON DELETE RESTRICT,
    CONSTRAINT chk_site_distance_diff CHECK (site_a_id <> site_b_id),
    CONSTRAINT chk_site_distance_min CHECK (min_minutes > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 13. expansion_phrase — 확장문구 (7 tier × 5구 × 5버전 = 175편)
CREATE TABLE IF NOT EXISTS expansion_phrase (
    expansion_phrase_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    verse_no TINYINT NOT NULL,                      -- 어느 구절의 문구인지
    tier VARCHAR(10) NOT NULL,                      -- 어느 대상용인지 (7종)
    version_no TINYINT NOT NULL,                    -- 같은 구절·대상 안의 버전 1~5
    text_ko TEXT NOT NULL,                          -- 문구 본문
    review_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',  -- DRAFT / REVIEWED(협회 검수 완료)
    CONSTRAINT uk_expansion_phrase UNIQUE (verse_no, tier, version_no),
    CONSTRAINT fk_expansion_phrase_verse_no FOREIGN KEY (verse_no)
        REFERENCES gwan_verse (verse_no) ON DELETE RESTRICT,
    CONSTRAINT chk_expansion_phrase_tier CHECK (tier IN
        ('AGE20','AGE30','AGE40','AGE50','AGE60','RIDER','FOREIGN')),
    CONSTRAINT chk_expansion_phrase_ver CHECK (version_no BETWEEN 1 AND 5),
    CONSTRAINT chk_expansion_phrase_review CHECK (review_status IN ('DRAFT','REVIEWED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 14. mission — 미션 (다짐 한 문장 과제)
CREATE TABLE IF NOT EXISTS mission (
    mission_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    verse_no TINYINT NULL,                          -- 어느 구절의 미션인지. SITE·COMMON 과제는 NULL
    tier VARCHAR(10) NOT NULL,                      -- 어느 대상용인지
    scope VARCHAR(10) NOT NULL DEFAULT 'VERSE',     -- SITE(이 사찰) / VERSE(이 구절) / COMMON(어디서나). 가중 50/30/20
    site_id BIGINT NULL,                            -- scope=SITE 일 때만 채운다
    site_key BIGINT GENERATED ALWAYS AS (IFNULL(site_id, 0)) STORED,    -- 유니크용. NULL 을 0 으로 접는다
    verse_key TINYINT GENERATED ALWAYS AS (IFNULL(verse_no, 0)) STORED, -- 〃
    variant_no TINYINT NOT NULL,                    -- 같은 축 안의 변형 번호
    body TEXT NOT NULL,                             -- 미션 본문
    origin_ref VARCHAR(20) NULL,                    -- 참고 과제 원형 번호 (예: 3-5)
    review_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',  -- DRAFT / REVIEWED
    -- MySQL 유니크는 NULL 을 서로 다른 값으로 보므로 생성 컬럼으로 접은 뒤 건다(챕터 8 함정 1).
    CONSTRAINT uk_mission UNIQUE (scope, site_key, verse_key, tier, variant_no),
    INDEX idx_mission_verse (verse_no),
    INDEX idx_mission_pool (tier, scope, site_id, verse_no),
    CONSTRAINT fk_mission_verse_no FOREIGN KEY (verse_no)
        REFERENCES gwan_verse (verse_no) ON DELETE RESTRICT,
    CONSTRAINT fk_mission_site_id FOREIGN KEY (site_id)
        REFERENCES site (site_id) ON DELETE RESTRICT,
    CONSTRAINT chk_mission_scope CHECK (scope IN ('SITE','VERSE','COMMON')),
    -- 축과 값이 어긋난 행은 어느 풀에도 안 잡히거나 두 풀에 잡힌다. 넣을 때 막는다.
    CONSTRAINT chk_mission_scope_ref CHECK (
        (scope = 'SITE'   AND site_id IS NOT NULL AND verse_no IS NULL)
     OR (scope = 'VERSE'  AND site_id IS NULL     AND verse_no IS NOT NULL)
     OR (scope = 'COMMON' AND site_id IS NULL     AND verse_no IS NULL)),
    CONSTRAINT chk_mission_tier CHECK (tier IN
        ('AGE20','AGE30','AGE40','AGE50','AGE60','RIDER','FOREIGN')),
    CONSTRAINT chk_mission_variant CHECK (variant_no >= 1),
    CONSTRAINT chk_mission_review CHECK (review_status IN ('DRAFT','REVIEWED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 14-b. manuscript — 사찰별 원고 (챕터 8)
--     기존 mission·expansion_phrase 는 (구, 계층) 축의 레거시 원고이고, 이 표는 (사찰, 구) 축이다.
--     둘을 합치지 않은 이유는 상한·기본 원고 규칙의 단위가 서로 다르기 때문이다(audit/ch8-manuscript.md §1).
--
--     ★ site_key 가 이 표의 핵심이다. MySQL 은 NULL 을 유니크에서 서로 다른 값으로 보기 때문에
--       site_id 에 그냥 유니크를 걸면 기본 원고(site_id NULL)가 몇 편이든 들어간다.
--       생성 컬럼으로 NULL 을 0 으로 접어 두면 그 구멍이 막힌다(함정 1).
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

-- 15. meditation — 명상 108선
CREATE TABLE IF NOT EXISTS meditation (
    meditation_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_no TINYINT NOT NULL,                   -- 카테고리 번호 1~10
    title VARCHAR(100) NOT NULL,                    -- 제목
    script TEXT NOT NULL,                           -- 원고 (도입-본-마무리)
    audio_key VARCHAR(500) NULL,                    -- 음성 파일 저장소 키. 없으면 텍스트만
    duration_sec INT NOT NULL,                      -- 길이(초). 3~5분 원칙
    sort_no INT NOT NULL DEFAULT 0,                 -- 나열 순서
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',    -- DRAFT / ACTIVE. ACTIVE만 노출
    INDEX idx_meditation_cat (category_no, status, sort_no),
    CONSTRAINT chk_meditation_cat CHECK (category_no BETWEEN 1 AND 10),
    CONSTRAINT chk_meditation_dur CHECK (duration_sec BETWEEN 1 AND 300),
    CONSTRAINT chk_meditation_status CHECK (status IN ('DRAFT','ACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 16. meditation_i18n — 명상 다국어
CREATE TABLE IF NOT EXISTS meditation_i18n (
    meditation_i18n_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    meditation_id BIGINT NOT NULL,                  -- 대상 명상
    locale VARCHAR(10) NOT NULL,                    -- 번역 언어
    title VARCHAR(100) NOT NULL,                    -- 제목 번역
    script TEXT NOT NULL,                           -- 원고 번역
    audio_key VARCHAR(500) NULL,                    -- 해당 언어 음성 파일 키
    CONSTRAINT uk_meditation_i18n UNIQUE (meditation_id, locale),
    CONSTRAINT fk_meditation_i18n_meditation_id FOREIGN KEY (meditation_id)
        REFERENCES meditation (meditation_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 17. reward_policy — 보상 규칙
CREATE TABLE IF NOT EXISTS reward_policy (
    reward_policy_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(30) NOT NULL,                      -- 규칙 코드 (프로그램 참조용)
    reward_type VARCHAR(20) NOT NULL,               -- STAMP / COUPON / PHYSICAL
    trigger_type VARCHAR(30) NOT NULL,              -- 언제 주는가 (스탬프/코스/3코스/전체 완주)
    name VARCHAR(100) NOT NULL,                     -- 보상 이름
    description VARCHAR(500) NULL,                  -- 보상 설명
    is_active TINYINT(1) NOT NULL DEFAULT 1,        -- 사용 중 여부
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_reward_policy_code UNIQUE (code),
    INDEX idx_reward_policy_trigger (trigger_type, is_active),
    CONSTRAINT chk_reward_policy_type CHECK
        -- DIGITAL 은 챕터 11 이 더했다. 전자일기장처럼 실물이 아닌 보상은 수령 신청을 받지 않는다.
        (reward_type IN ('STAMP','COUPON','PHYSICAL','DIGITAL')),
    CONSTRAINT chk_reward_policy_trigger CHECK (trigger_type IN
        ('STAMP_COMPLETED','COURSE_COMPLETED','EVERY_THREE_COURSES','ALL_COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================
--  ③ 순례·인증 (4) ★ 핵심
-- =====================================================================

-- 18. pilgrimage — 순례 (회원 × 코스)
--     완주 개수를 컬럼으로 저장하지 않는다. 항상 stamp 에서 COUNT 로 계산한다.
CREATE TABLE IF NOT EXISTS pilgrimage (
    pilgrimage_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,                        -- 걷는 사람
    course_id BIGINT NOT NULL,                      -- 걷는 코스
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',  -- IN_PROGRESS / COMPLETED
    started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 코스 카드를 처음 누른 때
    completed_at DATETIME NULL,                     -- 완주 시각
    CONSTRAINT uk_pilgrimage UNIQUE (user_id, course_id),   -- 코스 탭 연타해도 1건
    INDEX idx_pilgrimage_user_status (user_id, status),
    CONSTRAINT fk_pilgrimage_user_id FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_pilgrimage_course_id FOREIGN KEY (course_id)
        REFERENCES course (course_id) ON DELETE RESTRICT,
    CONSTRAINT chk_pilgrimage_status CHECK (status IN ('IN_PROGRESS','COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 19. stamp — 스탬프 (자리별 인증 결과) ★
--     위도·경도 컬럼이 없다. 앱이 거리를 계산해 판정 결과만 보내므로
--     서버는 사용자가 어디 있었는지 저장하지 않는다.
--     completed_course_site_id 는 COMPLETED 일 때만 값이 생기는 생성 컬럼이고,
--     거기에 UNIQUE 가 걸려 있어 완료 도장은 자리당 1개만 존재한다.
CREATE TABLE IF NOT EXISTS stamp (
    stamp_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pilgrimage_id BIGINT NOT NULL,                  -- 어느 순례의 도장인지
    course_site_id BIGINT NOT NULL,                 -- 코스의 몇 번째 자리인지
    verify_status VARCHAR(20) NOT NULL,             -- GPS_DONE→QR_DONE→COMPLETED / EXPIRED/PENDING/REJECTED
    verify_method VARCHAR(20) NOT NULL,             -- GPS_QR(정상 3단계) / EVIDENCE(예외 접수)
    accuracy_grade VARCHAR(10) NULL,                -- HIGH / MID. LOW는 거절되어 저장 안 됨
    gps_verified_at DATETIME NULL,                  -- 1단계 통과 시각. 여기서 60분 카운트 시작
    qr_verified_at DATETIME NULL,                   -- 2단계 통과 시각
    mission_verified_at DATETIME NULL,              -- 3단계 통과 = 도장 완료 시각
    mission_id BIGINT NULL,                         -- 그때 출제된 미션(레거시 계층 원고)
    manuscript_id BIGINT NULL,                      -- 그때 보여 준 사찰 원고. 원고가 퇴역해도 이 도장의 글은 안 바뀐다(챕터 8)
    expansion_phrase_id BIGINT NULL,                -- 그때 사용자가 읽은 확장문구. 미리보기가 아니라 완료 시점에 기록(챕터 5)
    ext_manuscript_id BIGINT NULL,                  -- 그때 실린 사찰 확장문구(챕터 8). 전자책이 이 시점의 글을 싣는다
    site_id BIGINT NULL,                            -- v4: 실제 인증한 후보 사찰. 완료 도장의 유일성은 여전히 슬롯 기준(uk_stamp_completed)
    user_sentence VARCHAR(500) NULL,                -- 원본 문장. 완료 후 UPDATE 금지 (인증서·전자책 재료)
    photo_key VARCHAR(500) NULL,                    -- 기념 사진 저장소 키 (선택)
    evidence_photo_key VARCHAR(500) NULL,           -- 예외 접수용 증빙 사진 키
    pending_reason VARCHAR(20) NULL,                -- EVIDENCE(사진 심사) / TRAVEL_TIME(이동시간 미달)
    reviewed_by BIGINT NULL,                        -- 심사한 관리자
    reviewed_at DATETIME NULL,                      -- 심사 시각
    review_note VARCHAR(255) NULL,                  -- 승인·반려 메모
    completed_course_site_id BIGINT
        GENERATED ALWAYS AS (                       -- 생성 컬럼. COMPLETED일 때만 자리 번호가 복사됨
            CASE WHEN verify_status = 'COMPLETED' THEN course_site_id ELSE NULL END
        ) STORED,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_stamp_completed UNIQUE (pilgrimage_id, completed_course_site_id),
    INDEX idx_stamp_progress (pilgrimage_id, course_site_id, verify_status),
    INDEX idx_stamp_pilgrimage_done (pilgrimage_id, verify_status, mission_verified_at),
    INDEX idx_stamp_pilgrimage_method (pilgrimage_id, verify_method, created_at),
    INDEX idx_stamp_pending (verify_status, created_at),
    CONSTRAINT fk_stamp_pilgrimage_id FOREIGN KEY (pilgrimage_id)
        REFERENCES pilgrimage (pilgrimage_id) ON DELETE RESTRICT,
    CONSTRAINT fk_stamp_course_site_id FOREIGN KEY (course_site_id)
        REFERENCES course_site (course_site_id) ON DELETE RESTRICT,
    CONSTRAINT fk_stamp_mission_id FOREIGN KEY (mission_id)
        REFERENCES mission (mission_id) ON DELETE RESTRICT,
    CONSTRAINT fk_stamp_manuscript_id FOREIGN KEY (manuscript_id)
        REFERENCES manuscript (manuscript_id) ON DELETE RESTRICT,
    CONSTRAINT fk_stamp_ext_manuscript_id FOREIGN KEY (ext_manuscript_id)
        REFERENCES manuscript (manuscript_id) ON DELETE RESTRICT,
    CONSTRAINT fk_stamp_expansion_phrase_id FOREIGN KEY (expansion_phrase_id)
        REFERENCES expansion_phrase (expansion_phrase_id) ON DELETE RESTRICT,
    CONSTRAINT fk_stamp_site_id FOREIGN KEY (site_id)
        REFERENCES site (site_id) ON DELETE RESTRICT,
    CONSTRAINT fk_stamp_reviewed_by FOREIGN KEY (reviewed_by)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT chk_stamp_status CHECK (verify_status IN
        ('GPS_DONE','QR_DONE','COMPLETED','EXPIRED','PENDING','REJECTED')),
    CONSTRAINT chk_stamp_method CHECK (verify_method IN ('GPS_QR','EVIDENCE')),
    CONSTRAINT chk_stamp_accuracy CHECK
        (accuracy_grade IS NULL OR accuracy_grade IN ('HIGH','MID')),
    CONSTRAINT chk_stamp_pending CHECK
        (pending_reason IS NULL OR pending_reason IN ('EVIDENCE','TRAVEL_TIME'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 20. phrase_seen — 확장문구 노출 이력 (INSERT IGNORE 로 넣는다)
CREATE TABLE IF NOT EXISTS phrase_seen (
    phrase_seen_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,                        -- 본 사람
    course_id BIGINT NOT NULL,                      -- 어느 코스를 걷는 동안 봤는지
    expansion_phrase_id BIGINT NOT NULL,            -- 본 문구. 같은 코스 내 반복 방지
    seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_phrase_seen UNIQUE (user_id, course_id, expansion_phrase_id),
    INDEX idx_phrase_seen_user_phrase (user_id, expansion_phrase_id),
    CONSTRAINT fk_phrase_seen_user_id FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_phrase_seen_course_id FOREIGN KEY (course_id)
        REFERENCES course (course_id) ON DELETE RESTRICT,
    CONSTRAINT fk_phrase_seen_expansion_phrase_id FOREIGN KEY (expansion_phrase_id)
        REFERENCES expansion_phrase (expansion_phrase_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 21. task_seen — 미션 노출 이력 (INSERT IGNORE 로 넣는다)
CREATE TABLE IF NOT EXISTS task_seen (
    task_seen_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,                        -- 받은 사람
    course_id BIGINT NOT NULL,                      -- 어느 코스에서 받았는지
    mission_id BIGINT NOT NULL,                     -- 받은 미션. 같은 코스 내 반복 방지
    seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_task_seen UNIQUE (user_id, course_id, mission_id),
    INDEX idx_task_seen_user_mission (user_id, mission_id),
    CONSTRAINT fk_task_seen_user_id FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_task_seen_course_id FOREIGN KEY (course_id)
        REFERENCES course (course_id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_seen_mission_id FOREIGN KEY (mission_id)
        REFERENCES mission (mission_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================
--  ④ 기록 (3)
-- =====================================================================

-- 22. thinkbox — 생각상자
--     점수·순위·답장 컬럼이 없다. 비교하지 않는 기록이라는 설계다.
CREATE TABLE IF NOT EXISTS thinkbox (
    thinkbox_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,                        -- 작성자
    body TEXT NOT NULL,                             -- 기록 본문. 미션 문장의 수정 가능한 복사본
    source VARCHAR(10) NOT NULL,                    -- MISSION(도장에서 옴) / DIRECT(직접 작성)
    stamp_id BIGINT NULL,                           -- MISSION일 때 출처 도장
    is_edited TINYINT(1) NOT NULL DEFAULT 0,        -- 원본에서 수정됐는지
    course_id BIGINT NULL,                          -- 관련 코스 (선택)
    site_id BIGINT NULL,                            -- 관련 사찰 (선택)
    is_private TINYINT(1) NOT NULL DEFAULT 0,       -- 비공개(공개 화면에서 감춤). 전자책에는 실린다 — 개인 소장본(챕터 9)
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_thinkbox_stamp UNIQUE (stamp_id),
    INDEX idx_thinkbox_user_date (user_id, created_at),
    INDEX idx_thinkbox_user_course (user_id, course_id),
    INDEX idx_thinkbox_user_site (user_id, site_id),
    CONSTRAINT fk_thinkbox_user_id FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_thinkbox_stamp_id FOREIGN KEY (stamp_id)
        REFERENCES stamp (stamp_id) ON DELETE RESTRICT,
    CONSTRAINT fk_thinkbox_course_id FOREIGN KEY (course_id)
        REFERENCES course (course_id) ON DELETE RESTRICT,
    CONSTRAINT fk_thinkbox_site_id FOREIGN KEY (site_id)
        REFERENCES site (site_id) ON DELETE RESTRICT,
    CONSTRAINT chk_thinkbox_source CHECK (source IN ('MISSION','DIRECT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 23. photo — 사진 (회원당 사찰당 1장)
CREATE TABLE IF NOT EXISTS photo (
    photo_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,                        -- 소유자
    site_id BIGINT NOT NULL,                        -- 대상 사찰
    file_key VARCHAR(500) NOT NULL,                 -- 저장소 파일 키
    has_other_face TINYINT(1) NOT NULL DEFAULT 0,   -- 타인 얼굴 포함 (업로드 시 자기 신고)
    is_private TINYINT(1) NOT NULL DEFAULT 0,       -- 비공개(공개 화면에서 감춤). 전자책에는 실린다 — 개인 소장본(챕터 9)
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_photo_user_site UNIQUE (user_id, site_id),
    CONSTRAINT fk_photo_user_id FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_photo_site_id FOREIGN KEY (site_id)
        REFERENCES site (site_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 24. meditation_log — 명상 재생 기록
CREATE TABLE IF NOT EXISTS meditation_log (
    meditation_log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,                        -- 들은 사람
    meditation_id BIGINT NOT NULL,                  -- 들은 명상
    played_sec INT NOT NULL,                        -- 재생 시간(초)
    memo VARCHAR(1000) NULL,                        -- 들은 뒤 메모 (선택)
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_meditation_log_user (user_id, created_at),
    CONSTRAINT fk_meditation_log_user_id FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_meditation_log_meditation_id FOREIGN KEY (meditation_id)
        REFERENCES meditation (meditation_id) ON DELETE RESTRICT,
    CONSTRAINT chk_meditation_log_sec CHECK (played_sec >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================
--  ⑤ 산출물·보상 (4)
-- =====================================================================

-- 25. user_reward — 내 보상
CREATE TABLE IF NOT EXISTS user_reward (
    user_reward_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,                        -- 받는 사람
    reward_policy_id BIGINT NOT NULL,               -- 어떤 규칙으로 받았는지
    stamp_id BIGINT NULL,                           -- 스탬프 단위 보상의 근거
    pilgrimage_id BIGINT NULL,                      -- 코스 단위 보상의 근거
    milestone SMALLINT NULL,                        -- 3·6·9·12 — 같은 정책으로 여러 번 받는 유일한 보상(전자일기장)
    -- 사용자 단위 보상의 유니크 키. MySQL 유니크는 NULL 을 서로 다른 값으로 보므로
    -- pilgrimage_id 를 비우는 것만으로는 중복이 막히지 않는다(챕터 8 함정 1 과 같은 자리).
    user_key BIGINT GENERATED ALWAYS AS (
        CASE WHEN stamp_id IS NULL AND pilgrimage_id IS NULL THEN user_id END) STORED,
    milestone_key SMALLINT GENERATED ALWAYS AS (IFNULL(milestone, 0)) STORED,
    status VARCHAR(20) NOT NULL DEFAULT 'GRANTED',  -- GRANTED→CLAIMED→PAID/REJECTED, 의심 시 UNDER_REVIEW
    needs_review TINYINT(1) NOT NULL DEFAULT 0,     -- 완주가 취소됐는데 이미 움직인 보상. 사람이 봐야 한다(챕터 7 §2-4)
    audit_score DECIMAL(6,2) NULL,                  -- 부정 의심 자동 점수. 초기엔 기록만 하고 전부 통과
    audit_detail JSON NULL,                         -- 점수 근거 (신호별 세부)
    granted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 적립 시각
    claimed_at DATETIME NULL,                       -- 청구 시각
    resolved_at DATETIME NULL,                      -- 심사 종료 시각
    resolved_by BIGINT NULL,                        -- 심사한 관리자
    tracking_no VARCHAR(100) NULL,                  -- 송장 문자열. 발송을 별도 상태로 두지 않고 PAID 전이 때 함께 받는다
    CONSTRAINT uk_user_reward_stamp UNIQUE (reward_policy_id, stamp_id),
    CONSTRAINT uk_user_reward_pilgrimage UNIQUE (reward_policy_id, pilgrimage_id),
    -- "이 사람의 누적" 에 주는 보상은 코스가 아니라 사람으로 묶인다(리뷰 2-2).
    CONSTRAINT uk_user_reward_user UNIQUE (reward_policy_id, user_key, milestone_key),
    INDEX idx_user_reward_user (user_id, status),
    INDEX idx_user_reward_review (status, claimed_at),
    CONSTRAINT fk_user_reward_user_id FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_reward_reward_policy_id FOREIGN KEY (reward_policy_id)
        REFERENCES reward_policy (reward_policy_id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_reward_stamp_id FOREIGN KEY (stamp_id)
        REFERENCES stamp (stamp_id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_reward_pilgrimage_id FOREIGN KEY (pilgrimage_id)
        REFERENCES pilgrimage (pilgrimage_id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_reward_resolved_by FOREIGN KEY (resolved_by)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT chk_user_reward_status CHECK (status IN
        ('GRANTED','CLAIMED','UNDER_REVIEW','PAID','REJECTED','REVOKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 25-b. reward_claim — 실물 보상 배송 정보 (챕터 7 보강 B-4)
--     본체(user_reward)와 나누는 이유는 하나다: 목록 API 가 user_reward 만 읽기 때문이다.
--     주소를 본체에 두면 "내 보상 목록" 한 번에 주소가 따라 나가고, 그 응답은 화면·로그·캐시를 탄다.
--     보상 하나에 배송 정보 하나(uk). 반려 뒤 다시 신청하면 덮어쓴다 — 주소가 바뀌었을 수 있다.
CREATE TABLE IF NOT EXISTS reward_claim (
    reward_claim_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_reward_id BIGINT NOT NULL,                 -- 어느 보상의 신청인가
    recipient_name VARCHAR(50) NOT NULL,            -- 받는 사람
    phone VARCHAR(30) NOT NULL,                     -- 연락처
    address VARCHAR(200) NOT NULL,                  -- 주소
    memo VARCHAR(200) NULL,                         -- 배송 메모 (선택)
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_reward_claim UNIQUE (user_reward_id),
    CONSTRAINT fk_reward_claim_user_reward_id FOREIGN KEY (user_reward_id)
        REFERENCES user_reward (user_reward_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 26. certificate — 인증서
CREATE TABLE IF NOT EXISTS certificate (
    certificate_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,                        -- 소유자
    pilgrimage_id BIGINT NULL,                      -- 순례형일 때 근거. 중복 발급 차단
    cert_type VARCHAR(20) NOT NULL,                 -- PILGRIMAGE(코스 완주) / HOEHYANG(60곳 회향)
    status VARCHAR(20) NOT NULL DEFAULT 'VALID',    -- VALID → REVOKED 한 방향. 회수해도 행은 남는다
    serial_no VARCHAR(40) NOT NULL,                 -- 일련번호 PG-2026-000012. 제3자 진위 확인 키
    issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at DATETIME NULL,                       -- 회수 시각
    revoke_reason VARCHAR(30) NULL,                 -- COMPLETION_CANCELED / ADMIN / USER_WITHDRAWN
    file_key VARCHAR(500) NULL,                     -- 인증서 파일(PDF/이미지) 저장소 키
    -- ★ 유효한 것만 완주 1건당 하나다. 회수된 옛 인증서는 같은 완주를 가리킨 채 남아야
    --   공개 진위 확인에서 "그런 번호가 있었고 지금은 무효" 로 계속 조회된다(챕터 7 §3-4).
    --   stamp.completed_course_site_id 와 같은 수법 — 조건부 유일성을 생성 컬럼으로 만든다.
    valid_pilgrimage_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status = 'VALID' THEN pilgrimage_id END) STORED,
    CONSTRAINT uk_certificate_serial UNIQUE (serial_no),
    CONSTRAINT uk_certificate_valid_pilgrimage UNIQUE (valid_pilgrimage_id),
    CONSTRAINT chk_certificate_status CHECK (status IN ('VALID','REVOKED')),
    INDEX idx_certificate_pilgrimage (pilgrimage_id),   -- 외래키가 쓸 색인. 유니크가 조건부로 바뀌어 따로 둔다
    INDEX idx_certificate_user (user_id),
    CONSTRAINT fk_certificate_user_id FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_certificate_pilgrimage_id FOREIGN KEY (pilgrimage_id)
        REFERENCES pilgrimage (pilgrimage_id) ON DELETE RESTRICT,
    CONSTRAINT chk_certificate_type CHECK (cert_type IN ('PILGRIMAGE','HOEHYANG'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 26-b. cert_serial — 인증서 일련번호 시퀀스 (챕터 7 §3-1)
--     COUNT(*)+1 이나 MAX+1 로 번호를 만들면 동시에 두 건이 발급될 때 같은 번호가 나오고,
--     회수로 행이 빠지면 번호가 재사용된다. 재사용된 번호는 공개 진위 확인을 거짓말로 만든다.
--     그래서 발급 수와 무관한 시퀀스를 따로 둔다. 되돌리지 않는다(회수해도 번호는 소비된 것).
CREATE TABLE IF NOT EXISTS cert_serial (
    cert_type VARCHAR(20) NOT NULL,                 -- PILGRIMAGE / HOEHYANG
    issue_year INT NOT NULL,                        -- 번호에 들어가는 연도
    next_seq INT NOT NULL,                          -- 마지막으로 내준 번호
    PRIMARY KEY (cert_type, issue_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 27. ebook — 전자책 생성 큐
CREATE TABLE IF NOT EXISTS ebook (
    ebook_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,                        -- 소유자
    pilgrimage_id BIGINT NULL,                      -- 코스 완주본일 때 근거
    ebook_type VARCHAR(20) NOT NULL,                -- PILGRIMAGE/INTERIM/HOEHYANG/PERSONAL(개인 소장본·챕터 9)
    snapshot_hash CHAR(64) NULL,                    -- 재료 정렬 → SHA-256. 같으면 같은 책(챕터 9 §2-1)
    milestone SMALLINT NULL,                        -- 전자일기장(INTERIM)의 3·6·9·12. 그 밖의 종류는 NULL
    -- ★ 전자일기장에만 값이 있다. 다른 종류는 NULL 이고, MySQL 유니크는 NULL 을 서로 다른 값으로 보므로
    --   제약 자체가 걸리지 않는다. IFNULL 로 0을 채우면 개인 소장본이 사용자당 한 권으로 묶여 버린다.
    milestone_key SMALLINT GENERATED ALWAYS AS (CASE WHEN ebook_type = 'INTERIM' THEN IFNULL(milestone, 0) END) STORED,
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',-- REQUESTED→READY/FAILED
    pdf_key VARCHAR(500) NULL,                      -- 완성된 PDF 저장소 키
    epub_key VARCHAR(500) NULL,                     -- 완성된 EPUB 저장소 키
    fail_reason VARCHAR(500) NULL,                  -- 실패 원인
    page_count INT NULL,                            -- READY 일 때만(챕터 9)
    byte_size BIGINT NULL,                          -- READY 일 때만(챕터 9)
    retry_count INT NOT NULL DEFAULT 0,             -- 옛 대기열의 재시도 횟수. 챕터 9 흐름은 쓰지 않는다
    building_started_at DATETIME NULL,              -- 옛 대기열의 좀비 회수 기준. 챕터 9 흐름은 쓰지 않는다
    queued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,   -- 대기열 등록 시각
    built_at DATETIME NULL,                         -- 완성 시각
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_ebook_pilgrimage_type UNIQUE (pilgrimage_id, ebook_type),
    -- 해시가 NULL 인 옛 방식(완주가 넣는 코스본)은 이 제약에 걸리지 않는다 — NULL 은 유니크에서 서로 다르다
    CONSTRAINT uk_ebook_user_snapshot UNIQUE (user_id, snapshot_hash),
    -- 전자일기장은 3·6·9·12 마다 한 권이다. 마일스톤이 키에 없으면 6코스 책이 3코스 책과 부딪혀 사라진다.
    CONSTRAINT uk_ebook_user_milestone UNIQUE (user_id, ebook_type, milestone_key),
    INDEX idx_ebook_worker (status, queued_at),
    INDEX idx_ebook_user (user_id, ebook_type),
    CONSTRAINT fk_ebook_user_id FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_ebook_pilgrimage_id FOREIGN KEY (pilgrimage_id)
        REFERENCES pilgrimage (pilgrimage_id) ON DELETE RESTRICT,
    CONSTRAINT chk_ebook_type CHECK (ebook_type IN ('PILGRIMAGE','INTERIM','HOEHYANG','PERSONAL')),
    -- BUILDING 을 없앴다. 있는 행을 FOR UPDATE 로 잠그고 그 안에서 만들면 중간 상태가 필요 없고,
    -- 중간 상태는 "BUILDING 인 채 죽은 행" 을 회수하는 코드를 또 부른다(챕터 9 §2-2).
    CONSTRAINT chk_ebook_status CHECK (status IN ('REQUESTED','READY','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 28. print_order — 소량 인쇄 신청
CREATE TABLE IF NOT EXISTS print_order (
    print_order_id  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL                       COMMENT '신청자',
    ebook_id        BIGINT       NOT NULL                       COMMENT '대상 전자책 (회향본만 허용 — 서비스 검증)',
    quantity        INT          NOT NULL DEFAULT 1             COMMENT '부수 1~3',

    -- 배송정보는 print_order_address 로 뗐다(챕터 9 §4). 목록 API 가 본체만 읽으므로
    -- 같은 표에 두면 목록을 부를 때마다 주소가 함께 나갈 길이 열린다(reward_claim 과 같은 이유).

    status          VARCHAR(20)  NOT NULL DEFAULT 'REQUESTED'   COMMENT 'REQUESTED→CONFIRMED→PRINTING→SHIPPED→DONE / CANCELED',
    note            VARCHAR(500) NULL                           COMMENT '신청자 메모',
    tracking_no     VARCHAR(50)  NULL                           COMMENT '송장번호. SHIPPED 전환 시 필수 — 서비스 검증',
    cancel_reason   VARCHAR(200) NULL                           COMMENT '관리자 취소 사유. 취소는 사유 필수',
    needs_review    TINYINT(1)   NOT NULL DEFAULT 0             COMMENT '탈퇴했는데 실물이 이미 움직이는 주문. 사람이 본다',

    requested_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (print_order_id),

    CONSTRAINT fk_print_order_user_id
        FOREIGN KEY (user_id)  REFERENCES users (user_id)   ON DELETE RESTRICT,
    CONSTRAINT fk_print_order_ebook_id
        FOREIGN KEY (ebook_id) REFERENCES ebook (ebook_id)  ON DELETE RESTRICT,

    CONSTRAINT chk_print_order_qty
        CHECK (quantity BETWEEN 1 AND 5),
    CONSTRAINT chk_print_order_status
        CHECK (status IN ('REQUESTED','CONFIRMED','PRINTING','SHIPPED','DONE','CANCELED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 28-b. print_order_address — 배송정보(챕터 9 §4). 본체와 1:1, 목록 API 에는 실리지 않는다.
CREATE TABLE IF NOT EXISTS print_order_address (
    print_order_id BIGINT PRIMARY KEY,
    recipient VARCHAR(50) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    postal_code VARCHAR(10) NULL,
    address VARCHAR(200) NOT NULL,
    memo VARCHAR(200) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_print_order_address_order FOREIGN KEY (print_order_id)
        REFERENCES print_order (print_order_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================
--  ⑧ 사찰 가는 법 (2) — 챕터 2 v2 델타
--     기존 테이블은 건드리지 않는다. 두 표만 새로 붙인다.
-- =====================================================================

-- 29. shrine_element — 참배 순서 사전. 60곳 공통, 7행 고정
--     자리 수와 순서가 어느 절이든 같다(R1). 순서 기준은 sort_no 하나뿐이며
--     site_element 에는 순서 컬럼을 두지 않아, 절마다 순서가 달라지는 것을 원천 차단한다.
CREATE TABLE IF NOT EXISTS shrine_element (
    element_id      BIGINT       AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(30)  NOT NULL,            -- ILJUMUN / GEUMGANGMUN / CHEONWANGMUN / BURIMUN / PAGODA / MAIN_HALL / ANNEX_HALL
    name            VARCHAR(50)  NOT NULL,            -- 일주문 · 금강문 · 천왕문 · 불이문 · 탑 · 주불전 · 부속전각
    sort_no         TINYINT      NOT NULL,            -- 참배 순서 1~7
    meaning         VARCHAR(500) NOT NULL,            -- ① 이 요소가 담은 의미. 없는 절에서도 이 문장은 보여준다(R3)
    etiquette       VARCHAR(500) NULL,                -- 있을 때의 참배 예법. 없는 절에서는 null 로 나간다
    passage_meaning VARCHAR(500) NOT NULL,            -- ② 직전 자리 → 이 자리로 오는 길의 의미. 다음 행의 것이 ③이 된다
    CONSTRAINT uk_shrine_element_code UNIQUE (code),
    CONSTRAINT uk_shrine_element_sort UNIQUE (sort_no),
    CONSTRAINT chk_shrine_element_sort CHECK (sort_no BETWEEN 1 AND 7)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 30. site_element — 사찰별 보유 요소. 행이 있으면 "있다", 없으면 "없다".
--     ON DELETE RESTRICT 로 사전 행을 지우지 못하게 막는다 — 7자리 형식이 무너지면 응답 계약이 깨진다.
CREATE TABLE IF NOT EXISTS site_element (
    site_element_id BIGINT       AUTO_INCREMENT PRIMARY KEY,
    site_id         BIGINT       NOT NULL,
    element_id      BIGINT       NOT NULL,
    local_name      VARCHAR(100) NULL,                -- 그 절의 실제 이름. 주불전 → 대웅전/대적광전/무량수전, 일주문 → 조계문
    note            VARCHAR(500) NULL,                -- 그 절만의 서술 (예: 3층 석탑, 통일신라 9세기)
    CONSTRAINT uk_site_element UNIQUE (site_id, element_id),
    CONSTRAINT fk_site_element_site_id    FOREIGN KEY (site_id)
        REFERENCES site (site_id) ON DELETE CASCADE,
    CONSTRAINT fk_site_element_element_id FOREIGN KEY (element_id)
        REFERENCES shrine_element (element_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================
--  ⑨ v4 — 슬롯 후보 (챕터 4 마감)
-- =====================================================================

-- 31. slot_site — 한 슬롯(코스의 자리 = 구)에 배정된 후보 사찰들
--     한 사찰이 여러 권역·구의 후보가 될 수 있다(대원사 산청: 부산/경남 2구 MAIN+SUNROAD).
--     course_site.site_id(대표)는 uk_course_site_site 로 계속 유일하다.
CREATE TABLE IF NOT EXISTS slot_site (
    slot_site_id   BIGINT       NOT NULL AUTO_INCREMENT,
    course_site_id BIGINT       NOT NULL                    COMMENT '슬롯(코스의 자리 = 구)',
    site_id        BIGINT       NOT NULL                    COMMENT '후보 사찰',
    track          VARCHAR(10)  NOT NULL DEFAULT 'MAIN'     COMMENT 'MAIN(공양의 길) / SUNROAD(선로드)',
    sort_no        INT          NOT NULL DEFAULT 0          COMMENT '후보 나열 순서(1=대표)',
    route_note     VARCHAR(255) NULL                        COMMENT '선로드 노선(배내고개·에덴밸리 등)',
    is_star        TINYINT(1)   NOT NULL DEFAULT 0          COMMENT '선로드 ★ 표시',
    serving_note   VARCHAR(255) NULL                        COMMENT '배정 근거(장독 5,000개 등)',
    is_congested   TINYINT(1)   NOT NULL DEFAULT 0          COMMENT 'Q3: 과포화 사찰 — 1이면 공개 응답에서 비노출(관리자는 보임)',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (slot_site_id),
    CONSTRAINT uk_slot_site UNIQUE (course_site_id, site_id, track),
    INDEX idx_slot_site_site (site_id),
    CONSTRAINT fk_slot_site_course_site_id FOREIGN KEY (course_site_id) REFERENCES course_site (course_site_id) ON DELETE RESTRICT,
    CONSTRAINT fk_slot_site_site_id        FOREIGN KEY (site_id)        REFERENCES site (site_id)               ON DELETE RESTRICT,
    CONSTRAINT chk_slot_site_track CHECK (track IN ('MAIN','SUNROAD')),
    -- 한 사찰이 같은 트랙으로 두 자리에 들어가면 같은 절에서 도장을 두 번 받는 길이 열린다(김해원 제안).
    CONSTRAINT uk_slot_site_site UNIQUE (site_id, track)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
