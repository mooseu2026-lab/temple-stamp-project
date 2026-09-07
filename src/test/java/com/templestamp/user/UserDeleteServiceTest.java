package com.templestamp.user;

import com.templestamp.certificate.CertificateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;

/**
 * 회원 탈퇴 연쇄(챕터 1 보강 §3). 아홉 단계를 하나씩 본다.
 * <p>
 * 이 기능의 어려운 점은 "무엇을 지우느냐" 가 아니라 <b>무엇을 남기느냐</b> 다 —
 * 인증서 번호는 제3자가 확인하던 값이고 완주 기록은 집계라, 지우면 안 되는 것이 지워도 되는 것보다 많다.
 * 그래서 삭제 검사만큼 "남아 있는지" 검사를 함께 둔다.
 */
@SpringBootTest
class UserDeleteServiceTest {

    private static final String EMAIL = "delete-test@test.com";
    private static final long COURSE_ID = 1L;

    @Autowired AccountDeletionService accountDeletionService;
    @Autowired CertificateService certificateService;
    @Autowired JdbcTemplate jdbc;
    /**
     * 롤백 검사에서 한 단계를 일부러 터뜨리려고 가짜로 바꾼다.
     * 다른 검사에서는 진짜처럼 굴어야 하므로 {@code @BeforeEach} 에서 원래 동작을 되돌려 준다.
     */
    @MockitoSpyBean StorageOrphanMapper storageOrphanMapper;

    private long userId;
    private long pilgrimageId;
    private long stampId;
    private String serialNo;

    @BeforeEach
    void setUp() {
        reset(storageOrphanMapper);   // 앞 검사에서 씌운 가짜 동작을 지운다
        cleanUp();
        jdbc.update("""
                INSERT INTO users (email, password, nickname, role, tier, locale)
                VALUES (?, '$2a$10$FVVkQmIpuwsYytWwqTLv7exlCfmLsSNovNp13Bo6m.DrG8cLgZj1a', '탈퇴시험', 'USER', 'AGE30', 'ko')
                """, EMAIL);
        userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, EMAIL);

        // 동의·세션
        jdbc.update("""
                INSERT INTO user_agreement (user_id, agreement_type, agreement_version, agreed_at)
                VALUES (?, 'LOCATION_SERVICE', '1', NOW())""", userId);
        jdbc.update("""
                INSERT INTO refresh_token (user_id, token_hash, expires_at)
                VALUES (?, REPEAT('a', 64), NOW() + INTERVAL 14 DAY)""", userId);

        // 순례 + 도장(완료 1 · 진행 중 1)
        jdbc.update("INSERT INTO pilgrimage (user_id, course_id, status) VALUES (?, ?, 'COMPLETED')",
                userId, COURSE_ID);
        pilgrimageId = jdbc.queryForObject(
                "SELECT pilgrimage_id FROM pilgrimage WHERE user_id = ? AND course_id = ?",
                Long.class, userId, COURSE_ID);
        Map<String, Object> slot = jdbc.queryForList(
                "SELECT course_site_id, site_id FROM course_site WHERE course_id = ? ORDER BY position", COURSE_ID).get(0);
        long courseSiteId = ((Number) slot.get("course_site_id")).longValue();
        long siteId = ((Number) slot.get("site_id")).longValue();
        jdbc.update("""
                INSERT INTO stamp (pilgrimage_id, course_site_id, site_id, verify_status, verify_method,
                                   user_sentence, mission_verified_at)
                VALUES (?, ?, ?, 'COMPLETED', 'GPS_QR', '어제 걸었다.', NOW())""",
                pilgrimageId, courseSiteId, siteId);
        // 도장에도 그 사람이 쓴 것이 남는다 — 사진 키와 다짐 문장(챕터 8 STEP 0).
        jdbc.update("UPDATE stamp SET photo_key = ? WHERE pilgrimage_id = ? AND verify_status = 'COMPLETED'",
                "PHOTO/" + userId + "/stamp.jpg", pilgrimageId);
        jdbc.update("""
                INSERT INTO stamp (pilgrimage_id, course_site_id, site_id, verify_status, verify_method, gps_verified_at)
                VALUES (?, ?, ?, 'GPS_DONE', 'GPS_QR', NOW())""",
                pilgrimageId, secondSlot(), siteId);
        stampId = jdbc.queryForObject(
                "SELECT MAX(stamp_id) FROM stamp WHERE pilgrimage_id = ?", Long.class, pilgrimageId);

