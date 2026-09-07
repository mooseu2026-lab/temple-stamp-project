package com.templestamp.ebook;

import com.templestamp.ebook.dto.DownloadUrlResponse;
import com.templestamp.ebook.dto.EbookResponse;
import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.response.ItemsResponse;
import com.templestamp.global.security.AuthenticatedUser;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/ebooks")
@RequiredArgsConstructor
public class EbookController {

    private final EbookService ebookService;

    /**
     * 만들기 요청(챕터 9 §2-2).
     * <p>
     * 상태코드가 둘이다 — <b>202</b> 는 "받아 두었다, 곧 만든다", <b>200</b> 은
     * "같은 재료의 책이 이미 있으니 그것을 준다" 다. 프론트는 202 면 잠시 뒤 다시 조회하고
     * 200 이면 바로 링크를 받으러 가면 된다. 둘을 한 코드로 뭉치면 그 분기를 본문에서 다시 읽어야 한다.
     * <p>
     * {@code type=INTERIM} 이면 전자일기장이다(챕터 11 결정 B). 3코스에 못 미치면 거절하지 않고
     * 개인 소장본으로 내려서 만든다 — 엔드포인트를 새로 파지 않는 이유이기도 하다.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<EbookResponse>> request(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "PERSONAL") String type) {
        EbookResponse created = ebookService.request(user.userId(), type);
        HttpStatus status = ebookService.isAlreadyReady(created) ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(ApiResponse.ok(created));
    }

    /** 내 전자책만 보인다. 전자책은 카탈로그가 아니라 사람별로 만들어지는 물건이다. */
    @GetMapping
    public ApiResponse<ItemsResponse<EbookResponse>> getMine(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(ItemsResponse.of(ebookService.getMyEbooks(user.userId())));
    }

    /**
     * 1권 조회. {@code downloadUrl} 은 <b>READY 일 때만</b> 붙고 그 밖에는 null 이다 —
     * 에러가 아니다. "만드는 중" 도 답이라, 그때마다 4xx 를 던지면 프론트가 오류 화면을 띄운다.
     * 남의 책은 403 이 아니라 404 다 — 있다는 사실 자체가 정보다.
     */
    @GetMapping("/{ebookId}")
    public ApiResponse<EbookResponse> getOne(@AuthenticationPrincipal AuthenticatedUser user,
                                             @PathVariable @Positive Long ebookId) {
        return ApiResponse.ok(ebookService.getOne(user.userId(), ebookId));
    }

    /** 링크는 몇 분 뒤 만료된다. 퍼 나른 링크로는 열리지 않는다. */
    @GetMapping("/{ebookId}/download-url")
    public ApiResponse<DownloadUrlResponse> getDownloadUrl(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable @Positive Long ebookId,
            @RequestParam(defaultValue = "PDF") String format) {
        return ApiResponse.ok(ebookService.getDownloadUrl(user.userId(), ebookId, format));
    }
}
