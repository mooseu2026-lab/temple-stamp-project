package com.templestamp.ebook.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 도장에 붙지 않은 사진 한 장(챕터 11 항목 6).
 * <p>
 * 전자일기장은 <b>도장이 없어도</b> 만들어진다. 그런데 사진은 지금까지 도장 질의에 조인돼서만
 * 책에 들어왔다 — 절에 가서 사진만 찍고 미션은 하지 않은 사람의 사진은 어디에도 실리지 않았다.
 * 이 행이 그 빈자리를 메운다.
 * <p>
 * 비공개(is_private)는 그대로 싣는다 — 개인 소장본이라 본인만 본다(챕터 9 결정).
 * 타인 얼굴이 든 사진은 여기에도 오지 않는다(기획 명세 §5.10 · 챕터 10).
 *
 * [사용 위치] EbookMaterialMapper.findLoosePhotos 의 resultType
 */
@Getter
@Setter
@NoArgsConstructor
public class EbookPhotoRow {
    private Long photoId;
    private String siteName;
    private String fileKey;
    private LocalDateTime createdAt;
}
