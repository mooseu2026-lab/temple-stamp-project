-- backend/docs/verify/alter-ch7.sql — 챕터 7 스키마 변경 3건
--
--   mysql -uroot -p temple_stamp_project < backend/docs/verify/alter-ch7.sql
--
-- schema.sql 은 CREATE TABLE IF NOT EXISTS 라 이미 있는 표에는 컬럼을 더하지 않는다.
-- 그래서 이미 만들어진 DB(로컬·운영)에는 이 파일을 손으로 한 번 돌려야 한다.
-- 운영 반영 목록은 정리.md §5-1.
-- USE 를 두지 않는다. 부르는 쪽이 스키마를 정한다:
--   mysql -u<user> -p <스키마> < 이 파일
-- 파일 안에 USE 가 있으면 운영 스키마에 올리려고 불러도 조용히 개발 DB 로 간다(최종 점검 F).

-- ① 보상: 회수 상태 + "사람이 봐야 함" 표시 (챕터 7 §2-4)
ALTER TABLE user_reward
    ADD COLUMN needs_review TINYINT(1) NOT NULL DEFAULT 0 AFTER status;
ALTER TABLE user_reward
    DROP CHECK chk_user_reward_status;
ALTER TABLE user_reward
    ADD CONSTRAINT chk_user_reward_status CHECK (status IN
        ('GRANTED','CLAIMED','UNDER_REVIEW','PAID','REJECTED','REVOKED'));

-- ② 인증서: 회수해도 행을 남긴다 (챕터 7 §3-2)
ALTER TABLE certificate
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'VALID' AFTER cert_type,
    ADD COLUMN revoked_at DATETIME NULL AFTER issued_at,
    ADD COLUMN revoke_reason VARCHAR(30) NULL AFTER revoked_at;
ALTER TABLE certificate
    ADD CONSTRAINT chk_certificate_status CHECK (status IN ('VALID','REVOKED'));

-- ③ 인증서: 유효한 것만 완주 1건당 하나 (회수본은 같은 완주를 가리킨 채 남는다)
--    유니크를 먼저 지우면 외래키가 쓸 색인이 사라져 ERROR 1553 이 난다. 평범한 색인을 먼저 깔고 지운다.
ALTER TABLE certificate
    ADD INDEX idx_certificate_pilgrimage (pilgrimage_id);
ALTER TABLE certificate
    DROP INDEX uk_certificate_pilgrimage;
ALTER TABLE certificate
    ADD COLUMN valid_pilgrimage_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status = 'VALID' THEN pilgrimage_id END) STORED AFTER pilgrimage_id;
ALTER TABLE certificate
    ADD CONSTRAINT uk_certificate_valid_pilgrimage UNIQUE (valid_pilgrimage_id);

-- 확인
SELECT '① user_reward' AS step;
SHOW COLUMNS FROM user_reward LIKE 'needs_review';
SELECT '② certificate' AS step;
SHOW COLUMNS FROM certificate LIKE 'status';
SHOW COLUMNS FROM certificate LIKE 'valid_pilgrimage_id';

-- ============================================================
--  챕터 7 보강 (2026-09-06) — 아래 셋은 보강에서 추가된 것이다.
-- ============================================================

-- ④ 송장 문자열. 발송을 별도 상태로 두지 않고 승인(PAID) 전이 때 함께 받는다.
ALTER TABLE user_reward
    ADD COLUMN tracking_no VARCHAR(100) NULL AFTER resolved_by;

-- ⑤ 실물 보상 배송 정보. 본체와 나누는 이유는 목록 API 가 user_reward 만 읽기 때문이다 —
--    주소를 본체에 두면 "내 보상 목록" 한 번에 주소가 따라 나간다.
CREATE TABLE IF NOT EXISTS reward_claim (
    reward_claim_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_reward_id BIGINT NOT NULL,
    recipient_name VARCHAR(50) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    address VARCHAR(200) NOT NULL,
    memo VARCHAR(200) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_reward_claim UNIQUE (user_reward_id),
    CONSTRAINT fk_reward_claim_user_reward_id FOREIGN KEY (user_reward_id)
        REFERENCES user_reward (user_reward_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ⑥ cert_serial — 인증서 번호를 세는 표(챕터 7). 신규 표라 CREATE 가 만든다.
--    이 줄이 없었다. schema.sql 에만 있어서, ALTER 로 올린 DB 에는 표 자체가 생기지 않고
--    첫 인증서 발행에서 "표가 없다" 로 터진다 — 챕터 9 보강의 H17(사슬 vs schema.sql) 이 찾았다.
--    빠뜨려도 기동은 성공하고 로그도 조용하다. 완주가 처음 나오는 날에야 드러난다.
CREATE TABLE IF NOT EXISTS cert_serial (
    cert_type VARCHAR(20) NOT NULL,                 -- PILGRIMAGE / HOEHYANG
    issue_year INT NOT NULL,                        -- 번호에 들어가는 연도
    next_seq INT NOT NULL,                          -- 마지막으로 내준 번호
    PRIMARY KEY (cert_type, issue_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 확인
SELECT '④ user_reward.tracking_no' AS step;
SHOW COLUMNS FROM user_reward LIKE 'tracking_no';
SELECT '⑤ reward_claim' AS step;
SHOW TABLES LIKE 'reward_claim';
SELECT '⑥ cert_serial' AS step;
SHOW TABLES LIKE 'cert_serial';
