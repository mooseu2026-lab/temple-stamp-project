package com.templestamp.user;

import com.templestamp.auth.RefreshTokenMapper;
import com.templestamp.certificate.Certificate;
import com.templestamp.certificate.CertificateMapper;
import com.templestamp.meditation.MeditationLogMapper;
import com.templestamp.photo.Photo;
import com.templestamp.photo.PhotoMapper;
import com.templestamp.reward.RewardClaimMapper;
import com.templestamp.reward.UserRewardMapper;
import com.templestamp.stamp.StampMapper;
import com.templestamp.thinkbox.ThinkboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 연쇄(챕터 1 보강 §3 + 챕터 9 §2-5). <b>열한 단계가 한 트랜잭션</b>이다 —
 * 중간에서 멈추면 "인증서는 회수됐는데 계정은 살아 있는" 상태가 남고, 그것은 아무도 못 고친다.
 * <p>
 * <b>user 행은 지우지 않는다.</b> 물리 DELETE 를 하면 외래키 연쇄로 인증서·완주 기록까지 사라지는데,
 * 이미 발급된 인증서 번호는 제3자가 확인하는 값이라 없어지면 안 된다. 그래서 지우는 대신 익명화한다 —
 * 남는 것은 "누군가 이 코스를 끝냈다" 는 사실뿐이고, 그 사람이 누구인지는 남지 않는다.
 * <p>
 * 저장소 파일은 여기서 지우지 않고 {@code storage_orphan} 큐에 적는다. 저장소 호출이 실패하면
 * 탈퇴 전체가 롤백될 텐데, "지워 달라" 는 요청이 저장소가 잠깐 아프다는 이유로 아무 일도
 * 일으키지 못하는 것이 가장 나쁘다(챕터 9 청소기가 소비한다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    /** 인증서 회수 사유·저장소 큐 사유. 두 곳에서 같은 낱말을 쓴다. */
    private static final String USER_WITHDRAWN = "USER_WITHDRAWN";

    private final UserMapper userMapper;
    private final UserAgreementMapper agreementMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final StampMapper stampMapper;
    private final CertificateMapper certificateMapper;
    private final UserRewardMapper userRewardMapper;
    private final RewardClaimMapper rewardClaimMapper;
    private final PhotoMapper photoMapper;
    private final ThinkboxMapper thinkboxMapper;
    private final MeditationLogMapper meditationLogMapper;
    private final StorageOrphanMapper storageOrphanMapper;
    private final com.templestamp.ebook.EbookService ebookService;             // 챕터 9 §2-5
    private final com.templestamp.ebook.PrintOrderService printOrderService;   // 챕터 9 §2-5

    @Transactional
    public void deleteCascade(Long userId) {
        // ② 세션부터 끊는다. 뒤 단계가 도는 동안 그 토큰으로 뭔가 더 만들 수 있으면 안 된다.
        int tokens = refreshTokenMapper.revokeAllByUser(userId);

        // ④ 진행 중이던 인증 세션을 닫는다. 완료된 도장은 완주의 근거라 손대지 않는다.
        int sessions = stampMapper.expireOpenByUser(userId);

        // ⑤ 인증서는 지우지 않고 회수한다 — 번호는 이미 밖에 나갔다.
        //    공개 진위 확인은 계속 200 + REVOKED 이고, 이름 자리에는 '탈퇴회원' 이 나간다.
        int certificates = certificateMapper.revokeAllByUser(userId, Certificate.USER_WITHDRAWN);

        // ⑥ 보상은 완주 취소와 같은 규칙이다. 아직 아무도 움직이지 않은 것만 회수하고,
        //    이미 신청·지급된 실물은 상태를 두고 사람이 보도록 표시만 켠다(택배가 나갔을 수 있다).
        int rewardsRevoked = userRewardMapper.revokeGrantedByUser(userId);
        int rewardsFlagged = userRewardMapper.flagNeedsReviewByUser(userId);

        // ⑦ 배송 정보(이름·연락처·주소)는 남길 이유가 없다.
        int claims = rewardClaimMapper.deleteByUser(userId);

        // ⑧ 사진·문장·생각상자·명상은 행째로 지운다. 저장소 파일 키는 큐에 적어 둔다.
        int photoKeys = 0;
        for (Photo photo : photoMapper.findByUserId(userId)) {
            if (photo.getFileKey() != null && !photo.getFileKey().isBlank()) {
                storageOrphanMapper.enqueue(photo.getFileKey(), USER_WITHDRAWN);
                photoKeys++;
            }
        }
        // 도장에도 그 사람이 쓴 것이 남아 있다 — 사진과 다짐 문장. 행은 완주의 근거라 지울 수 없으니
        // 키를 큐에 적고 두 칸만 비운다(챕터 8 STEP 0).
        for (String stampPhotoKey : stampMapper.findPhotoKeysByUser(userId)) {
            storageOrphanMapper.enqueue(stampPhotoKey, USER_WITHDRAWN);
            photoKeys++;
        }
        int scrubbed = stampMapper.scrubPersonalByUser(userId);

        // 챕터 9 §2-5 — <b>인쇄 주문이 먼저다.</b> 주문이 전자책을 참조하므로 순서를 바꾸면
        // FK 가 막는다(실측에서 그렇게 500 이 났다).
        // 아직 확인 전이면 취소하고 그 행은 지운다. 이미 종이가 움직이는 것은 취소하지 않고
        // 사람이 볼 표시만 켠다 — 시스템이 취소해도 인쇄소의 일이 되돌아오지는 않는다.
        int orders = printOrderService.handleWithdrawal(userId);
        // 전자책은 행째 지우고 파일 키는 큐로. 다만 <b>진행 중인 주문이 붙든 책은 남긴다</b> —
        // 주문서가 가리키는 것이 사라지면 인쇄소에 무엇을 보낼지 알 수 없다.
        int ebooks = ebookService.deleteAllForWithdrawal(userId);

        int photos = photoMapper.deleteByUser(userId);
        int thinkboxes = thinkboxMapper.deleteByUser(userId);
        int meditationLogs = meditationLogMapper.deleteByUser(userId);

        // ③ 동의 기록은 지우지 않는다. "언제 동의했고 언제 철회했는가" 가 증거다.
        int agreements = agreementMapper.withdrawAll(userId);

        // ① 마지막에 사람을 지운다. 앞 단계들이 user_id 로 찾아 들어가기 때문이다.
        //    ⑨ 완주 기록(pilgrimage)은 그대로 둔다 — 집계용이고 개인정보가 없다.
        if (userMapper.anonymize(userId) == 0) {
            // 이미 탈퇴한 계정이다. 두 번 눌러도 같은 결과여야 하므로 예외로 만들지 않는다.
            log.info("이미 탈퇴한 계정이다. userId={}", userId);
            return;
        }

        log.info("탈퇴 완료. userId={}, 토큰={}, 세션={}, 인증서={}, 보상 회수={}·표시={}, 배송정보={},"
                        + " 전자책={}, 인쇄주문={}, 사진={}(키 {}), 도장 내용 비움={}, 생각상자={}, 명상={}, 동의철회={}",
                userId, tokens, sessions, certificates, rewardsRevoked, rewardsFlagged, claims,
                ebooks, orders, photos, photoKeys, scrubbed, thinkboxes, meditationLogs, agreements);
    }
}
