// src/main/java/com/templestamp/photo/PhotoController.java
package com.templestamp.photo;

import com.templestamp.global.response.ApiResponse;
import com.templestamp.global.response.ItemsResponse;
import com.templestamp.global.security.AuthenticatedUser;
import com.templestamp.photo.dto.PhotoResponse;
import com.templestamp.photo.dto.PhotoSaveRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개인 소장 — 사찰당 사진 1장과 문장 1개. <b>도장 여부를 묻지 않는다.</b>
 * <p>
 * PUT 인 이유: 사찰당 하나뿐이라 "만들기/고치기" 가 나뉘지 않는다. 없으면 만들고 있으면 갈아 끼우므로
 * 언제나 200 이고, 프론트가 처음인지 두 번째인지 알 필요가 없다.
 * <p>
 * {@code @Validated} 가 있어야 경로 변수의 {@code @Positive} 가 검사된다(정리.md §6-6).
 */
@RestController
@RequestMapping("/api/photos")
@RequiredArgsConstructor
@Validated
public class PhotoController {

    private final PhotoService photoService;

    /** 있으면 교체, 없으면 생성 — 언제나 200. */
    @PutMapping("/{siteId}")
    public ApiResponse<PhotoResponse> save(@PathVariable @Positive Long siteId,
                                           @RequestBody @Valid PhotoSaveRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(photoService.save(user.userId(), siteId, request));
    }

    @GetMapping("/{siteId}")
    public ApiResponse<PhotoResponse> get(@PathVariable @Positive Long siteId,
                                          @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(photoService.get(user.userId(), siteId));
    }

    /** 내 사진 전부 — 전자책 미리보기가 쓴다. */
    @GetMapping
    public ApiResponse<ItemsResponse<PhotoResponse>> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(ItemsResponse.of(photoService.listMine(user.userId())));
    }
}
