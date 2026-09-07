package com.templestamp.user;

import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.global.type.AgreementType;
import com.templestamp.global.type.Tier;
import com.templestamp.user.dto.AgreementRequest;
import com.templestamp.user.dto.AgreementResponse;
import com.templestamp.user.dto.UserResponse;
import com.templestamp.user.dto.UserUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserMapper userMapper;
    private final UserAgreementMapper agreementMapper;

    public User getEntity(Long userId) {
        return userMapper.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_4040));
    }

    public UserResponse getMe(Long userId) {
        return UserResponse.from(getEntity(userId));
    }

    /** 문구·미션 선택에만 쓰는 조회. 12컬럼을 다 읽지 않는다. */
    public Tier getTier(Long userId) {
        return Tier.orDefault(userMapper.findTier(userId));
    }

    /**
     * 보낸 필드만 골라 UPDATE 한다. null 은 "변경하지 않음" 이므로 기존 값으로 채워 넣는다.
     * tier 는 나이로 자동 산정하지 않는다 — 생년월일을 받지 않기 때문이다.
     */
    @Transactional
    public UserResponse updateMe(Long userId, UserUpdateRequest request) {
        User user = getEntity(userId);

        userMapper.updateProfile(
                userId,
                request.nickname() != null ? request.nickname().trim() : user.getNickname(),
                request.locale() != null ? request.locale() : user.getLocale(),
                request.notificationEnabled() != null
                        ? request.notificationEnabled()
                        : user.getNotificationEnabled());

        if (request.tier() != null) {
            userMapper.updateTier(userId, request.tier().name());
        }

        return UserResponse.from(getEntity(userId));
    }

    /* ---------------- 약관 ---------------- */

    @Transactional
    public List<AgreementResponse> agree(Long userId, List<AgreementRequest> requests) {
        for (AgreementRequest request : requests) {
            agreementMapper.agree(userId,
                    request.agreementType().name(),
                    String.valueOf(request.version()));
        }
        return getAgreements(userId);
    }

    /**
     * 그 종류의 동의를 철회한다. 버전을 받지 않는다 — 사용자는 "위치기반서비스 동의를 거둔다" 고 하지
     * "v1 만 거둔다" 고 하지 않는다. 유효한 동의가 없으면 아무 일도 하지 않고 현재 목록을 그대로 준다(멱등).
     */
    @Transactional
    public List<AgreementResponse> withdraw(Long userId, AgreementType agreementType) {
        agreementMapper.withdrawByType(userId, agreementType.name());
        return getAgreements(userId);
    }

    /** 유효한(철회하지 않은) 동의만 내려보낸다. */
    public List<AgreementResponse> getAgreements(Long userId) {
        return agreementMapper.findByUserId(userId).stream()
                .filter(UserAgreement::isEffective)
                .map(a -> new AgreementResponse(
                        a.getAgreementType(),
                        parseVersion(a.getAgreementVersion()),
                        a.getAgreedAt()))
                .toList();
    }

    /**
     * 위치기반서비스 동의 확인. GPS 스탬프 진입점에서 반드시 먼저 호출한다.
     */
    public void requireLocationAgreement(Long userId) {
        if (!isAgreed(userId, AgreementType.LOCATION_SERVICE)) {
            throw new BusinessException(ErrorCode.USER_4031);
        }
    }

    /** 전자책 공개 수록 동의 여부. 미동의면 내 기록이 전자책에 실리지 않는다. */
    public boolean isEbookPublicAgreed(Long userId) {
        return isAgreed(userId, AgreementType.EBOOK_PUBLIC);
    }

    private boolean isAgreed(Long userId, AgreementType type) {
        return agreementMapper.isAgreed(userId, type.name(),
                String.valueOf(AgreementType.CURRENT_VERSION)) == 1;
    }

    /** 버전은 DB 에 VARCHAR 로 있고 응답은 숫자다. 숫자가 아니면 0으로 둔다. */
    private Integer parseVersion(String version) {
        try {
            return version == null ? null : Integer.valueOf(version.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 완주 연쇄를 시작하기 전에 그 사용자를 잠근다(챕터 7 보강 B-1).
     * <p>
     * 잠금 순서 <b>user → stamp/slot → completion → certificate/reward</b> 의 첫 칸이다.
     * 이 칸이 없으면 같은 사람의 도장 두 개가 동시에 5칸째를 채울 때, 두 트랜잭션이
     * 각자 완주 행을 세우려다 서로를 기다린다. 게다가 REPEATABLE READ 라 뒤에 온 쪽은
     * 앞사람이 만든 인증서를 <b>못 보고</b> 한 장 더 만들려다 유니크에 막힌다(정리.md §6-4).
     */
    @Transactional
    public void lockForCompletion(Long userId) {
        userMapper.lockById(userId);
    }
}
