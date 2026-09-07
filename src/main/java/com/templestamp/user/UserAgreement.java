package com.templestamp.user;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * user_agreement 테이블 도메인 클래스 — 약관 동의 이력.
 * 버전별로 한 행이며, 철회해도 행을 지우지 않고 withdrawnAt 만 채운다.
 * "언제 어느 버전에 동의했는가" 가 분쟁의 근거이기 때문이다.
 */
@Getter
@Setter
@NoArgsConstructor
public class UserAgreement {

    private Long userAgreementId;
    private Long userId;
    private String agreementType;
    private String agreementVersion;
    private LocalDateTime agreedAt;
    private LocalDateTime withdrawnAt;

    public boolean isEffective() { return withdrawnAt == null; }
}
