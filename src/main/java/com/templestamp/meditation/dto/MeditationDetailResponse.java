package com.templestamp.meditation.dto;

/**
 * 명상 한 편의 스크립트와 오디오 재생 URL 을 내려주는 DTO Record입니다.
 * audioUrl 은 저장소 키를 임시 URL 로 바꾼 값이며 저장소가 없으면 null 입니다.
 * [사용 위치] MeditationService 조립(저장소 presign 결합) → Controller 반환
 */
public record MeditationDetailResponse(
        Long meditationId,
        String title,
        String category,
        String script,
        String audioUrl,            // 임시 URL. 저장소 미구성 시 null
        Integer durationSeconds,

        /** 실제로 나간 언어. 요청한 locale 이 없으면 ko 로 물러난 결과가 여기 담긴다(명세 F-20). */
        String lang
) {
}
