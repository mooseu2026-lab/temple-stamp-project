// src/main/java/com/templestamp/admin/AdminCourseController.java
package com.templestamp.admin;

import com.templestamp.admin.dto.AdminCourseSaveRequest;
import com.templestamp.admin.dto.SiteDistanceSaveRequest;
import com.templestamp.admin.dto.StatusChangeRequest;
import com.templestamp.course.dto.CourseDetailResponse;
import com.templestamp.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** /api/admin/courses + /api/admin/site-distances — ADMIN */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Validated
@Slf4j
public class AdminCourseController {

    private final AdminCourseService adminCourseService;
    private final AdminSiteDistanceService adminSiteDistanceService;

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CourseDetailResponse> create(@RequestBody @Valid AdminCourseSaveRequest req) {
        return ApiResponse.ok(adminCourseService.create(req));
    }

    @PutMapping("/courses/{courseId}")
    public ApiResponse<CourseDetailResponse> update(@PathVariable @Positive Long courseId,
                                                    @RequestBody @Valid AdminCourseSaveRequest req) {
        return ApiResponse.ok(adminCourseService.update(courseId, req));
    }

    @PatchMapping("/courses/{courseId}/status")
    public ApiResponse<Void> status(@PathVariable @Positive Long courseId,
                                    @RequestBody @Valid StatusChangeRequest req) {
        adminCourseService.changeStatus(courseId, req);
        return ApiResponse.ok();
    }

    /** 이동시간 일괄 등록 — 코스당 20행(5×4, 방향 있음). UPSERT 라 여러 번 보내도 안전 */
    @PutMapping("/site-distances")
    public ApiResponse<Void> siteDistances(@RequestBody @Valid SiteDistanceSaveRequest req) {
        adminSiteDistanceService.upsertAll(req);
        return ApiResponse.ok();
    }
}
