package com.templestamp.course;

import com.templestamp.course.dto.RegionResponse;
import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.response.ItemsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 권역 9개 + 각 권역의 ACTIVE 코스 수. 인가 등급: 공개.
 * <p>
 * 권역 전용 Service 를 두지 않는다. 클래스를 하나 더 만드는 것보다
 * "권역·코스·자리는 한 묶음" 이라는 도메인 경계를 지키는 편이 낫다.
 * <p>
 * 9개 고정이라 페이징이 필요 없지만 그래도 ItemsResponse 로 감싼다 —
 * 배열을 응답 맨 바깥에 두면 나중에 필드 하나를 더할 때 프론트가 깨진다.
 */
@Validated
@RestController
@RequestMapping("/api/regions")
@RequiredArgsConstructor
@Slf4j
public class RegionController {

    private final CourseService courseService;

    /** 코스가 아직 없는 권역도 courseCount=0 으로 함께 내려간다. */
    @GetMapping
    public ApiResponse<ItemsResponse<RegionResponse>> list() {
        log.debug("GET /api/regions");
        return ApiResponse.ok(ItemsResponse.of(courseService.getRegions()));
    }
}
