package com.templestamp.ebook;

import com.templestamp.ebook.dto.EbookCertRow;
import com.templestamp.ebook.dto.EbookPhotoRow;
import com.templestamp.ebook.dto.EbookStampRow;
import com.templestamp.ebook.dto.EbookThinkboxRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 전자책 재료를 모으는 질의만 모아 둔 매퍼(챕터 9 §2-3).
 * <p>
 * 도메인 매퍼(StampMapper·ThinkboxMapper…)에 흩어 두지 않은 이유는,
 * 이 질의들이 <b>"책 한 권을 만들기 위한 모양"</b>으로 조인돼 있어서다.
 * 도장 목록 화면과 도장 페이지 재료는 같은 표를 읽지만 필요한 칸이 다르다.
 */
@Mapper
public interface EbookMaterialMapper {

    /** 완료된 도장만, 발행일 순. 그날의 확장문구는 stamp 가 붙든 원고를 따라간다. */
    List<EbookStampRow> findStamps(@Param("userId") Long userId);

    /**
     * 도장에 붙지 않은 사진. 전자일기장은 도장이 없어도 만들어지므로(챕터 11 결정 B)
     * 사진이 도장 질의에만 붙어 있으면 "절에 가서 사진만 찍은" 기록이 통째로 빠진다.
     * 완료된 도장이 있는 사찰의 사진은 여기서 빼고 도장 쪽에 싣는다 — 두 번 나오면 안 된다.
     */
    List<EbookPhotoRow> findLoosePhotos(@Param("userId") Long userId);

    /** 비공개 포함 전부, 작성일 순. 개인 소장본이라 감출 이유가 없다. */
    List<EbookThinkboxRow> findThinkboxes(@Param("userId") Long userId);

    /** VALID 만. 회수된 인증서는 이 책에 실리지 않는다. */
    List<EbookCertRow> findValidCerts(@Param("userId") Long userId);

    /**
     * 완주한 코스 수. 전자일기장의 마일스톤이 여기서 나온다.
     * PilgrimageMapper 를 끌어오지 않는 이유는 전자책 모듈이 순례 모듈에 매이지 않게 하기 위해서다 —
     * 같은 표를 읽지만 여기서 필요한 것은 숫자 하나뿐이다.
     */
    int countCompletedCourses(@Param("userId") Long userId);

    int countMeditationLogs(@Param("userId") Long userId);

    long sumMeditationSeconds(@Param("userId") Long userId);

    String findNickname(@Param("userId") Long userId);
}
