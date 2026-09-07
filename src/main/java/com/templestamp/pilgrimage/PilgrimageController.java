// src/main/java/com/templestamp/pilgrimage/PilgrimageController.java
package com.templestamp.pilgrimage;

import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.security.AuthenticatedUser;
import com.templestamp.pilgrimage.dto.PilgrimageResponse;
import com.templestamp.pilgrimage.dto.PilgrimageStartRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** POST /api/pilgrimages — 로그인 필요(anyRequest). 200 고정(멱등, 명세 §4). CoordinateFieldGuard 검사 대상 — 좌표 실리면 400 COMMON-4001 */
@Validated
@RestController
@RequestMapping("/api/pilgrimages")
@RequiredArgsConstructor
@Slf4j
public class PilgrimageController {

    private final PilgrimageService pilgrimageService;

    @PostMapping
    public ApiResponse<PilgrimageResponse> start(@RequestBody @Valid PilgrimageStartRequest req,
                                                 @AuthenticationPrincipal AuthenticatedUser user) {   // 보호 경로라 null 이 아니다
        log.debug("POST /api/pilgrimages userId={} courseId={}", user.userId(), req.courseId());
        return ApiResponse.ok(pilgrimageService.start(user.userId(), req.courseId()));
    }
    // GET /api/pilgrimages(목록)는 명세 §4 에 없다 — 여권이 같은 정보를 담는다. 저장소의 것은 [AUDIT] 후 제거
}
