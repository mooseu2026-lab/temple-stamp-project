package com.templestamp.pilgrimage.dto;

import java.time.LocalDateTime;

/**
 * 시작되었거나 이미 진행 중인 순례 정보를 내려주는 DTO Record입니다.
 * created 필드로 신규 생성인지 기존 재사용인지 구분하며 두 경우 모두 200 으로 응답합니다.
 * (코스 탭을 연타해도 pilgrimage 는 한 건이라 201/409 로 나눌 실익이 없다)
 * [사용 위치] PilgrimageService 조립 → Controller 반환
 */
public record PilgrimageResponse(
        Long pilgrimageId,
        Long courseId,
        String status,
        LocalDateTime startedAt,
        boolean created             // true=이번 요청으로 생성 / false=기존 진행 건 재사용
) {
}
