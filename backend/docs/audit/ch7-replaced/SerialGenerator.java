package com.templestamp.certificate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 인증서 일련번호. 형식: {PG|HH}-YYYY-NNNNNN (예: PG-2026-000012)
 * <p>
 * 제3자가 진위를 확인하는 키라서 사람이 옮겨 적기 쉬워야 하고, 그래서 난수가 아니라 순번이다.
 * 순번은 "그 해 그 종류의 발급 수 + 1" 로 만든다. 동시에 두 건이 발급되면 같은 번호가 나올 수
 * 있는데, serial_no 에 UNIQUE 가 걸려 있어 두 번째는 실패하고 호출부가 번호를 다시 뽑는다.
 * 순번을 별도 시퀀스 테이블로 관리하지 않는 이유는, 발급이 초당 수백 건 일어나는 종류의
 * 작업이 아니어서 재시도 한두 번이 더 단순하기 때문이다.
 */
@Component
@RequiredArgsConstructor
public class SerialGenerator {

    private static final int SEQUENCE_WIDTH = 6;

    private final CertificateMapper certificateMapper;

    /** 지정한 순번으로 만든다. 재시도 때 번호를 하나씩 밀어 올리는 데 쓴다. */
    public String generate(String certType, int sequence) {
        return String.format("%s-%d-%0" + SEQUENCE_WIDTH + "d",
                prefix(certType), LocalDate.now().getYear(), sequence);
    }

    public int nextSequence(String certType) {
        return certificateMapper.countByTypeAndYear(certType, LocalDate.now().getYear()) + 1;
    }

    private String prefix(String certType) {
        return Certificate.HOEHYANG.equals(certType) ? "HH" : "PG";
    }
}