        // 인증서
        serialNo = certificateService.issueForPilgrimage(userId, pilgrimageId).getSerialNo();

        // 보상 둘 — 하나는 손대지 않은 것, 하나는 이미 신청까지 간 것
        jdbc.update("""
                INSERT INTO user_reward (user_id, reward_policy_id, pilgrimage_id, status)
                VALUES (?, 2, ?, 'GRANTED')""", userId, pilgrimageId);
        jdbc.update("""
                INSERT INTO user_reward (user_id, reward_policy_id, stamp_id, status, claimed_at)
                VALUES (?, 1, ?, 'CLAIMED', NOW())""", userId, stampId);
        Long claimed = jdbc.queryForObject(
                "SELECT user_reward_id FROM user_reward WHERE user_id = ? AND status = 'CLAIMED'", Long.class, userId);
        jdbc.update("""
                INSERT INTO reward_claim (user_reward_id, recipient_name, phone, address)
                VALUES (?, '탈퇴시험', '010-1234-5678', '서울시 어딘가 1길 2')""", claimed);

        // 사진·문장·명상
        jdbc.update("""
                INSERT INTO photo (user_id, site_id, file_key, has_other_face, is_private)
                VALUES (?, ?, ?, 0, 0)""", userId, siteId, "PHOTO/" + userId + "/aaaa.jpg");
        jdbc.update("""
                INSERT INTO thinkbox (user_id, site_id, body, source, is_private)
                VALUES (?, ?, '탈퇴 전에 남겨 둔 한 줄.', 'DIRECT', 0)""", userId, siteId);
        jdbc.update("""
                INSERT INTO meditation_log (user_id, meditation_id, played_sec)
                VALUES (?, (SELECT MIN(meditation_id) FROM meditation), 60)""", userId);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("① 사람은 지우지 않고 익명화한다 — 상태·시각이 남고 개인정보는 사라진다")
    void anonymizes_instead_of_deleting() {
        accountDeletionService.deleteCascade(userId);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, email, nickname, password, deleted_at, tier FROM users WHERE user_id = ?", userId);
        assertThat(row.get("status")).isEqualTo("DELETED");
        assertThat((String) row.get("email")).isEqualTo("deleted-" + userId + "@invalid");
        assertThat(row.get("nickname")).isEqualTo("탈퇴회원");
        assertThat(row.get("password")).as("비밀번호 해시는 비운다").isNull();
        assertThat(row.get("tier")).isNull();
        assertThat(row.get("deleted_at")).isNotNull();
    }

