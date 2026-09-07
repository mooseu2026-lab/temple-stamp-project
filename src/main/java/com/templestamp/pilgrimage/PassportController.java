// src/main/java/com/templestamp/pilgrimage/PassportController.java
package com.templestamp.pilgrimage;

import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.security.AuthenticatedUser;
import com.templestamp.global.web.LocaleUtil;
import com.templestamp.pilgrimage.dto.PassportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/passport — 명세 §4·§10(S-02 홈, S-03 여권). 저장소의 /api/pilgrimages/passport 는 이 경로로 이동. 캐시 60초는 프론트 몫 */
@Validated
@RestController
@RequestMapping("/api/passport")
@RequiredArgsConstructor
public class PassportController {

    private final PassportService passportService;

    /**
     * 언어 우선순위는 챕터 2 와 같다 — Accept-Language → users.locale → ko.
     * 컨트롤러는 헤더에서 힌트만 만들고(못 고르면 null), users.locale 은 Service 가 본다.
     * 여기서 ko 로 확정해 버리면 users.locale 이 영영 쓰이지 않는다.
     * 여권에는 쿼리 locale 이 없으므로 hint 의 첫 인자는 null 이다.
     */
    @GetMapping
    public ApiResponse<PassportResponse> passport(
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(passportService.build(user.userId(), LocaleUtil.hint(null, acceptLanguage)));
    }
}
