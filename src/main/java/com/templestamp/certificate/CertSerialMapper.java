package com.templestamp.certificate;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 인증서 일련번호 시퀀스. 발급 수를 세지 않고 따로 굴린다(챕터 7 §3-1).
 * <p>
 * {@code COUNT(*) + 1} 은 두 가지로 틀린다 — 동시에 두 건이 발급되면 같은 번호가 나오고,
 * 회수로 행이 빠지면(옛 구현은 회수를 DELETE 로 했다) 번호가 <b>재사용</b>된다.
 * 재사용된 번호는 공개 진위 확인을 거짓말로 만든다.
 */
@Mapper
public interface CertSerialMapper {

    /**
     * 그 해 그 종류의 번호를 하나 올린다. 없으면 1부터 시작한다.
     * 같은 (종류, 해) 행에 잠금이 걸리므로 동시에 들어와도 한 줄로 선다.
     */
    int bump(@Param("certType") String certType, @Param("year") int year);

    /** 방금 올린 값을 읽는다. 같은 트랜잭션이라 자기 쓰기가 보인다. */
    int current(@Param("certType") String certType, @Param("year") int year);
}
