package com.templestamp.certificate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 인증서 일련번호. 형식: {PG|HH}-YYYY-NNNNNN (예: PG-2026-000012)
 * <p>
 * 제3자가 진위를 확인하는 키라서 사람이 옮겨 적기 쉬워야 하고, 그래서 난수가 아니라 순번이다.
 * 순번은 발급 수를 세어 만들지 않고 {@link CertSerialMapper} 시퀀스에서 받아 온다 —
 * 세어서 만들면 동시 발급 때 번호가 겹치고, 회수된 번호가 재사용된다(챕터 7 §3-1·함정 2).
 */
@Component
@RequiredArgsConstructor
public class SerialGenerator {

    private static final int SEQUENCE_WIDTH = 6;

    private final CertSerialMapper certSerialMapper;

    /** 이 종류의 다음 번호를 하나 받아 온다. 같은 번호가 두 번 나오지 않는다. */
    public String next(String certType) {
        int year = LocalDate.now().getYear();
        certSerialMapper.bump(certType, year);
        int sequence = certSerialMapper.current(certType, year);
        return String.format("%s-%d-%0" + SEQUENCE_WIDTH + "d", prefix(certType), year, sequence);
    }

    private String prefix(String certType) {
        return Certificate.HOEHYANG.equals(certType) ? "HH" : "PG";
    }
}