    @Test
    @DisplayName("② 리프레시 토큰이 전부 폐기된다")
    void revokes_all_sessions() {
        accountDeletionService.deleteCascade(userId);

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM refresh_token WHERE user_id = ? AND revoked_at IS NULL
                """, Integer.class, userId)).isZero();
    }

    @Test
    @DisplayName("③ 동의 기록은 지우지 않고 철회 시각만 남긴다 — 법정 보존")
    void keeps_agreements_with_withdrawn_time() {
        accountDeletionService.deleteCascade(userId);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_agreement WHERE user_id = ?", Integer.class, userId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_agreement WHERE user_id = ? AND withdrawn_at IS NOT NULL
                """, Integer.class, userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("④ 진행 중이던 인증 세션은 닫히고, 완료된 도장은 그대로 남는다")
    void expires_open_stamps_but_keeps_completed() {
        accountDeletionService.deleteCascade(userId);

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM stamp WHERE pilgrimage_id = ? AND verify_status IN ('GPS_DONE','QR_DONE','PENDING')
                """, Integer.class, pilgrimageId)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM stamp WHERE pilgrimage_id = ? AND verify_status = 'COMPLETED'
                """, Integer.class, pilgrimageId)).as("완주의 근거라 남긴다").isEqualTo(1);
    }

    @Test
    @DisplayName("⑤ 인증서는 지우지 않고 USER_WITHDRAWN 으로 회수된다 — 번호는 계속 조회된다")
    void revokes_certificates_but_keeps_the_number() {
        accountDeletionService.deleteCascade(userId);

        Map<String, Object> cert = jdbc.queryForMap(
                "SELECT status, revoke_reason FROM certificate WHERE serial_no = ?", serialNo);
        assertThat(cert.get("status")).isEqualTo("REVOKED");
        assertThat(cert.get("revoke_reason")).isEqualTo("USER_WITHDRAWN");
        // 공개 진위 확인은 계속 200 이고, 이름 자리에 '탈퇴회원' 이 나간다.
        var verify = certificateService.verify(serialNo);
        assertThat(verify.status()).isEqualTo("REVOKED");
        assertThat(verify.holderMasked()).isEqualTo("탈퇴회원");
    }

    @Test
    @DisplayName("⑥ 손대지 않은 보상만 회수하고, 이미 신청된 것은 사람이 보도록 표시만 켠다")
    void revokes_untouched_rewards_only() {
        accountDeletionService.deleteCascade(userId);

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_reward WHERE user_id = ? AND status = 'GRANTED'
                """, Integer.class, userId)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_reward WHERE user_id = ? AND status = 'REVOKED'
                """, Integer.class, userId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_reward WHERE user_id = ? AND status = 'CLAIMED'
                """, Integer.class, userId)).as("실물이 이미 움직였을 수 있다").isEqualTo(1);
        // tinyint(1) 은 드라이버가 Boolean 으로 준다 — 숫자로 캐스팅하면 ClassCastException 이다.
        assertThat(jdbc.queryForObject("""
                SELECT needs_review FROM user_reward WHERE user_id = ? AND status = 'CLAIMED'
                """, Boolean.class, userId)).as("사람이 보도록 표시").isTrue();
    }

    @Test
    @DisplayName("⑦ 배송 정보는 행째로 사라진다")
    void deletes_shipping_info() {
        accountDeletionService.deleteCascade(userId);

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM reward_claim rc JOIN user_reward ur ON ur.user_reward_id = rc.user_reward_id
                 WHERE ur.user_id = ?""", Integer.class, userId)).isZero();
    }

    @Test
    @DisplayName("⑧ 사진·문장·명상 기록은 지우고, 사진 키는 저장소 큐에 적는다")
    void deletes_records_and_queues_the_file_key() {
        int before = storageOrphanMapper.countPending("USER_WITHDRAWN");

        accountDeletionService.deleteCascade(userId);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM photo WHERE user_id = ?", Integer.class, userId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM thinkbox WHERE user_id = ?", Integer.class, userId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM meditation_log WHERE user_id = ?", Integer.class, userId)).isZero();
        // 도장 행은 남기고 내용만 비운다 — 완주·인증서가 이 행 위에 서 있다.
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM stamp WHERE pilgrimage_id = ? AND (photo_key IS NOT NULL OR user_sentence IS NOT NULL)
                """, Integer.class, pilgrimageId)).as("도장에 남은 개인 내용").isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM stamp WHERE pilgrimage_id = ?", Integer.class, pilgrimageId))
                .as("도장 행 자체는 남는다").isEqualTo(2);
        // 파일은 여기서 지우지 않는다 — 저장소가 아프면 탈퇴 전체가 롤백되기 때문이다.
        // 사진 표의 키 하나 + 도장의 키 하나 = 둘.
        assertThat(storageOrphanMapper.countPending("USER_WITHDRAWN")).isEqualTo(before + 2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM storage_orphan WHERE file_key = ? AND deleted_at IS NULL
                """, Integer.class, "PHOTO/" + userId + "/aaaa.jpg")).isEqualTo(1);
    }

    @Test
    @DisplayName("⑨ 완주 기록은 남는다 — 집계용이고 개인정보가 없다")
    void keeps_completion() {
        accountDeletionService.deleteCascade(userId);

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM pilgrimage WHERE user_id = ? AND status = 'COMPLETED'
                """, Integer.class, userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("중간에 실패하면 아홉 단계가 통째로 롤백된다 — 반쯤 지워진 계정은 아무도 못 고친다")
    void rolls_back_everything_when_a_step_fails() {
        // ⑧ 저장소 큐 적재를 일부러 터뜨린다. 연쇄 한가운데라, 그 앞의 인증서 회수·보상 회수·
        // 세션 폐기가 이미 일어난 뒤다 — 롤백이 안 되면 "반쯤 지워진 계정" 이 남는다.
        given(storageOrphanMapper.enqueue(anyString(), anyString()))
                .willThrow(new IllegalStateException("저장소 큐 적재 실패(일부러)"));

        try {
            accountDeletionService.deleteCascade(userId);
        } catch (RuntimeException expected) {
            // 실패는 예상한 것이다. 중요한 것은 아래의 "아무 일도 없었다".
        }

        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE user_id = ?", String.class, userId))
                .as("사람이 그대로다").isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM certificate WHERE serial_no = ?", String.class, serialNo))
                .as("인증서도 그대로다").isEqualTo("VALID");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_reward WHERE user_id = ? AND status = 'GRANTED'
                """, Integer.class, userId)).as("보상도 그대로다").isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM refresh_token WHERE user_id = ? AND revoked_at IS NULL
                """, Integer.class, userId)).as("세션도 그대로다").isEqualTo(1);
    }

    /* ---------------- 내부 ---------------- */

    private long secondSlot() {
        return jdbc.queryForObject("""
                SELECT course_site_id FROM course_site WHERE course_id = ? ORDER BY position LIMIT 1 OFFSET 1
                """, Long.class, COURSE_ID);
    }

    private void cleanUp() {
        String in = "(SELECT user_id FROM users WHERE email = '" + EMAIL + "' OR email LIKE 'deleted-%@invalid')";
        jdbc.update("DELETE FROM storage_orphan WHERE reason = 'USER_WITHDRAWN'");
        jdbc.update("DELETE FROM reward_claim WHERE user_reward_id IN (SELECT user_reward_id FROM user_reward WHERE user_id IN " + in + ")");
        jdbc.update("DELETE FROM user_reward WHERE user_id IN " + in);
        jdbc.update("DELETE FROM certificate WHERE user_id IN " + in);
        // 주문이 전자책을 참조한다(챕터 9). 주문을 먼저 지우지 않으면 여기서 FK 로 막힌다.
        jdbc.update("DELETE FROM print_order_address WHERE print_order_id IN "
                + "(SELECT print_order_id FROM print_order WHERE user_id IN " + in + ")");
        jdbc.update("DELETE FROM print_order WHERE user_id IN " + in);
        jdbc.update("DELETE FROM ebook WHERE user_id IN " + in);
        jdbc.update("DELETE FROM photo WHERE user_id IN " + in);
        jdbc.update("DELETE FROM thinkbox WHERE user_id IN " + in);
        jdbc.update("DELETE FROM meditation_log WHERE user_id IN " + in);
        jdbc.update("DELETE FROM stamp WHERE pilgrimage_id IN (SELECT pilgrimage_id FROM pilgrimage WHERE user_id IN " + in + ")");
        jdbc.update("DELETE FROM phrase_seen WHERE user_id IN " + in);
        jdbc.update("DELETE FROM task_seen WHERE user_id IN " + in);
        jdbc.update("DELETE FROM pilgrimage WHERE user_id IN " + in);
        jdbc.update("DELETE FROM user_agreement WHERE user_id IN " + in);
        jdbc.update("DELETE FROM refresh_token WHERE user_id IN " + in);
        jdbc.update("DELETE FROM users WHERE email = '" + EMAIL + "' OR email LIKE 'deleted-%@invalid'");
    }
}
