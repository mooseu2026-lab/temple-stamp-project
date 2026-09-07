package com.templestamp.certificate;

import com.templestamp.certificate.dto.CertificateResponse;
import com.templestamp.certificate.dto.CertificateRow;
import com.templestamp.certificate.dto.CertificateVerifyResponse;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CertificateService {

    private static final int SERIAL_RETRY = 5;
    private static final String VERIFY_PATH = "/api/certificates/verify/";

    private final CertificateMapper certificateMapper;
    private final SerialGenerator serialGenerator;

    /**
     * 코스 완주 인증서. 이미 있으면 그 일련번호를 그대로 돌려준다(재발급하지 않는다).
     * 완주 처리가 재시도돼도 인증서가 두 장 생기지 않게 하기 위해서다.
     */
    @Transactional
    public Certificate issueForPilgrimage(Long userId, Long pilgrimageId) {
        return certificateMapper.findByPilgrimageId(pilgrimageId)
                .orElseGet(() -> create(userId, pilgrimageId, Certificate.PILGRIMAGE));
    }

    /** 회향 인증서. 사용자당 한 장이며 순례에 매이지 않는다. */
    @Transactional
    public Certificate issueHoehyang(Long userId) {
        return certificateMapper.findByUserAndType(userId, Certificate.HOEHYANG)
                .orElseGet(() -> create(userId, null, Certificate.HOEHYANG));
    }

    /**
     * 순번은 "그 해 그 종류의 발급 수 + 1" 로 만든다. 동시 발급으로 번호가 겹치면
     * serial_no UNIQUE 가 두 번째를 막으므로, 번호를 하나 밀어 올려 다시 시도한다.
     */
    private Certificate create(Long userId, Long pilgrimageId, String certType) {
        int sequence = serialGenerator.nextSequence(certType);

        for (int attempt = 0; attempt < SERIAL_RETRY; attempt++) {
            String serial = serialGenerator.generate(certType, sequence + attempt);
            Certificate certificate = Certificate.builder()
                    .userId(userId)
                    .pilgrimageId(pilgrimageId)
                    .certType(certType)
                    .serialNo(serial)
                    .build();
            try {
                certificateMapper.save(certificate);
                return certificate;
            } catch (DuplicateKeyException e) {
                log.debug("인증서 일련번호 충돌, 다음 번호로 재시도: {}", serial);
            }
        }
        throw new IllegalStateException("인증서 일련번호 생성에 %d회 실패했습니다.".formatted(SERIAL_RETRY));
    }

    public List<CertificateResponse> getMyCertificates(Long userId) {
        return certificateMapper.findRowsByUserId(userId).stream()
                .map(row -> CertificateResponse.of(row, verifyUrl(row.getSerialNo())))
                .toList();
    }

    /**
     * 공개 검증. 없는 번호는 이 DTO 를 만들지 않고 404 로 끊는다 —
     * 존재하지 않는 번호에 200 을 주면 번호를 넣어 보며 유효 번호를 찾는 시도가 쉬워진다.
     */
    public CertificateVerifyResponse verify(String serialNo) {
        CertificateRow row = certificateMapper.findRowBySerial(serialNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.CERT_4041));
        return CertificateVerifyResponse.from(row);
    }

    /**
     * 완주가 깨졌을 때(심사 반려 등) 순례형 인증서를 회수한다.
     * 그 번호가 제3자에게 이미 제시됐을 수 있어 "유효하지 않음" 이 즉시 드러나야 한다.
     */
    @Transactional
    public void revokeForPilgrimage(Long pilgrimageId) {
        if (certificateMapper.deleteByPilgrimageId(pilgrimageId) > 0) {
            log.warn("완주 취소로 인증서 회수. pilgrimageId={}", pilgrimageId);
        }
    }

    public int countByUser(Long userId) {
        return certificateMapper.countByUserId(userId);
    }

    private String verifyUrl(String serialNo) {
        return VERIFY_PATH + serialNo;
    }
}
