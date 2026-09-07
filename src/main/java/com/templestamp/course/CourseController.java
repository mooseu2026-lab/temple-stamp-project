package com.templestamp.course;

import com.templestamp.course.dto.CourseDetailResponse;
import com.templestamp.course.dto.CourseResponse;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.response.ItemsResponse;
import com.templestamp.global.security.AuthenticatedUser;
import com.templestamp.global.type.Tier;
import com.templestamp.global.web.LocaleUtil;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 코스 목록·상세·진행률.
 * 인가 등급은 "선택" — 토큰이 있으면 progress 가 붙고, 없으면 progress 는 null 이다.
 * SecurityConfig: GET /api/courses/** permitAll.
 * <p>
 * @Validated 는 클래스에 붙어야 @PathVariable·@RequestParam 의 제약이 검사된다.
 * 빠뜨리면 검증이 예외 없이 조용히 통과한다.
 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@Validated
@Slf4j
public class CourseController {

    private final CourseService courseService;

    /**
     * GET /api/courses?regionId=3 — regionId 는 선택이고, 없으면 전체 코스다.
     * 0 이하면 400 COMMON-4000 이다(null 일 때는 @Positive 가 검사하지 않는다).
     */
    @GetMapping
    public ApiResponse<ItemsResponse<CourseResponse>> list(
            @RequestParam(required = false) @Positive Long regionId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        Long userId = user == null ? null : user.userId();
        log.debug("GET /api/courses regionId={} userId={}", regionId, userId);
        return ApiResponse.ok(ItemsResponse.of(courseService.getCourses(regionId, userId)));
    }

    /**
     * 사찰 5곳의 좌표를 한 번에 담아 준다. 산중에서 다시 네트워크를 타지 않게.
     * 이 뒤로 거리 계산은 단말이 하고, 좌표는 서버로 되돌아오지 않는다.
     */
    @GetMapping("/{courseId}")
    public ApiResponse<CourseDetailResponse> detail(
            @PathVariable @Positive Long courseId,
            @RequestParam(required = false) Tier target,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
            @AuthenticationPrincipal AuthenticatedUser user) {
        String locale = LocaleUtil.resolve(acceptLanguage);
        Long userId = user == null ? null : user.userId();
        log.debug("GET /api/courses/{} target={} locale={} userId={}", courseId, target, locale, userId);
        return ApiResponse.ok(courseService.getCourseDetail(courseId, userId, locale, target));
    }

    // GET /{courseId}/progress 는 명세 §4 에 없어 제거했다(ch4 부수 결정).
    // 진행률은 목록·상세 응답의 progress 로 이미 실려 나가고, 계산은 PilgrimageService.progressOf 한 곳에서만 한다.
    // 이 메서드가 저장소에서 컨트롤러가 직접 401 을 던지는 유일한 자리였는데, 그것도 함께 사라졌다.
}
