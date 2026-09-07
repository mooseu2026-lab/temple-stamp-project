package com.templestamp.admin;

import com.templestamp.admin.dto.MissionSaveRequest;
import com.templestamp.admin.dto.PhraseSaveRequest;
import com.templestamp.global.response.ApiResponse;
import com.templestamp.verse.PhraseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 확장문구·미션 원고 관리.
 * (구절 5 × 계층 7 × 버전 5 = 175편) 구조라, 버전 번호를 비우면 다음 빈 번호를 서버가 채운다.
 * 같은 (구절·계층·버전) 이면 덮어쓰므로 원고 파일을 다시 밀어 넣어도 중복되지 않는다.
 */
@Validated
@RestController
@RequestMapping("/api/admin/contents")
@RequiredArgsConstructor
public class AdminContentController {

    private final PhraseService phraseService;

    @PostMapping("/phrases")
    public ApiResponse<Long> savePhrase(@Valid @RequestBody PhraseSaveRequest request) {
        return ApiResponse.ok(phraseService.savePhrase(request));
    }

    @PostMapping("/missions")
    public ApiResponse<Long> saveMission(@Valid @RequestBody MissionSaveRequest request) {
        return ApiResponse.ok(phraseService.saveMission(request));
    }
}
