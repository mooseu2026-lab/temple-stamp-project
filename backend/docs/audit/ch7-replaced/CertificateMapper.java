package com.templestamp.certificate;

import com.templestamp.certificate.dto.CertificateRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface CertificateMapper {

    int save(Certificate certificate);

    Optional<Certificate> findByPilgrimageId(@Param("pilgrimageId") Long pilgrimageId);

    Optional<Certificate> findByUserAndType(@Param("userId") Long userId,
                                            @Param("certType") String certType);

    Optional<CertificateRow> findRowBySerial(@Param("serialNo") String serialNo);

    List<CertificateRow> findRowsByUserId(@Param("userId") Long userId);

    /** 그 해 그 종류의 발급 수. 다음 일련번호를 정하는 데 쓴다. */
    int countByTypeAndYear(@Param("certType") String certType, @Param("year") int year);

    int countByUserId(@Param("userId") Long userId);

    int updateFileKey(@Param("certificateId") Long certificateId, @Param("fileKey") String fileKey);

    /** 완주가 깨졌을 때 발급분을 지운다. 순례형만 해당한다. */
    int deleteByPilgrimageId(@Param("pilgrimageId") Long pilgrimageId);
}
